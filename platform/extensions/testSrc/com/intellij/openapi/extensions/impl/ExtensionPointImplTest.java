// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.extensions.*;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.picocontainer.defaults.DefaultPicoContainer;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * @author AKireyev
 */
public class ExtensionPointImplTest {
  @Test
  public void testCreate() {
    ExtensionPoint<Integer> extensionPoint = buildExtensionPoint(Integer.class);
    assertThat(extensionPoint.getName()).isEqualTo(ExtensionsImplTest.EXTENSION_POINT_NAME_1);
    assertThat(extensionPoint.getClassName()).isEqualTo(Integer.class.getName());
  }

  @Test
  public void testUnregisterObject() {
    ExtensionPoint<Integer> extensionPoint = buildExtensionPoint(Integer.class);
    extensionPoint.registerExtension(new Integer(123));
    Object[] extensions = extensionPoint.getExtensions();
    assertThat(extensions).hasSize(1);
    extensionPoint.unregisterExtension(new Integer(123));
    extensions = extensionPoint.getExtensions();
    assertThat(extensions).isEmpty();
  }

  @Test
  public void testRegisterUnregisterExtension() {
    final AreaInstance area = new AreaInstance() {};
    final ExtensionPoint<Object> extensionPoint = new ExtensionPointImpl<>(
      "an.extension.point", Object.class.getName(), ExtensionPoint.Kind.INTERFACE, buildExtensionArea(), area,
      new UndefinedPluginDescriptor());

    final boolean[] flags = new boolean[2];
    Extension extension = new Extension() {
      @Override
      public void extensionAdded(@NotNull ExtensionPoint extensionPoint1) {
        assertThat(extensionPoint1).isSameAs(extensionPoint);
        assertThat(extensionPoint1.getArea()).isSameAs(area);
        flags[0] = true;
      }

      @Override
      public void extensionRemoved(@NotNull ExtensionPoint extensionPoint1) {
        assertThat(extensionPoint1).isSameAs(extensionPoint);
        assertThat(extensionPoint1.getArea()).isSameAs(area);
        flags[1] = true;
      }
    };

    extensionPoint.registerExtension(extension);
    assertThat(flags[0]).describedAs("Register call is missed").isTrue();
    assertThat(flags[1]).isFalse();

    extensionPoint.unregisterExtension(extension);
    assertThat(flags[1]).describedAs("Unregister call is missed").isTrue();
  }

  @Test
  public void testRegisterObject() {
    ExtensionPoint<Integer> extensionPoint = buildExtensionPoint(Integer.class);
    extensionPoint.registerExtension(new Integer(123));
    Object[] extensions = extensionPoint.getExtensions();
    assertThat(extensions).describedAs("One extension").hasSize(1);
    assertThat(extensions).isInstanceOf(Integer[].class);
    assertThat(extensions[0]).isEqualTo(new Integer(123));
  }

  @Test
  public void testRegistrationOrder() {
    ExtensionPoint<Integer> extensionPoint = buildExtensionPoint(Integer.class);
    extensionPoint.registerExtension(new Integer(123));
    extensionPoint.registerExtension(new Integer(321), LoadingOrder.FIRST);
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
      public void extensionAdded(@NotNull Integer extension, final PluginDescriptor pluginDescriptor) {
        added[0] = true;
      }

      @Override
      public void extensionRemoved(@NotNull Integer extension, final PluginDescriptor pluginDescriptor) {
        removed[0] = true;
      }
    });
    assertThat(added[0]).isFalse();
    assertThat(removed[0]).isFalse();
    extensionPoint.registerExtension(new Integer(123));
    assertThat(added[0]).isTrue();
    assertThat(removed[0]).isFalse();
    added[0] = false;
    extensionPoint.unregisterExtension(new Integer(123));
    assertThat(added[0]).isFalse();
    assertThat(removed[0]).isTrue();
  }

  @Test
  public void testLateListener() {
    ExtensionPoint<Integer> extensionPoint = buildExtensionPoint(Integer.class);
    final boolean[] added = new boolean[1];
    extensionPoint.registerExtension(new Integer(123));
    assertThat(added[0]).isFalse();
    extensionPoint.addExtensionPointListener(new ExtensionPointListener<Integer>() {
      @Override
      public void extensionAdded(@NotNull Integer extension, final PluginDescriptor pluginDescriptor) {
        added[0] = true;
      }

      @Override
      public void extensionRemoved(@NotNull Integer extension, final PluginDescriptor pluginDescriptor) {
      }
    });
    assertThat(added[0]).isTrue();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testIncompatibleExtension() {
    ExtensionPoint extensionPoint = buildExtensionPoint(Integer.class);

    try {
      extensionPoint.registerExtension(new Double(0));
      fail("must throw");
    }
    catch (AssertionError ignored) {
    }

    assertThat(extensionPoint.getExtensions()).isEmpty();

    extensionPoint.registerExtension(new Integer(0));
    assertThat(extensionPoint.getExtensions()).hasSize(1);
  }

  @Test
  public void testIncompatibleAdapter() {
    ExtensionPointImpl<Integer> extensionPoint = buildExtensionPoint(Integer.class);

    extensionPoint.registerExtensionAdapter(stringAdapter());

    try {
      assertThat(extensionPoint.getExtensions()).isEmpty();
      fail("must throw");
    }
    catch (AssertionError ignored) {
    }
  }

  @Test
  public void testCompatibleAdapter() {
    ExtensionPointImpl<Integer> extensionPoint = buildExtensionPoint(Integer.class);
    extensionPoint.registerExtension(new Integer(0));
    assertThat(extensionPoint.getExtensions()).hasSize(1);
  }

  @Test
  public void testCancelledRegistration() {
    ExtensionPoint<String> extensionPoint = buildExtensionPoint(String.class);
    MyShootingComponentAdapter adapter = stringAdapter();

    extensionPoint.registerExtension("first");
    assertThat(extensionPoint.getExtensions()).hasSize(1);

    extensionPoint.registerExtension("second", LoadingOrder.FIRST);  // registers a wrapping adapter
    ((ExtensionPointImpl)extensionPoint).registerExtensionAdapter(adapter);
    adapter.setFire(true);
    try {
      extensionPoint.getExtensions();
      fail("PCE expected");
    }
    catch (ProcessCanceledException ignored) { }

    adapter.setFire(false);
    String[] extensions = extensionPoint.getExtensions();
    assertThat(extensions[0]).isEqualTo("second");
    assertThat(new SmartList<>(extensions[1])).containsAnyOf("", "first");
    assertThat(new SmartList<>(extensions[2])).containsAnyOf("", "first");
    assertThat(extensions[2]).isNotEqualTo(extensions[1]);
  }

  @Test
  public void testListenerNotifications() {
    ExtensionPoint<String> extensionPoint = buildExtensionPoint(String.class);
    final List<String> extensions = ContainerUtil.newArrayList();
    extensionPoint.addExtensionPointListener(new ExtensionPointListener<String>() {
      @Override
      public void extensionAdded(@NotNull String extension, @Nullable PluginDescriptor pluginDescriptor) {
        extensions.add(extension);
      }
    });
    MyShootingComponentAdapter adapter = stringAdapter();

    extensionPoint.registerExtension("first");
    assertThat(extensions).contains("first");

    extensionPoint.registerExtension("second", LoadingOrder.FIRST);
    ((ExtensionPointImpl)extensionPoint).registerExtensionAdapter(adapter);
    adapter.setFire(true);
    try {
      extensionPoint.getExtensions();
      fail("PCE expected");
    }
    catch (ProcessCanceledException ignored) { }
    assertThat(extensions).contains("first", "second");

    adapter.setFire(false);
    extensionPoint.getExtensions();
    assertThat(extensions).contains("first", "second", "");
  }

  @Test
  public void clientsCannotModifyCachedExtensions() {
    ExtensionPoint<Integer> extensionPoint = buildExtensionPoint(Integer.class);
    extensionPoint.registerExtension(4);
    extensionPoint.registerExtension(2);

    Integer[] extensions = extensionPoint.getExtensions();
    assertThat(extensions).containsExactly(4, 2);
    Arrays.sort(extensions);
    assertThat(extensions).containsExactly(2, 4);

    assertThat(extensionPoint.getExtensions()).containsExactly(4, 2);
  }

  private static <T> ExtensionPointImpl<T> buildExtensionPoint(Class<T> aClass) {
    return new ExtensionPointImpl<>(
      ExtensionsImplTest.EXTENSION_POINT_NAME_1, aClass.getName(), ExtensionPoint.Kind.INTERFACE,
      buildExtensionArea(), null, new UndefinedPluginDescriptor());
  }

  private static ExtensionsAreaImpl buildExtensionArea() {
    return new ExtensionsAreaImpl(new DefaultPicoContainer());
  }

  private static MyShootingComponentAdapter stringAdapter() {
    return new MyShootingComponentAdapter(String.class.getName());
  }

  private static class MyShootingComponentAdapter extends ExtensionComponentAdapter {
    private boolean myFire;

    MyShootingComponentAdapter(@NotNull String implementationClass) {
      super(implementationClass, ExtensionComponentAdapterTest.readElement("<bean/>"), new DefaultPicoContainer(), new DefaultPluginDescriptor("test"), false);
    }

    public void setFire(boolean fire) {
      myFire = fire;
    }

    @Override
    public Object getExtension() {
      if (myFire) {
        throw new ProcessCanceledException();
      }
      else {
        return super.getExtension();
      }
    }
  }
}
