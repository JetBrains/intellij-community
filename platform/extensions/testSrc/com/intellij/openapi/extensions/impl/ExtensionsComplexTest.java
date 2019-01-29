// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.extensions.*;
import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
  public void testPluginInit() throws IOException, JDOMException {
    // constructor injection supported only for not root areas for performance reasons, so, create test root area
    AreaInstance testRootArea = new MyAreaInstance();
    Extensions.instantiateArea("area", testRootArea, null);

    initExtensionPoints(
      "<extensionPoints>\n" +
                   "  <extensionPoint name=\"extensionPoint\" beanClass=\"com.intellij.openapi.extensions.impl.XMLTestBean\" />\n" +
                   "  <extensionPoint name=\"dependentOne\" beanClass=\"com.intellij.openapi.extensions.impl.DependentObjectOne\" />\n" +
                   "</extensionPoints>", Extensions.getArea(testRootArea));
    initExtensions(
      "  <extensions xmlns=\"the.test.plugin\">\n" +
      "    <extensionPoint>\n" +
      "      <prop1>321</prop1>\n" +
      "    </extensionPoint>\n" +
      "    <dependentOne/>\n" +
      "  </extensions>", Extensions.getArea(testRootArea));

    ExtensionsArea rootArea = Extensions.getArea(testRootArea);
    assertThat(rootArea.hasExtensionPoint("the.test.plugin.extensionPoint")).isTrue();
    assertThat(Extensions.getExtensions("the.test.plugin.extensionPoint", testRootArea)).hasSize(1);
    ExtensionPoint<XMLTestBean> ep = rootArea.getExtensionPoint("the.test.plugin.extensionPoint");
    XMLTestBean bean = ep.getExtension();
    assertThat(bean).isNotNull();
    assertThat(bean.getProp1()).isEqualTo(321);
    assertThat(bean.getPluginId().getIdString()).isEqualTo("the.test.plugin");

    DependentObjectOne dependentObjectOne = (DependentObjectOne)rootArea.getExtensionPoint("the.test.plugin.dependentOne").getExtension();
    assertThat(dependentObjectOne).isNotNull();
    assertThat(dependentObjectOne.getTestBeans()).hasSize(1);

    AreaInstance areaInstance = new MyAreaInstance();
    Extensions.instantiateArea("child_area", areaInstance, testRootArea);

    initExtensionPoints(
      "<extensionPoints>\n" +
      "  <extensionPoint name=\"dependentTwo\" beanClass=\"com.intellij.openapi.extensions.impl.DependentObjectTwo\" area=\"area\"/>\n" +
      "  <extensionPoint name=\"extensionPoint4area\" beanClass=\"com.intellij.openapi.extensions.impl.XMLTestBean\" area=\"area\" />\n" +
      "</extensionPoints>", Extensions.getArea(areaInstance));

    initExtensions(
      "  <extensions xmlns=\"the.test.plugin\">\n" +
      "    <extensionPoint4area area=\"area\"/>\n" +
      "    <dependentTwo area=\"area\"/>\n" +
      "  </extensions>", Extensions.getArea(areaInstance));

    ExtensionPoint extensionPoint = Extensions.getArea(areaInstance).getExtensionPoint("the.test.plugin.extensionPoint4area");
    assertThat(extensionPoint).isNotNull();
    assertThat(extensionPoint.getArea()).isSameAs(areaInstance);
    assertThat(extensionPoint.getExtension()).isNotNull();

    DependentObjectTwo dependentObjectTwo = (DependentObjectTwo)Extensions.getArea(areaInstance).getExtensionPoint("the.test.plugin.dependentTwo").getExtension();
    assertThat(dependentObjectTwo).isNotNull();
    assertThat(dependentObjectOne).isSameAs(dependentObjectTwo.getOne());
  }

  private static void initExtensionPoints(@NonNls String data, @Nullable ExtensionsArea area) throws IOException, JDOMException {
    final Element element = JDOMUtil.load(data);
    for (Element child : element.getChildren()) {
      area.registerExtensionPoint(new DefaultPluginDescriptor(PluginId.getId(PLUGIN_NAME)), child);
    }
  }

  private static void initExtensions(@NonNls String data, @Nullable ExtensionsArea area) throws IOException, JDOMException {
    final Element element = JDOMUtil.load(data);
    for (final Element child : element.getChildren()) {
      ExtensionsImplTest.registerExtension((ExtensionsAreaImpl)area, element.getNamespaceURI(), child);
    }
  }

  private class MyAreaInstance implements AreaInstance {
    private MyAreaInstance() {
      myAreasToDispose.add(this);
    }
  }
}
