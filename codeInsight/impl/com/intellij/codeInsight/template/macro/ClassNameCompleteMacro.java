
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.completion.CodeCompletionHandler;
import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.codeInsight.completion.LookupData;
import com.intellij.codeInsight.completion.ClassNameCompletionHandler;

public class ClassNameCompleteMacro extends BaseCompleteMacro {
  public ClassNameCompleteMacro() {
    super("classNameComplete");
  }

  CodeInsightActionHandler getCompletionHandler() {
    ClassNameCompletionHandler classNameCompletionHandler = new ClassNameCompletionHandler() {
      protected void handleEmptyLookup(CompletionContext context, LookupData lookupData) {
        // noithing
      }
    };

    return classNameCompletionHandler;
  }
}