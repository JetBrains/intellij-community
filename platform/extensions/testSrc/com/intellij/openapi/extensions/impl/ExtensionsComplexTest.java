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

import com.intellij.openapi.extensions.AreaInstance;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import junit.framework.TestCase;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Alexander Kireyev
 */
public class ExtensionsComplexTest extends TestCase {
  private static final String PLUGIN_NAME = "the.test.plugin";
  private static final String PLUGIN_NAME_2 = "another.test.plugin";

  private static final String TEST_EP_NAME = "the.test.plugin.extensionPoint";

  private static final String TEST_DEPENDENT1_NAME = "the.test.plugin.dependentOne";
  private static final String TEST_DEPENDENT2_NAME = "the.test.plugin.dependentTwo";
  private static final String TEST_DEPENDENT3_NAME = "another.test.plugin.dependentThree";

  private static final String TEST_DEPENDENT_CHILD_NAME = "the.test.plugin.dependentChildThree";

  private static final String EXTENSION_POINTS_ROOT =
    "<extensionPoints>\n" +
    "  <extensionPoint name=\"extensionPoint\" beanClass=\"com.intellij.openapi.extensions.impl.XMLTestBean\" />\n" +
    "  <extensionPoint name=\"dependentOne\" beanClass=\"com.intellij.openapi.extensions.impl.DependentObjectOne\" />\n" +
    "</extensionPoints>";

  private static final String EXTENSION_POINTS_4_AREA =
    "<extensionPoints>\n" +
    "  <extensionPoint name=\"dependentTwo\" beanClass=\"com.intellij.openapi.extensions.impl.DependentObjectTwo\" area=\"area\"/>\n" +
    "  <extensionPoint name=\"extensionPoint4area\" beanClass=\"com.intellij.openapi.extensions.impl.XMLTestBean\" area=\"area\" />\n" +
    "</extensionPoints>";

  private static final String EXTENSION_POINTS_4_CHILD_AREA =
    "<extensionPoints>\n" +
    "  <extensionPoint name=\"dependentChildThree\" beanClass=\"com.intellij.openapi.extensions.impl.DependentObjectThree\" area=\"child_area\"/>\n" +
    "</extensionPoints>";

  private static final String EXTENSIONS_ROOT =
    "  <extensions xmlns=\"the.test.plugin\">\n" +
    "    <extensionPoint>\n" +
    "      <prop1>321</prop1>\n" +
    "    </extensionPoint>\n" +
    "    <dependentOne/>\n" +
    "  </extensions>";

  private static final String EXTENSIONS_ROOT_FAILING =
    "  <extensions>\n" +
    "    <extension point=\"the.test.plugin.extensionPoint\" implementation=\"com.intellij.openapi.extensions.impl.NonCreatableClass\" />\n" +
    "  </extensions>";

  private static final String EXTENSIONS_4_CHILD_AREA =
    "  <extensions xmlns=\"the.test.plugin\">\n" +
    "    <dependentChildThree area=\"child_area\"/>\n" +
    "  </extensions>";

  private static final String EXTENSION_POINTS_ROOT_2 =
    "  <extensionPoints>\n" +
    "    <extensionPoint name=\"anotherTestEP\" beanClass=\"com.intellij.openapi.extensions.impl.XMLTestBean\" />\n" +
    "  </extensionPoints>";

  private static final String EXTENSION_POINTS_4_AREA_2 =
    "  <extensionPoints>\n" +
    "    <extensionPoint area=\"area\" name=\"anotherTestEP4area\" beanClass=\"com.intellij.openapi.extensions.impl.XMLTestBean\" />\n" +
    "    <extensionPoint name=\"dependentThree\" beanClass=\"com.intellij.openapi.extensions.impl.DependentObjectThree\" area=\"area\"/>\n" +
    "  </extensionPoints>";

  private static final String EXTENSIONS_4_AREA_2 =
    "  <extensions xmlns=\"the.test.plugin\">\n" +
    "    <extensionPoint4area area=\"area\"/>\n" +
    "  </extensions>";

  private static final String EXTENSIONS_4_AREA_2_PLUS =
    "  <extensions xmlns=\"another.test.plugin\">\n" +
    "    <dependentThree area=\"area\"/>\n" +
    "  </extensions>";

  private List<MyAreaInstance> myAreasToDispose;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myAreasToDispose = new ArrayList<MyAreaInstance>();
    Extensions.registerAreaClass("area", null);
    Extensions.registerAreaClass("child_area", "area");
  }

  @Override
  protected void tearDown() throws Exception {
    for (MyAreaInstance instance : myAreasToDispose) {
      Extensions.disposeArea(instance);
    }
    myAreasToDispose = null;
    for (ExtensionPoint extensionPoint : Extensions.getRootArea().getExtensionPoints()) {
      if (extensionPoint.getName().startsWith(PLUGIN_NAME) || extensionPoint.getName().startsWith(PLUGIN_NAME_2)) {
        Extensions.getRootArea().unregisterExtensionPoint(extensionPoint.getName());
      }
    }
    super.tearDown();
  }

  public void testPluginInit() throws Exception {
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
    assertEquals(321, ((XMLTestBean)Extensions.getRootArea().getExtensionPoint("the.test.plugin.extensionPoint").getExtension()).getProp1());
    assertEquals("the.test.plugin", ((XMLTestBean)Extensions.getRootArea().getExtensionPoint("the.test.plugin.extensionPoint").getExtension()).getPluginId().getIdString());

    DependentObjectOne dependentObjectOne = (DependentObjectOne)Extensions.getRootArea().getExtensionPoint("the.test.plugin.dependentOne").getExtension();
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
    assertSame(dependentObjectOne, dependentObjectTwo.getOne());
  }

  public void _testPluginInitInAreas() throws Exception {
    initExtensionPoints(PLUGIN_NAME,
                        "<extensionPoints>\n" +
                        "  <extensionPoint name=\"extensionPoint\" beanClass=\"com.intellij.openapi.extensions.impl.XMLTestBean\" />\n" +
                        "  <extensionPoint name=\"dependentOne\" beanClass=\"com.intellij.openapi.extensions.impl.DependentObjectOne\" />\n" +
                        "</extensionPoints>", null);
    initExtensionPoints(PLUGIN_NAME_2,
                        "  <extensionPoints>\n" +
                        "    <extensionPoint name=\"anotherTestEP\" beanClass=\"com.intellij.openapi.extensions.impl.XMLTestBean\" />\n" +
                        "  </extensionPoints>", null);
    initExtensions(EXTENSIONS_ROOT, null);

    AreaInstance areaInstance1 = new MyAreaInstance();
    Extensions.instantiateArea("area", areaInstance1, null);

    initExtensionsInAREA(areaInstance1);

    AreaInstance areaInstance2 = new MyAreaInstance();
    Extensions.instantiateArea("area", areaInstance2, null);

    initExtensionsInAREA(areaInstance2);

    AreaInstance areaInstance3 = new MyAreaInstance();
    Extensions.instantiateArea("area", areaInstance3, null);

    initExtensionsInAREA(areaInstance3);

    checkAreaInitialized(areaInstance1);
    checkAreaInitialized(areaInstance2);
    checkAreaInitialized(areaInstance3);

    MyAreaInstance childAreaInstance1 = new MyAreaInstance();
    Extensions.instantiateArea("child_area", childAreaInstance1, areaInstance1);
    initExtensionsInCHILD_AREA(childAreaInstance1);
    MyAreaInstance childAreaInstance2 = new MyAreaInstance();
    Extensions.instantiateArea("child_area", childAreaInstance2, areaInstance2);
    initExtensionsInCHILD_AREA(childAreaInstance2);

    // check initialization through PicoContainer
    DependentObjectOne dependentObjectOne = (DependentObjectOne)Extensions.getRootArea().getExtensionPoint(TEST_DEPENDENT1_NAME).getExtension();
    assertEquals(1, dependentObjectOne.getTestBeans().length);
    DependentObjectTwo dependentObjectTwo = (DependentObjectTwo)Extensions.getArea(areaInstance1).getExtensionPoint(TEST_DEPENDENT2_NAME).getExtension();
    assertSame(dependentObjectOne, dependentObjectTwo.getOne());
    DependentObjectThree dependentObjectThree = (DependentObjectThree)Extensions.getArea(areaInstance1).getExtensionPoint(TEST_DEPENDENT3_NAME).getExtension();
    assertSame(dependentObjectOne, dependentObjectThree.getOne());
    assertSame(dependentObjectTwo, dependentObjectThree.getTwo());
    assertTrue(Arrays.asList(dependentObjectThree.getTestBeans()).containsAll(Arrays.asList(dependentObjectOne.getTestBeans())));

    // check PicoContainers
    assertTrue(Extensions.getRootArea().getPicoContainer().getComponentInstances().contains(dependentObjectOne));
    assertTrue(Extensions.getArea(areaInstance1).getPicoContainer().getComponentInstances().contains(dependentObjectTwo));
    assertTrue(Extensions.getArea(areaInstance1).getPicoContainer().getComponentInstances().contains(dependentObjectThree));
    assertFalse(Extensions.getArea(areaInstance2).getPicoContainer().getComponentInstances().contains(dependentObjectThree));

    assertTrue(Extensions.getRootArea().getPluginContainer(PLUGIN_NAME).getComponentInstances().contains(dependentObjectOne));
    assertFalse(Extensions.getRootArea().getPluginContainer(PLUGIN_NAME_2).getComponentInstances().contains(dependentObjectOne));
    assertTrue(Extensions.getArea(areaInstance1).getPluginContainer(PLUGIN_NAME).getComponentInstances().contains(dependentObjectTwo));
    assertFalse(Extensions.getArea(areaInstance1).getPluginContainer(PLUGIN_NAME_2).getComponentInstances().contains(dependentObjectTwo));
    assertTrue(Extensions.getArea(areaInstance1).getPluginContainer(PLUGIN_NAME_2).getComponentInstances().contains(dependentObjectThree));

    // check area inheritance
    DependentObjectThree dependentChild1 = (DependentObjectThree)Extensions.getArea(childAreaInstance1).getExtensionPoint(TEST_DEPENDENT_CHILD_NAME).getExtension();
    DependentObjectThree dependentChild2 = (DependentObjectThree)Extensions.getArea(childAreaInstance2).getExtensionPoint(TEST_DEPENDENT_CHILD_NAME).getExtension();
    assertSame(dependentObjectTwo, dependentChild1.getTwo());
    assertNotSame(dependentObjectTwo, dependentChild2.getTwo());

    // Check extensions
    assertNotSame(Extensions.getArea(areaInstance1).getExtensionPoint("the.test.plugin.extensionPoint4area").getExtensions()[0],
                  Extensions.getArea(areaInstance2).getExtensionPoint("the.test.plugin.extensionPoint4area").getExtensions()[0]);

    XMLTestBean ownExtension = new XMLTestBean();
    ownExtension.setProp1(54321);
    Extensions.getArea(areaInstance1).getExtensionPoint("the.test.plugin.extensionPoint4area").registerExtension(ownExtension);
    ExtensionPoint ep = Extensions.getArea(areaInstance2).getExtensionPoint("the.test.plugin.extensionPoint4area");
    ep.unregisterExtension(ep.getExtension());
    ep.unregisterExtension(ep.getExtension());
    assertEquals(3, Extensions.getArea(areaInstance1).getExtensionPoint("the.test.plugin.extensionPoint4area").getExtensions().length);
    assertEquals(54321, ((XMLTestBean)Extensions.getArea(areaInstance1).getExtensionPoint("the.test.plugin.extensionPoint4area").getExtensions()[2]).getProp1());
    assertEquals(0, Extensions.getArea(areaInstance2).getExtensionPoint("the.test.plugin.extensionPoint4area").getExtensions().length);
    assertEquals(2, Extensions.getArea(areaInstance3).getExtensionPoint("the.test.plugin.extensionPoint4area").getExtensions().length);
  }

  private void initExtensionsInCHILD_AREA(final MyAreaInstance childAreaInstance1) {
    initExtensionPoints(PLUGIN_NAME, EXTENSION_POINTS_4_CHILD_AREA, childAreaInstance1);
    initExtensions(EXTENSIONS_4_CHILD_AREA, childAreaInstance1);
  }

  private void initExtensionsInAREA(final AreaInstance areaInstance1) {
    initExtensionPoints(PLUGIN_NAME, EXTENSION_POINTS_4_AREA, areaInstance1);
    initExtensionPoints(PLUGIN_NAME_2, EXTENSION_POINTS_4_AREA_2, areaInstance1);
    initExtensions("  <extensions xmlns=\"the.test.plugin\">\n" +
                   "    <extensionPoint4area area=\"area\"/>\n" +
                   "    <dependentTwo area=\"area\"/>\n" +
                   "  </extensions>", areaInstance1);
    initExtensions(EXTENSIONS_4_AREA_2, areaInstance1);
    initExtensions(EXTENSIONS_4_AREA_2_PLUS, areaInstance1);
  }

  private static void checkAreaInitialized(AreaInstance areaInstance) {
    assertNotNull(Extensions.getArea(areaInstance).getExtensionPoint("the.test.plugin.extensionPoint4area").getExtension());
    assertEquals(2, Extensions.getArea(areaInstance).getExtensionPoint("the.test.plugin.extensionPoint4area").getExtensions().length);
    assertTrue(Extensions.getArea(areaInstance).hasExtensionPoint("another.test.plugin.anotherTestEP4area"));
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
