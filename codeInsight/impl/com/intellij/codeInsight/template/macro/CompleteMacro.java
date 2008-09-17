package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.completion.*;

public class CompleteMacro extends BaseCompleteMacro {
  public CompleteMacro() {
    super("complete");
  }

  CodeInsightActionHandler getCompletionHandler() {
    return new CodeCompletionHandlerBase(CompletionType.BASIC) {
      @Override
      protected void handleEmptyLookup(final CompletionContext context, final CompletionParameters parameters, final CompletionProgressIndicator indicator) {
        // noithing
      }
    };
  }
}