/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.lang.refactoring.DefaultRefactoringSupportProvider;
import com.intellij.lang.refactoring.RefactoringSupportProvider;

public class LanguageRefactoringSupport extends LanguageExtension<RefactoringSupportProvider> {
  public static final LanguageRefactoringSupport INSTANCE = new LanguageRefactoringSupport();

  private LanguageRefactoringSupport() {
    super("com.intellij.lang.refactoringSupport", new DefaultRefactoringSupportProvider());
  }
}