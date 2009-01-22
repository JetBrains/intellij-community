/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor;

import org.jetbrains.annotations.NonNls;

/**
 * {@link com.intellij.openapi.editor.FoldRegion}s with same FoldingGroup instances expand and collapse together.
 *
 * @author peter
 */
public class FoldingGroup {
  @NonNls private final String myDebugName;

  private FoldingGroup(@NonNls String debugName) {
    myDebugName = debugName;
  }

  public static FoldingGroup newGroup(@NonNls String debugName) {
    return new FoldingGroup(debugName);
  }

  @Override
  public String toString() {
    return myDebugName;
  }
}
