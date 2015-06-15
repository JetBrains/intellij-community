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

import com.intellij.openapi.extensions.DefaultPluginDescriptor;
import com.intellij.openapi.extensions.LoadingOrder;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.picocontainer.defaults.DefaultPicoContainer;

import java.io.IOException;
import java.io.StringReader;

import static org.junit.Assert.assertEquals;

/**
 * @author Alexander Kireyev
 */
public class ExtensionComponentAdapterTest {
  @Test
  public void testLoadingOrderReading() {
    assertEquals(LoadingOrder.ANY, createAdapter("<extension/>").getOrder());
    assertEquals(LoadingOrder.FIRST, createAdapter("<extension order=\"FIRST\"/>").getOrder());
    assertEquals(LoadingOrder.LAST, createAdapter("<extension order=\"LAST\"/>").getOrder());
    assertEquals(LoadingOrder.before("test"), createAdapter("<extension order=\"BEFORE test\"/>").getOrder());
    assertEquals(LoadingOrder.after("test"), createAdapter("<extension order=\"AFTER test\"/>").getOrder());
  }

  @Test
  public void testUnknownAttributes() {
    String name = TestExtensionClassOne.class.getName();
    Element element = readElement("<bean implementation=\"123\"/>");
    DefaultPicoContainer container = new DefaultPicoContainer();
    DefaultPluginDescriptor descriptor = new DefaultPluginDescriptor("test");
    new ExtensionComponentAdapter(name, element, container, descriptor, false).getComponentInstance(container);
  }

  private static ExtensionComponentAdapter createAdapter(String text) {
    Element element = readElement(text);
    return new ExtensionComponentAdapter(Object.class.getName(), element, new DefaultPicoContainer(), new DefaultPluginDescriptor(""), false);
  }

  @NotNull
  static Element readElement(@NotNull String text) {
    try {
      return new SAXBuilder().build(new StringReader(text)).getRootElement();
    }
    catch (JDOMException e) {
      throw new RuntimeException(e);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
