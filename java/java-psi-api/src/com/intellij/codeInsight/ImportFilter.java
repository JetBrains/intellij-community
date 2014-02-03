package com.intellij.codeInsight;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class ImportFilter {
  public static final ExtensionPointName<ImportFilter> EP_NAME = new ExtensionPointName<ImportFilter>("com.intellij.importFilter");

  public abstract boolean shouldUseFullyQualifiedName(@Nullable PsiFile targetFile, @NotNull String classQualifiedName);

  public static boolean shouldImport(@Nullable PsiFile targetFile, @NotNull String classQualifiedName) {
    for (ImportFilter filter : EP_NAME.getExtensions()) {
      if (filter.shouldUseFullyQualifiedName(targetFile, classQualifiedName)) {
        return false;
      }
    }
    return true;
  }
}
