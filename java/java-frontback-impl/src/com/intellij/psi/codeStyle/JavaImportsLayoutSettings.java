// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle;

public interface JavaImportsLayoutSettings extends ImportsLayoutSettings {
  PackageEntryTable getImportLayoutTable();
  PackageEntryTable getPackagesToUseImportOnDemand();
  @Override
  boolean isLayoutStaticImportsSeparately();
  @Override
  void setLayoutStaticImportsSeparately(boolean value);
  @Override
  int getNamesCountToUseImportOnDemand();
  @Override
  void setNamesCountToUseImportOnDemand(int value);
  @Override
  int getClassCountToUseImportOnDemand();
  @Override
  void setClassCountToUseImportOnDemand(int value);
  @Override
  boolean isInsertInnerClassImports();
  @Override
  void setInsertInnerClassImports(boolean value);
  @Override
  boolean isUseSingleClassImports();
  @Override
  void setUseSingleClassImports(boolean value);
  @Override
  boolean isUseFqClassNames();
  @Override
  void setUseFqClassNames(boolean value);
}
