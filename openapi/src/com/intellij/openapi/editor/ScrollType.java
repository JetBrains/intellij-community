/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor;

public final class ScrollType {
  public static final ScrollType RELATIVE = new ScrollType("RELATIVE");
  public static final ScrollType CENTER = new ScrollType("CENTER");
  public static final ScrollType MAKE_VISIBLE = new ScrollType("MAKE_VISIBLE");
  public static final ScrollType CENTER_UP = new ScrollType("CENTER_UP");
  public static final ScrollType CENTER_DOWN = new ScrollType("CENTER_DOWN");

  private final String myDebugName;

  private ScrollType(String debugName) {
    myDebugName = debugName;
  }

  public String toString() {
    return myDebugName;
  }
}
