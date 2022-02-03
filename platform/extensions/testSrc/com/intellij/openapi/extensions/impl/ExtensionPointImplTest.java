// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions.impl;

import com.intellij.diagnostic.ActivityCategory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Test;
import org.picocontainer.PicoContainer;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.*;

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
    ExtensionPointImpl<Integer> extensionPoint = buildExtensionPoint(Integer.class);
    assertThat(extensionPoint.getName()).isEqualTo("ext.point.one");
    assertThat(extensionPoint.getClassName()).isEqualTo(Integer.class.getName());
  }

  @Test
  public void testUnregisterObject() {
    ExtensionPoint<Integer> extensionPoint = buildExtensionPoint(Integer.class);
    extensionPoint.registerExtension(123, disposable);
    assertThat(extensionPoint.getExtensionList()).hasSize(1);

    Disposer.dispose(disposable);
    disposable = null;

    assertThat(extensionPoint.getExtensionList()).isEmpty();
  }

  @Test
  public void testRegisterObject() {
    ExtensionPoint<Integer> extensionPoint = buildExtensionPoint(Integer.class);
    extensionPoint.registerExtension(123, disposable);
    Object[] extensions = extensionPoint.getExtensions();
    assertThat(extensions).describedAs("One extension").hasSize(1);
    assertThat(extensions).isInstanceOf(Integer[].class);
    assertThat(extensions[0]).isEqualTo(123);
  }

  @Test
  public void testRegistrationOrder() {
    ExtensionPoint<Integer> extensionPoint = buildExtensionPoint(Integer.class);
    extensionPoint.registerExtension(123, disposable);
    extensionPoint.registerExtension(321, LoadingOrder.FIRST, disposable);
    Object[] extensions = extensionPoint.getExtensions();
    assertThat(extensions).hasSize(2);
    assertThat(extensions[0]).isEqualTo(321);
  }

  @Test
  public void testListener() {
    ExtensionPoint<Integer> extensionPoint = buildExtensionPoint(Integer.class);
    final boolean[] added = new boolean[1];
    final boolean[] removed = new boolean[1];
    extensionPoint.addExtensionPointListener(new ExtensionPointListener<Integer>() {
      @Override
      public void extensionAdded(@NotNull Integer extension, final @NotNull PluginDescriptor pluginDescriptor) {
        added[0] = true;
      }

      @Override
      public void extensionRemoved(@NotNull Integer extension, final @NotNull PluginDescriptor pluginDescriptor) {
        removed[0] = true;
      }
    }, true, null);
    assertThat(added[0]).isFalse();
    assertThat(removed[0]).isFalse();
    extensionPoint.registerExtension(123, disposable);
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
    extensionPoint.registerExtension(123, disposable);
    //noinspection ConstantConditions
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
      extensionPoint.registerExtension((double)0, disposable);
      fail("must throw");
    }
    catch (RuntimeException ignored) {
    }

    assertThat(extensionPoint.getExtensionList()).isEmpty();

    extensionPoint.registerExtension(0, disposable);
    assertThat(extensionPoint.getExtensionList()).hasSize(1);
  }

  @Test
  public void testIncompatibleAdapter() {
    DefaultLogger.disableStderrDumping(disposable);

    ExtensionPointImpl<Integer> extensionPoint = buildExtensionPoint(Integer.class);
    extensionPoint.addExtensionAdapter(newStringAdapter());
    assertThatThrownBy(() -> {
      extensionPoint.getExtensionList();
    }).hasMessageContaining("Extension java.lang.String does not implement class java.lang.Integer");
  }

  @Test
  public void testCompatibleAdapter() {
    ExtensionPointImpl<Integer> extensionPoint = buildExtensionPoint(Integer.class);
    extensionPoint.registerExtension(0, disposable);
    assertThat(extensionPoint.getExtensions()).hasSize(1);
  }

  @Test
  public void cancelledRegistration() {
    doTestInterruptedAdapterProcessing(() -> {
      throw new ProcessCanceledException();
    }, (extensionPoint, adapter) -> {
      assertThatThrownBy(extensionPoint::getExtensionList).isInstanceOf(ProcessCanceledException.class);

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
      throw ExtensionNotApplicableException.create();
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

    MyShootingComponentAdapter adapter = newStringAdapter();
    extensionPoint.addExtensionAdapter(adapter);
    adapter.setFire(() -> {
      throw ExtensionNotApplicableException.create();
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
    MyShootingComponentAdapter adapter = newStringAdapter();

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

    MyShootingComponentAdapter adapter = newStringAdapter();
    ((ExtensionPointImpl<?>)extensionPoint).addExtensionAdapter(adapter);
    adapter.setFire(() -> {
      throw new ProcessCanceledException();
    });
    assertThatThrownBy(extensionPoint::getExtensionList).isInstanceOf(ProcessCanceledException.class);
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

    //noinspection deprecation
    extensionPoint.registerExtension("first");
    assertThat(extensionPoint.getExtensionList().size()).isEqualTo(1);
    extensionPoint.unregisterExtensions((adapterName, adapter) -> false, false);
    assertThat(sizeList).contains(0);
  }

  @Test
  public void clientsCannotModifyCachedExtensions() {
    ExtensionPointImpl<Integer> extensionPoint = buildExtensionPoint(Integer.class);
    extensionPoint.registerExtension(4, disposable);
    extensionPoint.registerExtension(2, disposable);

    Integer[] extensions = extensionPoint.getExtensions();
    assertThat(extensions).containsExactly(4, 2);
    Arrays.sort(extensions);
    assertThat(extensions).containsExactly(2, 4);

    assertThat(extensionPoint.getExtensionList()).containsExactly(4, 2);

    Function<Integer, String> f = it -> "foo";
    assertThat(ExtensionProcessingHelper.getByGroupingKey(extensionPoint, f.getClass(), "foo", f)).isEqualTo(extensionPoint.getExtensionList());
    assertThat(ExtensionProcessingHelper.getByKey(extensionPoint, 2, ExtensionPointImplTest.class, Function.identity(), Function.identity())).isEqualTo(2);
    Function<Integer, Integer> f2 = (Integer it) -> it * 2;
    assertThat(ExtensionProcessingHelper.getByKey(extensionPoint, 2, f2.getClass(), Function.identity(), f2)).isEqualTo(4);

    Function<Integer, Integer> filteringKeyMapper = it -> it < 3 ? it : null;
    assertThat(ExtensionProcessingHelper.getByKey(extensionPoint, 2, filteringKeyMapper.getClass(), filteringKeyMapper, Function.identity())).isEqualTo(2);
    assertThat(ExtensionProcessingHelper.getByKey(extensionPoint, 4, filteringKeyMapper.getClass(), filteringKeyMapper, Function.identity())).isNull();
    Function<@NotNull Integer, @Nullable Integer> f3 = (Integer it) -> (Integer)null;
    assertThat(ExtensionProcessingHelper.getByKey(extensionPoint, 4, f3.getClass(), Function.identity(), f3)).isNull();
  }

  @Test
  public void keyedExtensionDisposable() {
    BeanExtensionPoint<KeyedLazyInstance<Integer>> extensionPoint =
      new BeanExtensionPoint<>("foo", KeyedLazyInstance.class.getName(), new DefaultPluginDescriptor("test"), new MyComponentManager(), true);
    KeyedLazyInstance<Integer> extension = new KeyedLazyInstance<Integer>() {
      @Override
      public String getKey() {
        return "one";
      }

      @Override
      public @NotNull Integer getInstance() {
        return 1;
      }
    };
    extensionPoint.registerExtension(extension, LoadingOrder.ANY);
    Disposable disposable = ExtensionPointUtil.createKeyedExtensionDisposable(extension.getInstance(), extensionPoint);
    extensionPoint.unregisterExtension(extension);
    assertThat(Disposer.isDisposed(disposable)).isTrue();
    Disposer.dispose(extensionPoint.getComponentManager());
  }

  private static @NotNull <T> ExtensionPointImpl<T> buildExtensionPoint(@NotNull Class<T> aClass) {
    return new InterfaceExtensionPoint<>("ext.point.one", aClass.getName(), new DefaultPluginDescriptor("test"), new MyComponentManager(),
                                         aClass, false);
  }

  private static MyShootingComponentAdapter newStringAdapter() {
    return new MyShootingComponentAdapter(String.class.getName());
  }

  private static final class MyShootingComponentAdapter extends XmlExtensionAdapter {
    private Runnable myFire;

    MyShootingComponentAdapter(@NotNull String implementationClass) {
      super(implementationClass, new DefaultPluginDescriptor("test"), null, LoadingOrder.ANY, null,
            InterfaceExtensionImplementationClassResolver.INSTANCE);
    }

    public synchronized void setFire(@Nullable Runnable fire) {
      myFire = fire;
    }

    @Override
    public synchronized @Nullable <T> T createInstance(@NotNull ComponentManager componentManager) {
      if (myFire != null) {
        try {
          myFire.run();
        }
        catch (ExtensionNotApplicableException e) {
          return null;
        }
      }
      return super.createInstance(componentManager);
    }
  }

  static final class MyComponentManager implements ComponentManager {
    @Override
    public <T> T getComponent(@NotNull Class<T> interfaceClass) {
      return null;
    }

    @Override
    public <T> T @NotNull [] getComponents(@NotNull Class<T> baseClass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull PicoContainer getPicoContainer() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isInjectionForExtensionSupported() {
      return false;
    }

    @Override
    public @NotNull MessageBus getMessageBus() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isDisposed() {
      return false;
    }

    @Override
    public @NotNull Condition<?> getDisposed() {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T> T getService(@NotNull Class<T> serviceClass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T> T instantiateClassWithConstructorInjection(@NotNull Class<T> aClass,
                                                          @NotNull Object key,
                                                          @NotNull PluginId pluginId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T> @NotNull Class<T> loadClass(@NotNull String className, @NotNull PluginDescriptor pluginDescriptor) throws ClassNotFoundException {
      //noinspection unchecked
      return (Class<T>)Class.forName(className);
    }

    @Override
    public @NotNull ActivityCategory getActivityCategory(boolean isExtension) {
      return ActivityCategory.APP_EXTENSION;
    }

    @Override
    public void dispose() {
    }

    @Override
    public @Nullable <T> T getUserData(@NotNull Key<T> key) {
      return null;
    }

    @Override
    public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    }

    @Override
    public @NotNull RuntimeException createError(@NotNull @NonNls String message, @NotNull PluginId pluginId) {
      return new RuntimeException(message);
    }

    @Override
    public @NotNull RuntimeException createError(@NotNull @NonNls String message,
                                                 @Nullable Throwable error,
                                                 @NotNull PluginId pluginId,
                                                 @Nullable Map<String, String> attachments) {
      return new RuntimeException(message);
    }

    @Override
    public @NotNull RuntimeException createError(@NotNull Throwable error, @NotNull PluginId pluginId) {
      return new RuntimeException(error);
    }
  }
}
