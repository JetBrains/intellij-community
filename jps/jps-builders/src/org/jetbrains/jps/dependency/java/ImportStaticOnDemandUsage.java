// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;

public final class ImportStaticOnDemandUsage extends JvmElementUsage {

  public ImportStaticOnDemandUsage(@NotNull String importedClassName) {
    this(new JvmNodeReferenceID(importedClassName));
  }

  public ImportStaticOnDemandUsage(@NotNull JvmNodeReferenceID importedClassId) {
    super(importedClassId);
  }

  public String getImportedClassName() {
    return ((JvmNodeReferenceID)getElementOwner()).getNodeName();
  }
}
