// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.extensions.DefaultPluginDescriptor;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.util.JDOMUtil;
import org.jdom.JDOMException;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;

/**
 * @author yole
 */
public class XmlExtensionAdapterTest {
  @Test
  public void testIsLoadedFromAnyElementWithDefaultAttributes() throws IOException, JDOMException {
    String declaration = "<foo implementationClass=\"bar\" cleanupTool=\"false\"/>";
    XmlExtensionAdapter adapter = new XmlExtensionAdapter(
      ClassWithBooleanAttributes.class.getName(), new DefaultPluginDescriptor("abc"), null,
      LoadingOrder.ANY, JDOMUtil.load(declaration));
    adapter.createInstance(new ExtensionPointImplTest.MyComponentManager());
    Assert.assertTrue(adapter.isLoadedFromAnyElement(Collections.singletonList(JDOMUtil.load(declaration))));
  }
}
