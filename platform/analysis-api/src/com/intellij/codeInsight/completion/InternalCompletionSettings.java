// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

public class InternalCompletionSettings {
  public static @NotNull InternalCompletionSettings getInstance() {
    return ApplicationManager.getApplication().getService(InternalCompletionSettings.class);
  }

  public boolean mayStartClassNameCompletion(CompletionResultSet result) {
    return StringUtil.isNotEmpty(result.getPrefixMatcher().getPrefix());
  }
}
