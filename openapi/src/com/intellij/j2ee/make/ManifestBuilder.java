package com.intellij.j2ee.make;

import java.util.jar.Attributes;

public class ManifestBuilder {
  private static final Attributes.Name CREATED_BY = new Attributes.Name("Created-By");

  public static void setGlobalAttributes(Attributes mainAttributes) {
    setIfNone(mainAttributes, Attributes.Name.MANIFEST_VERSION, "1.0");
    setIfNone(mainAttributes, CREATED_BY, "IntelliJ IDEA");
  }

  private static void setIfNone(Attributes mainAttributes, Attributes.Name attrName, String value) {
    if (mainAttributes.getValue(attrName) == null) {
      mainAttributes.put(attrName, value);
    }
  }
}
