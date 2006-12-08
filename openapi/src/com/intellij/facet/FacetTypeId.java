/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

/**
 * @author nik
 */
public final class FacetTypeId<F extends Facet> {
  private @NotNull @NonNls String myId;


  public FacetTypeId(@NotNull @NonNls final String id) {
    myId = id;
  }

  @NotNull
  public String getId() {
    return myId;
  }


  public String toString() {
    return myId;
  }
}
