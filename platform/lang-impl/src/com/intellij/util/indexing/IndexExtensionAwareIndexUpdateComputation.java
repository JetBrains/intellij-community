// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.util.Computable;

public final class IndexExtensionAwareIndexUpdateComputation implements Computable<Boolean> {
  private final Computable<Boolean> myComputation;
  public final boolean indexWasProvidedByExtension;

  public IndexExtensionAwareIndexUpdateComputation(Computable<Boolean> computation,
                                                   boolean indexWasProvidedByExtension) {
    this.myComputation = computation;
    this.indexWasProvidedByExtension = indexWasProvidedByExtension;
  }

  @Override
  public Boolean compute() {
    return myComputation.compute();
  }
}
