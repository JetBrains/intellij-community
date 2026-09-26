// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.GraphDataInput;

import java.io.IOException;

/**
 * Tracks a type-import-on-demand scope: {@code import p.q.*;} (package) or {@code import p.Outer.Mid.*;} (class).
 * The scope is a namespace path, not a node reference: it is canonicalized to '/'-separated form on construction,
 * so a class scope "p/Outer$Mid" and its source spelling "p.Outer.Mid" produce the same usage.
 * Consequently '$' occurring in source-level identifiers is not supported for on-demand-import tracking.
 * (Sibling usages keying by class-node identity, e.g. {@link ClassUsage} and {@link ImportStaticMemberUsage},
 * keep binary '$'-form names.)
 */
public final class ImportPackageOnDemandUsage extends JvmElementUsage {

  public ImportPackageOnDemandUsage(@NotNull String packageOrClassName) {
    this(new JvmNodeReferenceID(packageOrClassName));
  }

  public ImportPackageOnDemandUsage(@NotNull JvmNodeReferenceID scopeId) {
    super(toOnDemandScope(scopeId));
  }

  public ImportPackageOnDemandUsage(GraphDataInput in) throws IOException {
    super(in);
  }

  @Override
  public int hashCode() {
    return super.hashCode() + 3;
  }
}
