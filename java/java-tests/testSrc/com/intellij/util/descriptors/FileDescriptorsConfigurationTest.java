/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.descriptors;

import org.jdom.Element;

import java.io.IOException;

public class FileDescriptorsConfigurationTest extends DescriptorsTestCase {
  public void testAddDeleteDescriptor() throws IOException {
    final ConfigFileInfoSet configuration = createConfiguration();
    final ConfigFileInfo descriptor = createDescriptor();
    configuration.addConfigFile(descriptor);

    assertSame(descriptor, assertOneElement(configuration.getConfigFileInfos()));

    configuration.removeConfigFile(descriptor);
    assertEquals(0, configuration.getConfigFileInfos().size());
  }

  public void testWriteReadExternal() throws IOException {
    final ConfigFileInfoSet configuration = createConfiguration();
    final ConfigFileInfo descriptor = createDescriptor();
    configuration.addConfigFile(descriptor);

    final ConfigFileInfoSet configuration2 = createConfiguration();
    final Element root = new Element("root");
    configuration.writeExternal(root);
    configuration2.readExternal(root);
    assertEquals(descriptor, assertOneElement(configuration2.getConfigFileInfos()));
  }
}
