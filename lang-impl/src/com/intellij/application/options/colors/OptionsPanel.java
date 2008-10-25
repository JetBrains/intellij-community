package com.intellij.application.options.colors;

import com.intellij.openapi.options.SearchableConfigurable;

import javax.swing.*;


public interface OptionsPanel {
  void addListener(ColorAndFontSettingsListener listener);

  JPanel getPanel();

  void updateOptionsList();

  Runnable showOption(String path, SearchableConfigurable configurable, String option, boolean highlight);

  void applyChangesToScheme();

  void selectOption(String typeToSelect);
}
