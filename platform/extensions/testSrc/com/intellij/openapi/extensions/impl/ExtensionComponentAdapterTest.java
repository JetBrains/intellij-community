// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.extensions.DefaultPluginDescriptor;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * @author Alexander Kireyev
 */
public class ExtensionComponentAdapterTest {
  @Test
  public void testLoadingOrderReading() {
    assertEquals(LoadingOrder.ANY, createAdapter(LoadingOrder.ANY).getOrder());
    assertEquals(LoadingOrder.FIRST, createAdapter(LoadingOrder.FIRST).getOrder());
    assertEquals(LoadingOrder.LAST, createAdapter(LoadingOrder.LAST).getOrder());
    assertEquals(LoadingOrder.before("test"), createAdapter(LoadingOrder.readOrder("BEFORE test")).getOrder());
    assertEquals(LoadingOrder.after("test"), createAdapter(LoadingOrder.readOrder("AFTER test")).getOrder());
  }

  @Test
  public void testUnknownAttributes() throws IOException, JDOMException {
    String name = TestExtensionClassOne.class.getName();
    Element element = JDOMUtil.load("<bean implementation=\"123\"/>");
    DefaultPluginDescriptor descriptor = new DefaultPluginDescriptor("test");
    new XmlExtensionAdapter(name, descriptor, null, LoadingOrder.ANY, element, InterfaceExtensionImplementationClassResolver.INSTANCE).createInstance(new ExtensionPointImplTest.MyComponentManager());
  }

  @NotNull
  private static ExtensionComponentAdapter createAdapter(@NotNull LoadingOrder order) {
    return new XmlExtensionAdapter(Object.class.getName(), new DefaultPluginDescriptor("test"), null, order, null, InterfaceExtensionImplementationClassResolver.INSTANCE);
  }
}
