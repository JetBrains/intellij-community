package com.intellij.refactoring.lang;

import com.intellij.lang.LanguageExtension;
import com.intellij.refactoring.RefactoringActionHandler;

/**
 * @author yole
 */
public class LanguageExtractInclude extends LanguageExtension<RefactoringActionHandler> {
  public static final LanguageExtractInclude INSTANCE = new LanguageExtractInclude();

  private LanguageExtractInclude() {
    super("com.intellij.refactoring.extractIncludeHandler");
  }
}
