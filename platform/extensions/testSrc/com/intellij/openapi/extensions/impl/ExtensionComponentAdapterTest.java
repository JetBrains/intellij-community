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

import com.intellij.openapi.extensions.DefaultPluginDescriptor;
import com.intellij.openapi.extensions.LoadingOrder;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jmock.cglib.MockObjectTestCase;
import org.picocontainer.defaults.DefaultPicoContainer;

import java.io.IOException;
import java.io.StringReader;

/**
 * @author Alexander Kireyev
 */
public class ExtensionComponentAdapterTest extends MockObjectTestCase {
  public void testLoadingOrderReading() {
    assertEquals(LoadingOrder.ANY, createAdapter("<extension/>").getOrder());
    assertEquals(LoadingOrder.FIRST, createAdapter("<extension order=\"FIRST\"/>").getOrder());
    assertEquals(LoadingOrder.LAST, createAdapter("<extension order=\"LAST\"/>").getOrder());
    assertEquals(LoadingOrder.before("test"), createAdapter("<extension order=\"BEFORE test\"/>").getOrder());
    assertEquals(LoadingOrder.after("test"), createAdapter("<extension order=\"AFTER test\"/>").getOrder());
  }

  public void testUnknownAttributes() {
    final DefaultPicoContainer container = new DefaultPicoContainer();
    final ExtensionComponentAdapter extensionComponentAdapter =
          new ExtensionComponentAdapter(TestExtensionClassOne.class.getName(), readElement("<bean implementation=\"123\"/>"), container, new DefaultPluginDescriptor("test"), false);
    extensionComponentAdapter.getComponentInstance(container);
  }

  private ExtensionComponentAdapter createAdapter(String text) {
    Element extensionElement = readElement(text);

    ExtensionComponentAdapter adapter = new ExtensionComponentAdapter(Object.class.getName(), extensionElement, new DefaultPicoContainer(), new DefaultPluginDescriptor(""), false);
    return adapter;
  }

  static Element readElement(String text) {
    Element extensionElement1 = null;
    try {
      extensionElement1 = new SAXBuilder().build(new StringReader(text)).getRootElement();
    }
    catch (JDOMException e) {
      throw new RuntimeException(e);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    Element extensionElement = extensionElement1;
    return extensionElement;
  }

}
