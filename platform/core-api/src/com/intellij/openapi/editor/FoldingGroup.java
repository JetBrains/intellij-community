// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import org.jetbrains.annotations.NonNls;

/**
 * {@link com.intellij.openapi.editor.FoldRegion}s with same FoldingGroup instances expand and collapse together.
 *
 * @author peter
 */
public final class FoldingGroup {
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
