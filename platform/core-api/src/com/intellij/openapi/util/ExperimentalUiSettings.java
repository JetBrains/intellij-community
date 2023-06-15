// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import com.intellij.openapi.application.ApplicationManager;

public interface ExperimentalUiSettings {
  static ExperimentalUiSettings getInstance() {
    return ApplicationManager.getApplication().getService(ExperimentalUiSettings.class);
  }
  boolean isEnabled();
}
