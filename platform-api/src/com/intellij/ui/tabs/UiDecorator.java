package com.intellij.ui.tabs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public interface UiDecorator {
  @NotNull
  UiDecoration getDecoration();

  class UiDecoration {
    private @Nullable Font myLabelFont;
    private @Nullable Insets myLabelInsets;

    public UiDecoration(final Font labelFont, final Insets labelInsets) {
      myLabelFont = labelFont;
      myLabelInsets = labelInsets;
    }

    @Nullable
    public Font getLabelFont() {
      return myLabelFont;
    }

    @Nullable
    public Insets getLabelInsets() {
      return myLabelInsets;
    }
  }
}
