// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.pico.DefaultPicoContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Test;
import org.picocontainer.PicoContainer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.*;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class ExtensionPointImplTest {
  private Disposable disposable = Disposer.newDisposable();

  @After
  public void tearDown() {
    if (disposable != null) {
      Disposer.dispose(disposable);
    }
  }

  @Test
  public void testCreate() {
    ExtensionPoint<Integer> extensionPoint = buildExtensionPoint(Integer.class);
    assertThat(extensionPoint.getName()).isEqualTo(ExtensionsImplTest.EXTENSION_POINT_NAME_1);
    assertThat(extensionPoint.getClassName()).isEqualTo(Integer.class.getName());
  }

  @Test
  public void testUnregisterObject() {
    ExtensionPoint<Integer> extensionPoint = buildExtensionPoint(Integer.class);
    extensionPoint.registerExtension(new Integer(123), disposable);
    assertThat(extensionPoint.getExtensionList()).hasSize(1);

    Disposer.dispose(disposable);
    disposable = null;

    assertThat(extensionPoint.getExtensionList()).isEmpty();
  }

  @Test
  public void testRegisterObject() {
    ExtensionPoint<Integer> extensionPoint = buildExtensionPoint(Integer.class);
    extensionPoint.registerExtension(new Integer(123), disposable);
    Object[] extensions = extensionPoint.getExtensions();
    assertThat(extensions).describedAs("One extension").hasSize(1);
    assertThat(extensions).isInstanceOf(Integer[].class);
    assertThat(extensions[0]).isEqualTo(new Integer(123));
  }

  @Test
  public void testRegistrationOrder() {
    ExtensionPoint<Integer> extensionPoint = buildExtensionPoint(Integer.class);
    extensionPoint.registerExtension(new Integer(123), disposable);
    extensionPoint.registerExtension(new Integer(321), LoadingOrder.FIRST, disposable);
    Object[] extensions = extensionPoint.getExtensions();
    assertThat(extensions).hasSize(2);
    assertThat(extensions[0]).isEqualTo(new Integer(321));
  }

  @Test
  public void testListener() {
    ExtensionPoint<Integer> extensionPoint = buildExtensionPoint(Integer.class);
    final boolean[] added = new boolean[1];
    final boolean[] removed = new boolean[1];
    extensionPoint.addExtensionPointListener(new ExtensionPointListener<Integer>() {
      @Override
      public void extensionAdded(@NotNull Integer extension, @NotNull final PluginDescriptor pluginDescriptor) {
        added[0] = true;
      }

      @Override
      public void extensionRemoved(@NotNull Integer extension, @NotNull final PluginDescriptor pluginDescriptor) {
        removed[0] = true;
      }
    }, true, null);
    assertThat(added[0]).isFalse();
    assertThat(removed[0]).isFalse();
    extensionPoint.registerExtension(new Integer(123), disposable);
    assertThat(added[0]).isTrue();
    assertThat(removed[0]).isFalse();
    added[0] = false;

    Disposer.dispose(disposable);
    disposable = null;

    assertThat(added[0]).isFalse();
    assertThat(removed[0]).isTrue();
  }

  @Test
  public void testLateListener() {
    ExtensionPoint<Integer> extensionPoint = buildExtensionPoint(Integer.class);
    final boolean[] added = new boolean[1];
    extensionPoint.registerExtension(new Integer(123), disposable);
    assertThat(added[0]).isFalse();
    extensionPoint.addExtensionPointListener(new ExtensionPointListener<Integer>() {
      @Override
      public void extensionAdded(@NotNull Integer extension, @NotNull PluginDescriptor pluginDescriptor) {
        added[0] = true;
      }
    }, true, null);
    assertThat(added[0]).isTrue();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testIncompatibleExtension() {
    @SuppressWarnings("rawtypes")
    ExtensionPoint extensionPoint = buildExtensionPoint(Integer.class);

    try {
      extensionPoint.registerExtension(new Double(0), disposable);
      fail("must throw");
    }
    catch (RuntimeException ignored) {
    }

    assertThat(extensionPoint.getExtensionList()).isEmpty();

    extensionPoint.registerExtension(new Integer(0), disposable);
    assertThat(extensionPoint.getExtensionList()).hasSize(1);
  }

  @Test
  public void testIncompatibleAdapter() {
    ExtensionPointImpl<Integer> extensionPoint = buildExtensionPoint(Integer.class);

    extensionPoint.addExtensionAdapter(stringAdapter());

    try {
      assertThat(extensionPoint.getExtensionList()).isEmpty();
      fail("must throw");
    }
    catch (AssertionError ignored) {
    }
  }

  @Test
  public void testCompatibleAdapter() {
    ExtensionPointImpl<Integer> extensionPoint = buildExtensionPoint(Integer.class);
    extensionPoint.registerExtension(new Integer(0), disposable);
    assertThat(extensionPoint.getExtensions()).hasSize(1);
  }

  @Test
  public void cancelledRegistration() {
    doTestInterruptedAdapterProcessing(() -> {
      throw new ProcessCanceledException();
    }, (extensionPoint, adapter) -> {
      assertThatThrownBy(() -> extensionPoint.getExtensionList()).isInstanceOf(ProcessCanceledException.class);

      adapter.setFire(null);
      List<String> extensions = extensionPoint.getExtensionList();
      assertThat(extensionPoint.getExtensionList()).hasSize(3);

      assertThat(extensions.get(0)).isEqualTo("second");
      assertThat(extensions.get(1)).isIn("", "first");
      assertThat(extensions.get(2)).isIn("", "first");
      assertThat(extensions.get(2)).isNotEqualTo(extensions.get(1));
    });
  }

  @Test
  public void notApplicableRegistration() {
    doTestInterruptedAdapterProcessing(() -> {
      throw ExtensionNotApplicableException.INSTANCE;
    }, (extensionPoint, adapter) -> {
      assertThat(extensionPoint.getExtensionList()).hasSize(2);
      adapter.setFire(null);
      // even if now extension is applicable, adapters is not reprocessed and result is the same
      assertThat(extensionPoint.getExtensionList()).hasSize(2);
    });
  }

  @Test
  public void iteratorAndNotApplicableRegistration() {
    ExtensionPointImpl<String> extensionPoint = buildExtensionPoint(String.class);

    extensionPoint.registerExtension("first", disposable);

    MyShootingComponentAdapter adapter = stringAdapter();
    extensionPoint.addExtensionAdapter(adapter);
    adapter.setFire(() -> {
      throw ExtensionNotApplicableException.INSTANCE;
    });

    extensionPoint.registerExtension("third", disposable);

    Iterator<String> iterator = extensionPoint.iterator();
    assertThat(iterator.hasNext()).isTrue();
    assertThat(iterator.next()).isEqualTo("first");
    assertThat(iterator.hasNext()).isTrue();
    assertThat(iterator.next()).isEqualTo("third");
    assertThat(iterator.hasNext()).isFalse();
  }

  private void doTestInterruptedAdapterProcessing(@NotNull Runnable firework, @NotNull BiConsumer<ExtensionPointImpl<String>, MyShootingComponentAdapter> test) {
    ExtensionPointImpl<String> extensionPoint = buildExtensionPoint(String.class);
    MyShootingComponentAdapter adapter = stringAdapter();

    extensionPoint.registerExtension("first", disposable);
    assertThat(extensionPoint.getExtensionList()).hasSize(1);

    // registers a wrapping adapter
    extensionPoint.registerExtension("second", LoadingOrder.FIRST, disposable);
    extensionPoint.addExtensionAdapter(adapter);
    adapter.setFire(firework);

    test.accept(extensionPoint, adapter);
  }

  @Test
  public void testListenerNotifications() {
    ExtensionPoint<String> extensionPoint = buildExtensionPoint(String.class);

    final List<String> extensions = new ArrayList<>();
    extensionPoint.addExtensionPointListener(new ExtensionPointListener<String>() {
      @Override
      public void extensionAdded(@NotNull String extension, @NotNull PluginDescriptor pluginDescriptor) {
        extensions.add(extension);
      }
    }, true, null);

    extensionPoint.registerExtension("first", disposable);
    assertThat(extensions).contains("first");

    extensionPoint.registerExtension("second", LoadingOrder.FIRST, disposable);

    MyShootingComponentAdapter adapter = stringAdapter();
    ((ExtensionPointImpl<?>)extensionPoint).addExtensionAdapter(adapter);
    adapter.setFire(() -> {
      throw new ProcessCanceledException();
    });
    assertThatThrownBy(() -> extensionPoint.getExtensionList()).isInstanceOf(ProcessCanceledException.class);
    assertThat(extensions).contains("first", "second");

    adapter.setFire(null);
    extensionPoint.getExtensionList();
    assertThat(extensions).contains("first", "second", "");
  }

  @Test
  public void testClearCacheOnUnregisterExtensions() {
    ExtensionPoint<String> extensionPoint = buildExtensionPoint(String.class);

    List<Integer> sizeList = new ArrayList<>();
    extensionPoint.addExtensionPointListener(new ExtensionPointListener<String>() {
      @Override
      public void extensionRemoved(@NotNull String extension, @NotNull PluginDescriptor pluginDescriptor) {
        sizeList.add(extensionPoint.getExtensionList().size());
      }
    }, true, null);

    extensionPoint.registerExtension("first");
    assertThat(extensionPoint.getExtensionList().size()).isEqualTo(1);
    extensionPoint.unregisterExtensions((adapterName, adapter) -> false, false);
    assertThat(sizeList).contains(0);
  }

  @Test
  public void clientsCannotModifyCachedExtensions() {
    ExtensionPoint<Integer> extensionPoint = buildExtensionPoint(Integer.class);
    extensionPoint.registerExtension(4, disposable);
    extensionPoint.registerExtension(2, disposable);

    Integer[] extensions = extensionPoint.getExtensions();
    assertThat(extensions).containsExactly(4, 2);
    Arrays.sort(extensions);
    assertThat(extensions).containsExactly(2, 4);

    assertThat(extensionPoint.getExtensions()).containsExactly(4, 2);
  }

  @NotNull
  private static <T> ExtensionPointImpl<T> buildExtensionPoint(@NotNull Class<T> aClass) {
    return new InterfaceExtensionPoint<>(ExtensionsImplTest.EXTENSION_POINT_NAME_1, aClass, new MyComponentManager());
  }

  private static MyShootingComponentAdapter stringAdapter() {
    return new MyShootingComponentAdapter(String.class.getName());
  }

  private static final class MyShootingComponentAdapter extends XmlExtensionAdapter {
    private Runnable myFire;

    MyShootingComponentAdapter(@NotNull String implementationClass) {
      super(implementationClass, new DefaultPluginDescriptor("test"), null, LoadingOrder.ANY, null);
    }

    public synchronized void setFire(@Nullable Runnable fire) {
      myFire = fire;
    }

    @NotNull
    @Override
    public synchronized <T> T createInstance(@NotNull ComponentManager componentManager) {
      if (myFire != null) {
        myFire.run();
      }
      return super.createInstance(componentManager);
    }
  }

  static class MyComponentManager implements ComponentManager {
    private final DefaultPicoContainer myContainer = new DefaultPicoContainer();

    @Override
    public <T> T getComponent(@NotNull Class<T> interfaceClass) {
      return null;
    }

    @NotNull
    @Override
    public PicoContainer getPicoContainer() {
      return myContainer;
    }

    @NotNull
    @Override
    public MessageBus getMessageBus() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isDisposed() {
      return false;
    }

    @NotNull
    @Override
    public Condition<?> getDisposed() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void dispose() {

    }

    @Nullable
    @Override
    public <T> T getUserData(@NotNull Key<T> key) {
      return null;
    }

    @Override
    public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {

    }
  }
}
