// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.jlink;

import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class JLinkArtifactPropertiesStorageTest {

  @Test
  public void testDefault() {
    JLinkArtifactProperties properties = new JLinkArtifactProperties();
    assertRightAttributes(properties, 0, false);
  }

  @Test
  public void testSharedStringVerbose() {
    JLinkArtifactProperties properties = new JLinkArtifactProperties(JLinkArtifactProperties.CompressionLevel.FIRST, true);
    assertRightAttributes(properties, 1, true);
  }

  @Test
  public void testZip() {
    JLinkArtifactProperties properties = new JLinkArtifactProperties(JLinkArtifactProperties.CompressionLevel.SECOND, false);
    assertRightAttributes(properties, 2, false);
  }

  private static void assertRightAttributes(@NotNull JLinkArtifactProperties properties, int level, boolean verbose) {
    Element element = XmlSerializer.serialize(properties);

    assertEquals(2, element.getChildren().size());

    Element firstChild = element.getChildren().get(0);
    assertEquals(2, firstChild.getAttributes().size());
    assertEquals("compressionLevel", firstChild.getAttributeValue("name"));
    assertEquals(String.valueOf(level), firstChild.getAttributeValue("value"));

    Element secondChild = element.getChildren().get(1);
    assertEquals(2, secondChild.getAttributes().size());
    assertEquals("verbose", secondChild.getAttributeValue("name"));
    assertEquals(String.valueOf(verbose), secondChild.getAttributeValue("value"));
  }
}
