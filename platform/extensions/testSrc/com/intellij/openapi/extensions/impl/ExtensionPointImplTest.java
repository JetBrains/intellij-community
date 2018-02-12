/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.extensions.*;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Test;
import org.picocontainer.defaults.DefaultPicoContainer;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * @author AKireyev
 */
public class ExtensionPointImplTest {
  private static final TestLogProvider ourTestLog = new TestLogProvider();

  @After
  public void tearDown() {
    ourTestLog.errors();
  }

  @Test
  public void testCreate() {
    ExtensionPoint<Integer> extensionPoint = buildExtensionPoint(Integer.class);
    assertEquals(ExtensionsImplTest.EXTENSION_POINT_NAME_1, extensionPoint.getName());
    assertEquals(Integer.class.getName(), extensionPoint.getClassName());
  }

  @Test
  public void testUnregisterObject() {
    ExtensionPoint<Integer> extensionPoint = buildExtensionPoint(Integer.class);
    extensionPoint.registerExtension(new Integer(123));
    Object[] extensions = extensionPoint.getExtensions();
    assertEquals(1, extensions.length);
    extensionPoint.unregisterExtension(new Integer(123));
    extensions = extensionPoint.getExtensions();
    assertEquals(0, extensions.length);
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
        assertSame(extensionPoint, extensionPoint1);
        assertSame(area, extensionPoint1.getArea());
        flags[0] = true;
      }

      @Override
      public void extensionRemoved(@NotNull ExtensionPoint extensionPoint1) {
        assertSame(extensionPoint, extensionPoint1);
        assertSame(area, extensionPoint1.getArea());
        flags[1] = true;
      }
    };

    extensionPoint.registerExtension(extension);
    assertTrue("Register call is missed", flags[0]);
    assertFalse(flags[1]);

    extensionPoint.unregisterExtension(extension);
    assertTrue("Unregister call is missed", flags[1]);
  }

  @Test
  public void testRegisterObject() {
    ExtensionPoint<Integer> extensionPoint = buildExtensionPoint(Integer.class);
    extensionPoint.registerExtension(new Integer(123));
    Object[] extensions = extensionPoint.getExtensions();
    assertEquals("One extension", 1, extensions.length);
    assertSame("Correct type", Integer[].class, extensions.getClass());
    assertEquals("Correct object", new Integer(123), extensions[0]);
  }

  @Test
  public void testRegistrationOrder() {
    ExtensionPoint<Integer> extensionPoint = buildExtensionPoint(Integer.class);
    extensionPoint.registerExtension(new Integer(123));
    extensionPoint.registerExtension(new Integer(321), LoadingOrder.FIRST);
    Object[] extensions = extensionPoint.getExtensions();
    assertEquals("One extension", 2, extensions.length);
    assertEquals("Correct object", new Integer(321), extensions[0]);
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
    assertFalse(added[0]);
    assertFalse(removed[0]);
    extensionPoint.registerExtension(new Integer(123));
    assertTrue(added[0]);
    assertFalse(removed[0]);
    added[0] = false;
    extensionPoint.unregisterExtension(new Integer(123));
    assertFalse(added[0]);
    assertTrue(removed[0]);
  }

  @Test
  public void testLateListener() {
    ExtensionPoint<Integer> extensionPoint = buildExtensionPoint(Integer.class);
    final boolean[] added = new boolean[1];
    extensionPoint.registerExtension(new Integer(123));
    assertFalse(added[0]);
    extensionPoint.addExtensionPointListener(new ExtensionPointListener<Integer>() {
      @Override
      public void extensionAdded(@NotNull Integer extension, final PluginDescriptor pluginDescriptor) {
        added[0] = true;
      }

      @Override
      public void extensionRemoved(@NotNull Integer extension, final PluginDescriptor pluginDescriptor) {
      }
    });
    assertTrue(added[0]);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testIncompatibleExtension() {
    ExtensionPoint extensionPoint = buildExtensionPoint(Integer.class);

    extensionPoint.registerExtension(new Double(0));
    assertThat(ourTestLog.errors(), hasSize(1));

    assertThat(extensionPoint.getExtensions(), emptyArray());
    assertThat(ourTestLog.errors(), empty());

    extensionPoint.registerExtension(new Integer(0));
    assertThat(extensionPoint.getExtensions(), arrayWithSize(1));
    assertThat(ourTestLog.errors(), empty());
  }

  @Test
  public void testIncompatibleAdapter() {
    ExtensionPoint<Integer> extensionPoint = buildExtensionPoint(Integer.class);

    ((ExtensionPointImpl)extensionPoint).registerExtensionAdapter(buildAdapter());
    assertThat(ourTestLog.errors(), empty());

    assertThat(extensionPoint.getExtensions(), emptyArray());
    assertThat(ourTestLog.errors(), hasSize(1));

    extensionPoint.registerExtension(new Integer(0));
    assertThat(extensionPoint.getExtensions(), arrayWithSize(1));
    assertThat(ourTestLog.errors(), empty());
  }

  @Test
  public void testCancelledRegistration() {
    ExtensionPoint<String> extensionPoint = buildExtensionPoint(String.class);
    MyShootingComponentAdapter adapter = buildAdapter();

    extensionPoint.registerExtension("first");
    assertThat(extensionPoint.getExtensions(), arrayWithSize(1));
    assertThat(ourTestLog.errors(), empty());

    extensionPoint.registerExtension("second", LoadingOrder.FIRST);  // registers a wrapping adapter
    ((ExtensionPointImpl)extensionPoint).registerExtensionAdapter(adapter);
    adapter.setFire(true);
    try {
      extensionPoint.getExtensions();
      fail("PCE expected");
    }
    catch (ProcessCanceledException ignored) { }
    assertThat(ourTestLog.errors(), empty());

    adapter.setFire(false);
    String[] extensions = extensionPoint.getExtensions();
    assertEquals("second", extensions[0]);
    assertThat(extensions[1], isOneOf("", "first"));
    assertThat(extensions[2], isOneOf("", "first"));
    assertNotEquals(extensions[2], extensions[1]);
    assertThat(ourTestLog.errors(), empty());
  }

  @Test
  public void testListenerNotifications() {
    ExtensionPoint<String> extensionPoint = buildExtensionPoint(String.class);
    final List<String> extensions = ContainerUtil.newArrayList();
    extensionPoint.addExtensionPointListener(new ExtensionPointListener.Adapter<String>() {
      @Override
      public void extensionAdded(@NotNull String extension, @Nullable PluginDescriptor pluginDescriptor) {
        extensions.add(extension);
      }
    });
    MyShootingComponentAdapter adapter = buildAdapter();

    extensionPoint.registerExtension("first");
    assertThat(extensions, contains("first"));
    assertThat(ourTestLog.errors(), empty());

    extensionPoint.registerExtension("second", LoadingOrder.FIRST);
    ((ExtensionPointImpl)extensionPoint).registerExtensionAdapter(adapter);
    adapter.setFire(true);
    try {
      extensionPoint.getExtensions();
      fail("PCE expected");
    }
    catch (ProcessCanceledException ignored) { }
    assertThat(extensions, contains("first", "second"));
    assertThat(ourTestLog.errors(), empty());

    adapter.setFire(false);
    extensionPoint.getExtensions();
    assertThat(extensions, contains("first", "second", ""));
    assertThat(ourTestLog.errors(), empty());
  }

  @Test
  public void clientsCannotModifyCachedExtensions() {
    ExtensionPoint<Integer> extensionPoint = buildExtensionPoint(Integer.class);
    extensionPoint.registerExtension(4);
    extensionPoint.registerExtension(2);

    Integer[] extensions = extensionPoint.getExtensions();
    assertEquals(ContainerUtil.newArrayList(4, 2), Arrays.asList(extensions));
    Arrays.sort(extensions);
    assertEquals(ContainerUtil.newArrayList(2, 4), Arrays.asList(extensions));

    assertEquals(ContainerUtil.newArrayList(4, 2), Arrays.asList(extensionPoint.getExtensions()));
  }

  private static <T> ExtensionPoint<T> buildExtensionPoint(Class<T> aClass) {
    return new ExtensionPointImpl<>(
      ExtensionsImplTest.EXTENSION_POINT_NAME_1, aClass.getName(), ExtensionPoint.Kind.INTERFACE,
      buildExtensionArea(), null, new UndefinedPluginDescriptor());
  }

  private static ExtensionsAreaImpl buildExtensionArea() {
    return new ExtensionsAreaImpl(new DefaultPicoContainer(), ourTestLog);
  }

  private static MyShootingComponentAdapter buildAdapter() {
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
