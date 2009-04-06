/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.lookup;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author peter
 */
public abstract class LookupElementAction {
  private final Icon myIcon;
  private final String myText;

  protected LookupElementAction(@Nullable Icon icon, @NotNull String text) {
    myIcon = icon;
    myText = text;
  }

  @Nullable
  public Icon getIcon() {
    return myIcon;
  }

  public String getText() {
    return myText;
  }

  public abstract void performLookupAction();
}
