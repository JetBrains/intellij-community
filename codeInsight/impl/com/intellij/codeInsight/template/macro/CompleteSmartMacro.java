package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.completion.*;

public class CompleteSmartMacro extends BaseCompleteMacro {
  public CompleteSmartMacro() {
    super("completeSmart");
  }

  CodeInsightActionHandler getCompletionHandler() {
    return new CodeCompletionHandlerBase(CompletionType.SMART);
  }
}