package com.intellij.codeInsight;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class ImportFilter {
  public static final ExtensionPointName<ImportFilter> EP_NAME = new ExtensionPointName<ImportFilter>("com.intellij.importFilter");

  public abstract boolean shouldUseFullyQualifiedName(@NotNull String classQualifiedName);

  public static boolean shouldImport(@NotNull String classQualifiedName) {
    for (ImportFilter filter : EP_NAME.getExtensions()) {
      if (filter.shouldUseFullyQualifiedName(classQualifiedName)) {
        return false;
      }
    }
    return true;
  }
}
