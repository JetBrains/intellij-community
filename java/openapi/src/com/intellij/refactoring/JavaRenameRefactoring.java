package com.intellij.refactoring;

/**
 * @author yole
 */
public interface JavaRenameRefactoring extends RenameRefactoring {
  void setShouldRenameVariables(boolean value);

  void setShouldRenameInheritors(boolean value);
}
