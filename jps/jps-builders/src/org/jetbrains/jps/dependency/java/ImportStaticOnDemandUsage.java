// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.impl.StringReferenceID;

public final class ImportStaticOnDemandUsage extends JvmElementUsage {

  public ImportStaticOnDemandUsage(@NotNull String importedClassName) {
    super(new StringReferenceID(importedClassName));
  }
  
  public String getImportedClassName() {
    return ((StringReferenceID)getElementOwner()).getValue();
  }
}
