package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.extensions.Extensions;
import junit.framework.TestCase;
import org.picocontainer.MutablePicoContainer;
import org.picocontainer.defaults.DefaultPicoContainer;
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
    myExtensionsArea.registerExtensionPoint("test.ep", TestClass.class.getName());
    final Element element = ExtensionComponentAdapterTest.readElement("<extension point=\"test.ep\"/>");
    TestClass.ourCreationCount = 0;
    myExtensionsArea.registerExtension("test", element);
    assertEquals(0, TestClass.ourCreationCount);
    myExtensionsArea.unregisterExtension("test", element);
    assertEquals(0, TestClass.ourCreationCount);
  }

  public static class TestClass {
    public static int ourCreationCount = 0;

    public TestClass() {
      ourCreationCount++;
    }
  }
}
