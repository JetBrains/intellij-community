// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.util.Computable;
import org.jetbrains.annotations.ApiStatus;

/**
 * Indicates is index update provided by {@link FileBasedIndexInfrastructureExtension}
 * or calculated from {@link FileContent} by {@link FileBasedIndexExtension}
 */
@ApiStatus.Internal
public final class IndexInfrastructureExtensionUpdateComputation implements Computable<Boolean> {
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
  public Boolean compute() {
    return myComputation.compute();
  }
}
