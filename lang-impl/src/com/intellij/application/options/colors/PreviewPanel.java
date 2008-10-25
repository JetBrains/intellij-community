package com.intellij.application.options.colors;

import java.awt.*;

public interface PreviewPanel {
  void blinkSelectedHighlightType(Object selected);

  void disposeUIResources();

  class Empty implements PreviewPanel{
    public Component getPanel() {
      return null;
    }

    public void updateView() {
    }

    public void addListener(final ColorAndFontSettingsListener listener) {

    }

    public void blinkSelectedHighlightType(final Object selected) {

    }

    public void disposeUIResources() {

    }
  }

  Component getPanel();

  void updateView();

  void addListener(ColorAndFontSettingsListener listener);
}
