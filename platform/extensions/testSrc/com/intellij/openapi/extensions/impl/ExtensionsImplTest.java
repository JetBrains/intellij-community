// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.pico.DefaultPicoContainer;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Test;
import org.picocontainer.MutablePicoContainer;

import java.io.IOException;
import java.util.Arrays;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ExtensionsImplTest {
  static final String EXTENSION_POINT_NAME_1 = "ext.point.one";

  private final Disposable disposable = Disposer.newDisposable();

  @After
  public void tearDown() {
    Disposer.dispose(disposable);
  }

  @Test
  public void testCreateAndAccess() {
    ExtensionsAreaImpl extensionsArea = new ExtensionsAreaImpl(new ExtensionPointImplTest.MyComponentManager());
    int numEP = extensionsArea.getExtensionPoints().length;
    registerInterfaceExtension(extensionsArea);
    assertEquals("Additional EP available", numEP + 1, extensionsArea.getExtensionPoints().length);
    assertThat(extensionsArea.getExtensionPoint(EXTENSION_POINT_NAME_1)).withFailMessage("EP by name available").isNotNull();
  }

  private static void registerInterfaceExtension(@NotNull ExtensionsAreaImpl extensionsArea) {
    extensionsArea.doRegisterExtensionPoint(EXTENSION_POINT_NAME_1, Integer.class.getName(), ExtensionPoint.Kind.INTERFACE);
  }

  @Test(expected = Throwable.class)
  public void testInvalidActions() {
    ExtensionsAreaImpl extensionsArea = new ExtensionsAreaImpl(new ExtensionPointImplTest.MyComponentManager());
    registerInterfaceExtension(extensionsArea);
    extensionsArea.doRegisterExtensionPoint(EXTENSION_POINT_NAME_1, Boolean.class.getName(), ExtensionPoint.Kind.INTERFACE);
    fail("Should not allow duplicate registration");
  }

  @Test
  public void testUnregisterEP() {
    ExtensionsAreaImpl extensionsArea = new ExtensionsAreaImpl(new ExtensionPointImplTest.MyComponentManager());
    int numEP = extensionsArea.getExtensionPoints().length;
    registerInterfaceExtension(extensionsArea);

    final boolean[] removed = {true};
    ExtensionPointImpl<Object> point = extensionsArea.getExtensionPoint(EXTENSION_POINT_NAME_1);
    point.addExtensionPointListener(new ExtensionPointListener<Object>() {
      @Override
      public void extensionAdded(@NotNull Object extension, @NotNull PluginDescriptor pluginDescriptor) {
        removed[0] = false;
      }

      @Override
      public void extensionRemoved(@NotNull Object extension, @NotNull PluginDescriptor pluginDescriptor) {
        removed[0] = true;
      }
    }, false, null);
    point.registerExtension(new Integer(123));
    extensionsArea.unregisterExtensionPoint(EXTENSION_POINT_NAME_1);
    assertThat(extensionsArea.getExtensionPoints().length).withFailMessage("Extension point should be removed").isEqualTo(numEP);
    assertThat(removed[0]).withFailMessage("Extension point disposed").isTrue();
  }

  @Test
  public void testTryPicoContainer() {
    DefaultPicoContainer rootContainer = new DefaultPicoContainer();
    rootContainer.registerComponentInstance("plugin1", new DefaultPicoContainer(rootContainer));
    rootContainer.registerComponentInstance("plugin2", new DefaultPicoContainer(rootContainer));
    MutablePicoContainer container1 = (MutablePicoContainer)rootContainer.getComponentInstance("plugin1");
    MutablePicoContainer container2 = (MutablePicoContainer)rootContainer.getComponentInstance("plugin2");
    container1.registerComponentImplementation("component1", MyComponent1.class);
    container1.registerComponentImplementation("component1.1", MyComponent1.class);
    container2.registerComponentImplementation("component2", MyComponent2.class);
    MyInterface1 testInstance = () -> { };
    rootContainer.registerComponentInstance(testInstance);
    MyComponent1 component1 = (MyComponent1)container1.getComponentInstance("component1");
    assertEquals(testInstance, component1.testObject);
    rootContainer.registerComponentInstance("component1", component1);
    MyComponent1 component11 = (MyComponent1)container1.getComponentInstance("component1.1");
    rootContainer.registerComponentInstance("component11", component11);
    MyComponent2 component2 = (MyComponent2)container2.getComponentInstance("component2");
    assertEquals(testInstance, component2.testObject);
    assertTrue(Arrays.asList(component2.comp1).contains(component1));
    assertTrue(Arrays.asList(component2.comp1).contains(component11));
    rootContainer.registerComponentInstance("component2", component2);
    rootContainer.registerComponentImplementation(MyTestComponent.class);
    MyTestComponent testComponent = (MyTestComponent)rootContainer.getComponentInstance(MyTestComponent.class);
    assertTrue(Arrays.asList(testComponent.comp1).contains(component1));
    assertTrue(Arrays.asList(testComponent.comp1).contains(component11));
    assertEquals(component2, testComponent.comp2);
  }

  @Test
  public void testTryPicoContainer2() {
    DefaultPicoContainer rootContainer = new DefaultPicoContainer();
    rootContainer.registerComponentImplementation("component1", MyComponent1.class);
    rootContainer.registerComponentImplementation("component1.1", MyComponent1.class);
    rootContainer.registerComponentImplementation("component2", MyComponent2.class);
    rootContainer.registerComponentImplementation(MyTestComponent.class);
    MyInterface1 testInstance = () -> { };
    rootContainer.registerComponentInstance(testInstance);
    MyTestComponent testComponent = (MyTestComponent)rootContainer.getComponentInstance(MyTestComponent.class);
    MyComponent2 component2 = (MyComponent2)rootContainer.getComponentInstance("component2");
    MyComponent1 component11 = (MyComponent1)rootContainer.getComponentInstance("component1.1");
    MyComponent1 component1 = (MyComponent1)rootContainer.getComponentInstance("component1");
    assertEquals(testInstance, component1.testObject);
    assertEquals(testInstance, component2.testObject);
    assertTrue(Arrays.asList(component2.comp1).contains(component1));
    assertTrue(Arrays.asList(component2.comp1).contains(component11));
    assertTrue(Arrays.asList(testComponent.comp1).contains(component1));
    assertTrue(Arrays.asList(testComponent.comp1).contains(component11));
    assertEquals(component2, testComponent.comp2);
  }

  @Test
  public void testExtensionsNamespaces() throws IOException, JDOMException {
    ExtensionsAreaImpl extensionsArea = new ExtensionsAreaImpl(new ExtensionPointImplTest.MyComponentManager());
    extensionsArea.doRegisterExtensionPoint("plugin.ep1", TestExtensionClassOne.class.getName(), ExtensionPoint.Kind.BEAN_CLASS);
    registerExtension(extensionsArea, "plugin", JDOMUtil.load(
        "<plugin:ep1 xmlns:plugin=\"plugin\" order=\"LAST\"><text>3</text></plugin:ep1>"));
    registerExtension(extensionsArea, "plugin", JDOMUtil.load(
        "<ep1 xmlns=\"plugin\" order=\"FIRST\"><text>1</text></ep1>"));
    registerExtension(extensionsArea, "plugin", JDOMUtil.load(
        "<extension point=\"plugin.ep1\"><text>2</text></extension>"));
    ExtensionPoint<?> extensionPoint = extensionsArea.getExtensionPoint("plugin.ep1");
    TestExtensionClassOne[] extensions = (TestExtensionClassOne[]) extensionPoint.getExtensions();
    assertEquals(3, extensions.length);
    assertEquals("1", extensions[0].getText());
    assertEquals("2", extensions[1].getText());
    assertEquals("3", extensions[2].getText());
  }

  @Test
  public void testExtensionsWithOrdering() throws IOException, JDOMException {
    ExtensionsAreaImpl extensionsArea = new ExtensionsAreaImpl(new ExtensionPointImplTest.MyComponentManager());
    extensionsArea.doRegisterExtensionPoint("ep1", TestExtensionClassOne.class.getName(), ExtensionPoint.Kind.BEAN_CLASS);
    registerExtension(extensionsArea, "", JDOMUtil.load(
        "<extension point=\"ep1\" order=\"LAST\"><text>3</text></extension>"));
    registerExtension(extensionsArea, "", JDOMUtil.load(
        "<extension point=\"ep1\" order=\"FIRST\"><text>1</text></extension>"));
    registerExtension(extensionsArea, "", JDOMUtil.load(
        "<extension point=\"ep1\"><text>2</text></extension>"));
    ExtensionPoint<?> extensionPoint = extensionsArea.getExtensionPoint("ep1");
    TestExtensionClassOne[] extensions = (TestExtensionClassOne[]) extensionPoint.getExtensions();
    assertEquals(3, extensions.length);
    assertEquals("1", extensions[0].getText());
    assertEquals("2", extensions[1].getText());
    assertEquals("3", extensions[2].getText());
  }

  @Test
  public void testExtensionsWithOrderingUpdate() throws IOException, JDOMException {
    ExtensionsAreaImpl extensionsArea = new ExtensionsAreaImpl(new ExtensionPointImplTest.MyComponentManager());
    extensionsArea.doRegisterExtensionPoint("ep1", TestExtensionClassOne.class.getName(), ExtensionPoint.Kind.BEAN_CLASS);
    registerExtension(extensionsArea, "", JDOMUtil.load("<extension point=\"ep1\" id=\"_7\" order=\"LAST\"><text>7</text></extension>"));
    registerExtension(extensionsArea, "", JDOMUtil.load("<extension point=\"ep1\" id=\"fst\" order=\"FIRST\"><text>1</text></extension>"));
    registerExtension(extensionsArea, "", JDOMUtil.load("<extension point=\"ep1\" id=\"id\"><text>3</text></extension>"));
    ExtensionPoint<TestExtensionClassOne> extensionPoint = extensionsArea.getExtensionPoint("ep1");
    TestExtensionClassOne[] extensions = extensionPoint.getExtensions();
    assertEquals(3, extensions.length);
    assertEquals("1", extensions[0].getText());
    assertEquals("3", extensions[1].getText());
    assertEquals("7", extensions[2].getText());
    TestExtensionClassOne extension = new TestExtensionClassOne("xxx");

    Disposable disposable2 = Disposer.newDisposable();
    extensionPoint.registerExtension(extension, disposable2);
    Disposer.dispose(disposable2);
    //noinspection UnusedAssignment
    disposable2 = null;

    registerExtension(extensionsArea, "", JDOMUtil.load("<extension point=\"ep1\" order=\"BEFORE id\"><text>2</text></extension>"));
    registerExtension(extensionsArea, "", JDOMUtil.load("<extension point=\"ep1\" order=\"AFTER id\"><text>4</text></extension>"));
    registerExtension(extensionsArea, "", JDOMUtil.load("<extension point=\"ep1\" order=\"last, after _7\"><text>8</text></extension>"));
    registerExtension(extensionsArea, "", JDOMUtil.load("<extension point=\"ep1\" order=\"after:id, before _7, after fst\"><text>5</text></extension>"));
    extensionPoint.registerExtension(new TestExtensionClassOne("6"), disposable);
    extensions = extensionPoint.getExtensions();
    assertEquals(8, extensions.length);
    assertEquals("1", extensions[0].getText());
    assertEquals("2", extensions[1].getText());
    assertEquals("3", extensions[2].getText());
    assertTrue("4".equals(extensions[3].getText()) || "5".equals(extensions[3].getText()) );
    assertTrue("4".equals(extensions[4].getText()) || "5".equals(extensions[4].getText()) );
    assertEquals("6", extensions[5].getText());
    assertEquals("7", extensions[6].getText());
    assertEquals("8", extensions[7].getText());
  }

  public static void registerExtension(ExtensionsAreaImpl area, @NotNull final String pluginName, @NotNull final Element extensionElement) {
    area.registerExtension(new DefaultPluginDescriptor(PluginId.getId(pluginName)), extensionElement, null);
  }

  public interface MyInterface1 extends Runnable {
  }

  public interface MyInterface2 {
  }

  public interface MyInterface3 extends Runnable {
  }

  public interface MyInterface4 extends MyInterface1, MyInterface2, MyInterface3 {
  }

  public static class MyClass implements MyInterface4 {
    @Override
    public void run() {
    }
  }

  public static class MyComponent1 {
    public MyInterface1 testObject;

    public MyComponent1(MyInterface1 testObject) {
      this.testObject = testObject;
    }
  }

  public static class MyComponent2 {
    public MyInterface1 testObject;
    public MyComponent1[] comp1;

    public MyComponent2(MyComponent1[] comp1, MyInterface1 testObject) {
      this.comp1 = comp1;
      this.testObject = testObject;
    }
  }

  public static class MyTestComponent {
    public MyComponent1[] comp1;
    public MyComponent2 comp2;

    public MyTestComponent(MyComponent1[] comp1, MyComponent2 comp2) {
      this.comp1 = comp1;
      this.comp2 = comp2;
    }
  }
}
