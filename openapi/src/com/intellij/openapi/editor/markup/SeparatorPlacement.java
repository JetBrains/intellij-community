/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor.markup;

public final class SeparatorPlacement {
  public static final SeparatorPlacement TOP = new SeparatorPlacement("TOP");
  public static final SeparatorPlacement BOTTOM = new SeparatorPlacement("BOTTOM");
  private String myDebugName;

  private SeparatorPlacement(String debugName) {
    myDebugName = debugName;
  }

  public String toString() {
    return myDebugName;
  }
}
