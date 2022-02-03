// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.externalSystem.testFramework;

import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 */
public class TestExternalProjectSettings extends ExternalProjectSettings {

  @NotNull
  @Override
  public ExternalProjectSettings clone() {
    throw new UnsupportedOperationException();
  }
}
