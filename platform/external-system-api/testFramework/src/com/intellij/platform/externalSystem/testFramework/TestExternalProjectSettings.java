// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.externalSystem.testFramework;

import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import org.jetbrains.annotations.NotNull;

public class TestExternalProjectSettings extends ExternalProjectSettings {

  @Override
  public @NotNull ExternalProjectSettings clone() {
    throw new UnsupportedOperationException();
  }
}
