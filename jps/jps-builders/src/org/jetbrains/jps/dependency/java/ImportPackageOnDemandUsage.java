// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.GraphDataInput;

import java.io.IOException;

public final class ImportPackageOnDemandUsage extends JvmElementUsage {

  public ImportPackageOnDemandUsage(@NotNull String packageName) {
    this(new JvmNodeReferenceID(packageName));
  }

  public ImportPackageOnDemandUsage(@NotNull JvmNodeReferenceID packageId) {
    super(packageId);
  }

  public ImportPackageOnDemandUsage(GraphDataInput in) throws IOException {
    super(in);
  }

  @Override
  public int hashCode() {
    return super.hashCode() + 3;
  }
}
