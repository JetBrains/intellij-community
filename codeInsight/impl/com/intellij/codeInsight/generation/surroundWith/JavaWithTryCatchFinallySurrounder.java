
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.codeInsight.CodeInsightBundle;

class JavaWithTryCatchFinallySurrounder extends JavaWithTryCatchSurrounder{
  public String getTemplateDescription() {
    return CodeInsightBundle.message("surround.with.try.catch.finally.template");
  }

  public JavaWithTryCatchFinallySurrounder() {
    myGenerateFinally = true;
  }
}