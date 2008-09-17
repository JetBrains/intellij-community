
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.completion.*;

public class ClassNameCompleteMacro extends BaseCompleteMacro {
  public ClassNameCompleteMacro() {
    super("classNameComplete");
  }

  CodeInsightActionHandler getCompletionHandler() {

    return new CodeCompletionHandlerBase(CompletionType.CLASS_NAME) {
      @Override
      protected void handleEmptyLookup(final CompletionContext context, final CompletionParameters parameters, final CompletionProgressIndicator indicator) {
        // noithing
      }
    };
  }
}