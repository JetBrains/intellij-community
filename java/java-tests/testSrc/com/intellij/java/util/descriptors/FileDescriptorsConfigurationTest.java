// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.util.descriptors;

import com.intellij.util.descriptors.ConfigFileInfo;
import com.intellij.util.descriptors.ConfigFileInfoSet;
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
