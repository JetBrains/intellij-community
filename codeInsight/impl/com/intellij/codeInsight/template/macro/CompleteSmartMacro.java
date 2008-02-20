package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.codeInsight.completion.LookupData;
import com.intellij.codeInsight.completion.SmartCodeCompletionHandler;

public class CompleteSmartMacro extends BaseCompleteMacro {
  public CompleteSmartMacro() {
    super("completeSmart");
  }

  CodeInsightActionHandler getCompletionHandler() {
    return new SmartCodeCompletionHandler() {
      protected void handleEmptyLookup(CompletionContext context, LookupData lookupData) {
        // nothing
      }
    };
  }
}