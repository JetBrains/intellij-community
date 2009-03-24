package com.intellij.application.options.colors;

/**
 * @author yole
 */
public interface ColorAndFontPanelFactory {
  NewColorAndFontPanel createPanel(ColorAndFontOptions options);
  String getPanelDisplayName();
}
