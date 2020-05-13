package com.intellij.codeInsight.completion;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

public class InternalCompletionSettings {
  @NotNull
  public static InternalCompletionSettings getInstance() {
    return ApplicationManager.getApplication().getService(InternalCompletionSettings.class);
  }

  public boolean mayStartClassNameCompletion(CompletionResultSet result) {
    return StringUtil.isNotEmpty(result.getPrefixMatcher().getPrefix());
  }
}
