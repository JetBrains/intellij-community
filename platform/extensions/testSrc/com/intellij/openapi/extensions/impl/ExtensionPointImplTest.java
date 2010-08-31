/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import junit.framework.TestCase;
import org.picocontainer.defaults.DefaultPicoContainer;

/**
 * @author AKireyev
 */
public class ExtensionPointImplTest extends TestCase {
  public void testCreate() {
    ExtensionPointImpl extensionPoint = buildExtensionPoint();
    assertEquals(ExtensionsImplTest.EXTENSION_POINT_NAME_1, extensionPoint.getName());
    assertEquals(Integer.class.getName(), extensionPoint.getClassName());
  }

  private ExtensionPointImpl buildExtensionPoint() {
    return new ExtensionPointImpl(ExtensionsImplTest.EXTENSION_POINT_NAME_1, Integer.class.getName(), ExtensionPoint.Kind.INTERFACE, buildExtensionArea(), null, new Extensions.SimpleLogProvider(), new UndefinedPluginDescriptor());
  }

  private ExtensionsAreaImpl buildExtensionArea() {
    return new ExtensionsAreaImpl(new DefaultPicoContainer(), new Extensions.SimpleLogProvider());
  }

  public void testUnregisterObject() {
    ExtensionPointImpl extensionPoint = buildExtensionPoint();
    extensionPoint.registerExtension(new Integer(123));
    Object[] extensions = extensionPoint.getExtensions();
    assertEquals(1, extensions.length);
    extensionPoint.unregisterExtension(new Integer(123));
    extensions = extensionPoint.getExtensions();
    assertEquals(0, extensions.length);
  }

  public void testRegisterUnregister_Extension() {

    final AreaInstance area = new AreaInstance() {};
    final ExtensionPointImpl extensionPoint = new ExtensionPointImpl("an.extension.point", Object.class.getName(), ExtensionPoint.Kind.INTERFACE, buildExtensionArea(), area, new Extensions.SimpleLogProvider(), new UndefinedPluginDescriptor());

    final boolean[] flags = new boolean[2];
    Extension extension = new Extension() {
      public void extensionAdded(ExtensionPoint extensionPoint1) {
        assertSame(extensionPoint, extensionPoint1);
        assertSame(area, extensionPoint1.getArea());
        flags[0] = true;
      }

      public void extensionRemoved(ExtensionPoint extensionPoint1) {
        assertSame(extensionPoint, extensionPoint1);
        assertSame(area, extensionPoint1.getArea());
        flags[1] = true;
      }
    };

    extensionPoint.registerExtension(extension);
    assertTrue("Registratioon call is missed", flags[0]);
    assertFalse(flags[1]);

    extensionPoint.unregisterExtension(extension);
    assertTrue("UnRegistratioon call is missed", flags[1]);
  }

  public void testRegisterObject() {
    ExtensionPointImpl extensionPoint = buildExtensionPoint();
    extensionPoint.registerExtension(new Integer(123));
    Object[] extensions = extensionPoint.getExtensions();
    assertEquals("One extension", 1, extensions.length);
    assertEquals("Correct type", Integer[].class, extensions.getClass());
    assertEquals("Correct object", new Integer(123), extensions[0]);
  }

  public void testRegistrationOrder() {
    ExtensionPointImpl extensionPoint = buildExtensionPoint();
    extensionPoint.registerExtension(new Integer(123));
    extensionPoint.registerExtension(new Integer(321), LoadingOrder.FIRST);
    Object[] extensions = extensionPoint.getExtensions();
    assertEquals("One extension", 2, extensions.length);
    assertEquals("Correct object", new Integer(321), extensions[0]);
  }

  public void testListener() {
    ExtensionPointImpl extensionPoint = buildExtensionPoint();
    final boolean added[] = new boolean[1];
    final boolean removed[] = new boolean[1];
    extensionPoint.addExtensionPointListener(new ExtensionPointListener() {
      public void extensionAdded(Object extension, final PluginDescriptor pluginDescriptor) {
        added[0] = true;
      }

      public void extensionRemoved(Object extension, final PluginDescriptor pluginDescriptor) {
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

  public void testLateListener() {
    ExtensionPointImpl extensionPoint = buildExtensionPoint();
    final boolean added[] = new boolean[1];
    extensionPoint.registerExtension(new Integer(123));
    assertFalse(added[0]);
    extensionPoint.addExtensionPointListener(new ExtensionPointListener() {
      public void extensionAdded(Object extension, final PluginDescriptor pluginDescriptor) {
        added[0] = true;
      }

      public void extensionRemoved(Object extension, final PluginDescriptor pluginDescriptor) {
      }
    });
    assertTrue(added[0]);
  }
}
