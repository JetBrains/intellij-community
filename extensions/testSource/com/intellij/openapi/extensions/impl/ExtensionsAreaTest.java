package com.intellij.openapi.extensions.impl;

import junit.framework.TestCase;
import org.picocontainer.defaults.DefaultPicoContainer;
import org.picocontainer.MutablePicoContainer;

import java.util.List;

import com.intellij.openapi.extensions.Extensions;

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
}
