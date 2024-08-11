// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.util.Computable;
import org.jetbrains.annotations.ApiStatus;

/**
 * Indicates is index update provided by {@link FileBasedIndexInfrastructureExtension}
 * or calculated from {@link FileContent} by {@link FileBasedIndexExtension}
 */
@ApiStatus.Internal
public final class IndexInfrastructureExtensionUpdateComputation implements StorageUpdate {
  private final Computable<Boolean> myComputation;
  private final boolean myIndexProvided;

  public IndexInfrastructureExtensionUpdateComputation(Computable<Boolean> computation, boolean isIndexProvided) {
    this.myComputation = computation;
    this.myIndexProvided = isIndexProvided;
  }

  boolean isIndexProvided() {
    return myIndexProvided;
  }

  @Override
  public boolean update() {
    return myComputation.compute();
  }
}
