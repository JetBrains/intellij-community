// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle;

/**
 * Use {@link com.intellij.psi.codeStyle.JavaImportsLayoutSettings}
 */
@SuppressWarnings("DeprecatedIsStillUsed")
@Deprecated(forRemoval = true)
public interface ImportsLayoutSettings {
  boolean isLayoutStaticImportsSeparately();
  void setLayoutStaticImportsSeparately(boolean value);
  int getNamesCountToUseImportOnDemand();
  void setNamesCountToUseImportOnDemand(int value);
  int getClassCountToUseImportOnDemand();
  void setClassCountToUseImportOnDemand(int value);
  boolean isInsertInnerClassImports();
  void setInsertInnerClassImports(boolean value);
  boolean isUseSingleClassImports();
  void setUseSingleClassImports(boolean value);
  boolean isUseFqClassNames();
  void setUseFqClassNames(boolean value);
}