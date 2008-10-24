package com.intellij.application.options.colors;

import java.awt.*;

public interface PreviewPanel {

  class Empty implements PreviewPanel{
    public Component getPanel() {
      return null;
    }

    public void updateView() {
    }

    public void addListener(final ColorAndFontSettingsListener listener) {

    }
  }

  Component getPanel();

  void updateView();

  void addListener(ColorAndFontSettingsListener listener);
}
