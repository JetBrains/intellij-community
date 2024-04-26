// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers;

import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class BrowserSpecificSettings implements Cloneable {
  public abstract @NotNull Configurable createConfigurable();

  public @NotNull List<String> getAdditionalParameters() {
    return Collections.emptyList();
  }

  public @NotNull Map<String, String> getEnvironmentVariables() {
    return Collections.emptyMap();
  }

  @Override
  public BrowserSpecificSettings clone() {
    try {
      return (BrowserSpecificSettings)super.clone();
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }
}
