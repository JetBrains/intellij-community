package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.completion.*;

public class CompleteSmartMacro extends BaseCompleteMacro {
  public CompleteSmartMacro() {
    super("completeSmart");
  }

  CodeInsightActionHandler getCompletionHandler() {
    return new SmartCodeCompletionHandler() {
      @Override
      protected void handleEmptyLookup(final CompletionContext context, final CompletionParameters parameters, final CompletionProgressIndicator indicator) {
        super.handleEmptyLookup(context, parameters, indicator);
      }

    };
  }
}