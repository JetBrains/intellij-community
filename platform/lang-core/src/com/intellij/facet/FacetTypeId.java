// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.facet;

import org.jetbrains.annotations.NonNls;

public final class FacetTypeId<F extends Facet> {
  private final String myDebugName;

  /**
   * @deprecated use {@link FacetTypeId#FacetTypeId(String)} instead
   */
  @Deprecated(forRemoval = true)
  public FacetTypeId() {
    this("unknown");
  }

  public FacetTypeId(@NonNls String debugName) {
    myDebugName = debugName;
  }

  public String toString() {
    return myDebugName;
  }
}
