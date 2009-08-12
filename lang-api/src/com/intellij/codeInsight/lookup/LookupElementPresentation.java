/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.lookup;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author peter
 */
public abstract class LookupElementPresentation {
  public abstract void setIcon(@Nullable Icon icon);

  public final void setItemText(@Nullable String text) {
    setItemText(text, false, false);
  }

  public abstract void setItemText(@Nullable String text, boolean strikeout, final boolean bold);

  public final void setTailText(@Nullable String text) {
    setTailText(text, false, false, false);
  }

  public abstract void setTailText(@Nullable String text, boolean grayed, boolean bold, boolean strikeout);

  public abstract void setTailText(@Nullable String text, @Nullable Color foreground, boolean bold, boolean strikeout);

  public final void setTypeText(@Nullable String text) {
    setTypeText(text, null);
  }

  public abstract void setTypeText(@Nullable String text, @Nullable Icon icon);

  /**
   * @return whether the presentation is requested to actually render lookup element on screen, or just to estimate its width.
   * In the second, 'non-real' case, some heavy operations (e.g. getIcon()) can be omitted (only icon width is important)
   */
  public abstract boolean isReal();
}
