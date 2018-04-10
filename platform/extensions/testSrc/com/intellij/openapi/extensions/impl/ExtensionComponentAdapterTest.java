// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  public static Element readElement(@NotNull String text) {
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
