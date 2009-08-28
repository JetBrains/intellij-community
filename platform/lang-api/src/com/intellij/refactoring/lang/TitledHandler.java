package com.intellij.refactoring.lang;

import com.intellij.refactoring.RefactoringActionHandler;

/**
 * @author yole
 */
public interface TitledHandler extends RefactoringActionHandler {
  String getActionTitle();
}
