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

import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.AreaPicoContainer;
import junit.framework.TestCase;
import org.picocontainer.MutablePicoContainer;
import org.picocontainer.PicoContainer;
import org.picocontainer.PicoInitializationException;
import org.picocontainer.PicoIntrospectionException;
import org.picocontainer.defaults.DefaultPicoContainer;
import org.picocontainer.defaults.AbstractComponentAdapter;
import org.jdom.Element;

import java.util.List;

/**
 * @author mike
 */
public class ExtensionsAreaTest extends TestCase {
  private ExtensionsAreaImpl myExtensionsArea;
  private MutablePicoContainer myPicoContainer;

  protected void setUp() throws Exception {
    super.setUp();

    myExtensionsArea = new ExtensionsAreaImpl("foo", null, new DefaultPicoContainer(), new Extensions.SimpleLogProvider());
    myPicoContainer = myExtensionsArea.getPicoContainer();
  }

  public void testGetComponentAdapterDoesntDuplicateAdapters() throws Exception {
    myPicoContainer.registerComponentImplementation("runnable", ExtensionsAreaTest.class);

    final List adapters = myPicoContainer.getComponentAdaptersOfType(ExtensionsAreaTest.class);
    assertEquals(1, adapters.size());
  }

  public void testNoCreateOnUnregisterElement() {
    myExtensionsArea.registerExtensionPoint("test.ep", TestClass.class.getName(), ExtensionPoint.Kind.BEAN_CLASS);
    final Element element = ExtensionComponentAdapterTest.readElement("<extension point=\"test.ep\"/>");
    TestClass.ourCreationCount = 0;
    myExtensionsArea.registerExtension("test", element);
    assertEquals(0, TestClass.ourCreationCount);
    myExtensionsArea.unregisterExtension("test", element);
    assertEquals(0, TestClass.ourCreationCount);
  }

  public void testNoCreateOnUnregisterExisting() {
    myExtensionsArea.registerExtensionPoint("test.ep", TestClass.class.getName(), ExtensionPoint.Kind.BEAN_CLASS);
    final Element element = ExtensionComponentAdapterTest.readElement("<extension point=\"test.ep\"/>");
    TestClass.ourCreationCount = 0;
    myExtensionsArea.registerExtension("test", element);
    assertEquals(0, TestClass.ourCreationCount);
    final AreaPicoContainer container = myExtensionsArea.getPicoContainer();
    List instances = container.getComponentInstancesOfType(TestClass.class);
    assertEquals(1, instances.size());
    assertEquals(1, TestClass.ourCreationCount);
    final Object[] extensions = myExtensionsArea.getExtensionPoint("test.ep").getExtensions();
    assertEquals(1, extensions.length);
    instances = container.getComponentInstancesOfType(TestClass.class);
    assertEquals(1, instances.size());
    container.registerComponent(new AbstractComponentAdapter(new Object(), Object.class) {
      public Object getComponentInstance(PicoContainer container) throws PicoInitializationException, PicoIntrospectionException {
        fail("Should not be invoked");
        throw new Error(); // not reached
      }

      public void verify(PicoContainer container) throws PicoIntrospectionException {
      }
    });
    final TestClass extension = new TestClass();
    myExtensionsArea.getExtensionPoint("test.ep").registerExtension(extension);
    myExtensionsArea.unregisterExtension("test", element);
    myExtensionsArea.getExtensionPoint("test.ep").unregisterExtension(extension);
  }

  public static class TestClass {
    public static int ourCreationCount = 0;

    public TestClass() {
      ourCreationCount++;
    }
  }
}
