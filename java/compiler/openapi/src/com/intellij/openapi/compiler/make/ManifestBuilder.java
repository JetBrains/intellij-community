// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.compiler.make;

import com.intellij.openapi.application.ApplicationNamesInfo;
import org.jetbrains.annotations.NonNls;

import java.util.jar.Attributes;

public final class ManifestBuilder {
  private static final @NonNls String NAME = "Created-By";
  private static final Attributes.Name CREATED_BY = new Attributes.Name(NAME);

  private ManifestBuilder() {
  }

  public static void setGlobalAttributes(Attributes mainAttributes) {
    setVersionAttribute(mainAttributes);
    setIfNone(mainAttributes, CREATED_BY, ApplicationNamesInfo.getInstance().getFullProductName());
  }

  public static void setVersionAttribute(Attributes mainAttributes) {
    setIfNone(mainAttributes, Attributes.Name.MANIFEST_VERSION, "1.0");
  }

  private static void setIfNone(Attributes mainAttributes, Attributes.Name attrName, String value) {
    if (mainAttributes.getValue(attrName) == null) {
      mainAttributes.put(attrName, value);
    }
  }
}
