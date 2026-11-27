// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.util.descriptors;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.descriptors.ConfigFileInfo;
import com.intellij.util.descriptors.ConfigFileInfoSet;
import org.jdom.Element;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class FileDescriptorsConfigurationTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  @Test
  public void testAddDeleteDescriptor() throws IOException {
    DescriptorsTestHelper descriptors = new DescriptorsTestHelper(heavyFixture.get());
    final ConfigFileInfoSet configuration = descriptors.createConfiguration();
    final ConfigFileInfo descriptor = descriptors.createDescriptor();
    configuration.addConfigFile(descriptor);

    assertThat(configuration.getConfigFileInfos()).singleElement().isSameAs(descriptor);

    configuration.removeConfigFile(descriptor);
    assertEquals(0, configuration.getConfigFileInfos().size());
  }

  @Test
  public void testWriteReadExternal() throws IOException {
    DescriptorsTestHelper descriptors = new DescriptorsTestHelper(heavyFixture.get());
    final ConfigFileInfoSet configuration = descriptors.createConfiguration();
    final ConfigFileInfo descriptor = descriptors.createDescriptor();
    configuration.addConfigFile(descriptor);

    final ConfigFileInfoSet configuration2 = descriptors.createConfiguration();
    final Element root = new Element("root");
    configuration.writeExternal(root);
    configuration2.readExternal(root);
    assertThat(configuration2.getConfigFileInfos()).singleElement().isEqualTo(descriptor);
  }
}
