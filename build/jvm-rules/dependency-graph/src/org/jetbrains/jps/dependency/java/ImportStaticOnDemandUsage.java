// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.GraphDataInput;

import java.io.IOException;

/**
 * Tracks a static-import-on-demand scope: {@code import static p.Outer.*;}.
 * The scope is a namespace path, not a node reference: it is canonicalized to '/'-separated form on construction
 * (see {@link JvmElementUsage#toOnDemandScope}), so nested scopes match regardless of the '$'/'.'-spelling of
 * the producer. '$' occurring in source-level identifiers is not supported for on-demand-import tracking.
 */
public final class ImportStaticOnDemandUsage extends JvmElementUsage {

  public ImportStaticOnDemandUsage(@NotNull String importedClassName) {
    this(new JvmNodeReferenceID(importedClassName));
  }

  public ImportStaticOnDemandUsage(@NotNull JvmNodeReferenceID importedClassId) {
    super(toOnDemandScope(importedClassId));
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
