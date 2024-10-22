// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle;

public interface ImportsLayoutSettings {
  boolean isLayoutStaticImportsSeparately();
  void setLayoutStaticImportsSeparately(boolean value);
  default boolean isLayoutOnDemandImportFromSamePackageFirst() { return false; }
  default void setLayoutOnDemandImportFromSamePackageFirst(boolean value) {}
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
  PackageEntryTable getImportLayoutTable();
  PackageEntryTable getPackagesToUseImportOnDemand();
}