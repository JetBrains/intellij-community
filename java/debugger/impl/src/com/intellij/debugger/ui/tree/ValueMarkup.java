/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.ui.tree;

import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 27, 2007
 */
public class ValueMarkup {
  private final String myText;
  private final Color myColor;

  public ValueMarkup(final String text, final Color color) {
    myText = "[" + text + "] ";
    myColor = color;
  }

  @NotNull
  public String getText() {
    return myText;
  }

  public Color getColor() {
    return myColor;
  }
}
