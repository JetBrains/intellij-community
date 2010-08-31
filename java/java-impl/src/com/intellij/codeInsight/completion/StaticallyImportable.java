package com.intellij.codeInsight.completion;

/**
 * @author peter
 */
public interface StaticallyImportable {
  void setShouldBeImported(boolean shouldImportStatic);

  boolean canBeImported();

  boolean willBeImported();
}
