package com.intellij.application.options.colors;

import javax.swing.*;
import java.util.Map;


public interface OptionsPanel {
  void addListener(ColorAndFontSettingsListener listener);

  JPanel getPanel();

  void updateOptionsList();

  Runnable showOption(String option);

  void applyChangesToScheme();

  void selectOption(String typeToSelect);

  Map<String, String> processListOptions();
}
