// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.GraphDataInput;

import java.io.IOException;

public final class ImportStaticOnDemandUsage extends JvmElementUsage {

  public ImportStaticOnDemandUsage(@NotNull String importedClassName) {
    this(new JvmNodeReferenceID(importedClassName));
  }

  public ImportStaticOnDemandUsage(@NotNull JvmNodeReferenceID importedClassId) {
    super(importedClassId);
  }

  public ImportStaticOnDemandUsage(GraphDataInput in) throws IOException {
    super(in);
  }

  public String getImportedClassName() {
    return getElementOwner().getNodeName();
  }

  @Override
  public int hashCode() {
    return super.hashCode() + 2;
  }
}
