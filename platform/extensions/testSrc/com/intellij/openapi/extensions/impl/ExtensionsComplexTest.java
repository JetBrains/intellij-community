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

import com.intellij.openapi.extensions.AreaInstance;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Alexander Kireyev
 */
public class ExtensionsComplexTest {
  private static final String PLUGIN_NAME = "the.test.plugin";
  private static final String PLUGIN_NAME_2 = "another.test.plugin";

  private List<MyAreaInstance> myAreasToDispose;

  @Before
  public void setUp() {
    myAreasToDispose = new ArrayList<>();
    Extensions.registerAreaClass("area", null);
    Extensions.registerAreaClass("child_area", "area");
  }

  @After
  public void tearDown() {
    for (MyAreaInstance instance : myAreasToDispose) {
      Extensions.disposeArea(instance);
    }
    myAreasToDispose = null;
    for (ExtensionPoint extensionPoint : Extensions.getRootArea().getExtensionPoints()) {
      if (extensionPoint.getName().startsWith(PLUGIN_NAME) || extensionPoint.getName().startsWith(PLUGIN_NAME_2)) {
        Extensions.getRootArea().unregisterExtensionPoint(extensionPoint.getName());
      }
    }
  }

  @Test
  public void testPluginInit() {
    initExtensionPoints(
      PLUGIN_NAME, "<extensionPoints>\n" +
                   "  <extensionPoint name=\"extensionPoint\" beanClass=\"com.intellij.openapi.extensions.impl.XMLTestBean\" />\n" +
                   "  <extensionPoint name=\"dependentOne\" beanClass=\"com.intellij.openapi.extensions.impl.DependentObjectOne\" />\n" +
                   "</extensionPoints>", null);
    initExtensions(
      "  <extensions xmlns=\"the.test.plugin\">\n" +
      "    <extensionPoint>\n" +
      "      <prop1>321</prop1>\n" +
      "    </extensionPoint>\n" +
      "    <dependentOne/>\n" +
      "  </extensions>", null);

    assertTrue(Extensions.getRootArea().hasExtensionPoint("the.test.plugin.extensionPoint"));
    assertEquals(1, Extensions.getExtensions("the.test.plugin.extensionPoint").length);
    ExtensionPoint<XMLTestBean> ep = Extensions.getRootArea().getExtensionPoint("the.test.plugin.extensionPoint");
    XMLTestBean bean = ep.getExtension();
    assertNotNull(bean);
    assertEquals(321, bean.getProp1());
    assertEquals("the.test.plugin", bean.getPluginId().getIdString());

    DependentObjectOne dependentObjectOne = (DependentObjectOne)Extensions.getRootArea().getExtensionPoint("the.test.plugin.dependentOne").getExtension();
    assertNotNull(dependentObjectOne);
    assertEquals(1, dependentObjectOne.getTestBeans().length);

    AreaInstance areaInstance = new MyAreaInstance();
    Extensions.instantiateArea("area", areaInstance, null);

    initExtensionPoints(
      PLUGIN_NAME,
      "<extensionPoints>\n" +
      "  <extensionPoint name=\"dependentTwo\" beanClass=\"com.intellij.openapi.extensions.impl.DependentObjectTwo\" area=\"area\"/>\n" +
      "  <extensionPoint name=\"extensionPoint4area\" beanClass=\"com.intellij.openapi.extensions.impl.XMLTestBean\" area=\"area\" />\n" +
      "</extensionPoints>", areaInstance);

    initExtensions(
      "  <extensions xmlns=\"the.test.plugin\">\n" +
      "    <extensionPoint4area area=\"area\"/>\n" +
      "    <dependentTwo area=\"area\"/>\n" +
      "  </extensions>", areaInstance);

    ExtensionPoint extensionPoint = Extensions.getArea(areaInstance).getExtensionPoint("the.test.plugin.extensionPoint4area");
    assertNotNull(extensionPoint);
    assertSame(areaInstance, extensionPoint.getArea());
    assertNotNull(extensionPoint.getExtension());

    DependentObjectTwo dependentObjectTwo = (DependentObjectTwo)Extensions.getArea(areaInstance).getExtensionPoint("the.test.plugin.dependentTwo").getExtension();
    assertNotNull(dependentObjectTwo);
    assertSame(dependentObjectOne, dependentObjectTwo.getOne());
  }

  private static void initExtensionPoints(@NonNls String pluginName, @NonNls String data, AreaInstance instance) {
    final Element element = ExtensionComponentAdapterTest.readElement(data);
    for (final Object o : element.getChildren()) {
      Element child = (Element)o;
      Extensions.getArea(instance).registerExtensionPoint(pluginName, child);
    }
  }

  private static void initExtensions(@NonNls String data, AreaInstance instance) {
    final Element element = ExtensionComponentAdapterTest.readElement(data);
    for (final Object o : element.getChildren()) {
      Element child = (Element)o;
      Extensions.getArea(instance).registerExtension(element.getNamespaceURI(), child);
    }
  }

  private class MyAreaInstance implements AreaInstance {
    private MyAreaInstance() {
      myAreasToDispose.add(this);
    }
  }
}
