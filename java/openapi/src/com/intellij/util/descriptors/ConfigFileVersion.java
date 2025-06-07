// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.descriptors;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NonNls;

public class ConfigFileVersion {
  private final String myName;
  private final @NonNls String myTemplateName;

  public ConfigFileVersion(final @NlsSafe String name, final @NonNls String templateName) {
    myName = name;
    myTemplateName = templateName;
  }

  public @NlsSafe String getName() {
    return myName;
  }

  public String getTemplateName() {
    return myTemplateName;
  }
}
