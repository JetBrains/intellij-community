/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor;

import com.intellij.openapi.diagnostic.Logger;

public class VisualPosition {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.VisualPosition");
  public final int line;
  public final int column;

  public VisualPosition(int line, int column) {
    //TODO: this is a temporary assert, remove it ASAP!
    LOG.assertTrue(line  >= 0, "Line number cannot be negative");
    this.line = line;
    this.column = column;
  }

  public String toString() {
    return "VisualPosition: line = " + line + " column = " + column;
  }
}
