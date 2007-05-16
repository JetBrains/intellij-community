/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet;

import org.jetbrains.annotations.NonNls;

/**
 * @author nik
 */
public final class FacetTypeId<F extends Facet> {
  private final String myDebugName;

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
