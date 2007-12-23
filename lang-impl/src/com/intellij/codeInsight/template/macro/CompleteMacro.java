
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.completion.CodeCompletionHandler;
import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.codeInsight.completion.LookupData;

public class CompleteMacro extends BaseCompleteMacro {
  public CompleteMacro() {
    super("complete");
  }

  CodeInsightActionHandler getCompletionHandler() {
    return new CodeCompletionHandler() {
      protected void handleEmptyLookup(CompletionContext context, LookupData lookupData) {
        // noithing
      }
    };
  }
}