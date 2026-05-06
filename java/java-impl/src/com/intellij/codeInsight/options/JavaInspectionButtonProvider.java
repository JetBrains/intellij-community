// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.options;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptRegularComponent;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

public class JavaInspectionButtonProvider {
  public static @NotNull JavaInspectionButtonProvider getInstance() {
    return ApplicationManager.getApplication().getService(JavaInspectionButtonProvider.class);
  }
  
  public @NotNull OptRegularComponent button(@NotNull JavaControlButtonKind kind) {
    // Empty
    return OptPane.horizontalStack();
  }
}
