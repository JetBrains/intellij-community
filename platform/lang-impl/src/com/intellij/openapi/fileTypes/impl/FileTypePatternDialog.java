// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes.impl;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.TemplateLanguageFileType;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.psi.templateLanguages.TemplateDataLanguageMappings;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Comparator;
import java.util.List;

final class FileTypePatternDialog {
  private JTextField myPatternField;
  private ComboBox<Language> myLanguageCombo;
  private JLabel myTemplateDataLanguageButton;
  private JPanel myMainPanel;

  FileTypePatternDialog(@Nullable String initialPatterns, @NotNull FileType fileType, Language templateDataLanguage) {
    myPatternField.setText(initialPatterns);

    if (fileType instanceof TemplateLanguageFileType) {
      DefaultComboBoxModel<Language> model = (DefaultComboBoxModel<Language>)myLanguageCombo.getModel();
      model.addElement(null);
      List<Language> languages = ContainerUtil.sorted(TemplateDataLanguageMappings.getTemplateableLanguages(), Comparator.comparing(Language::getDisplayName));
      for (Language language : languages) {
        model.addElement(language);
      }
      myLanguageCombo.setSwingPopup(false);
      myLanguageCombo.setRenderer(SimpleListCellRenderer.create((label, value, index) -> {
        label.setText(value == null ? "" : value.getDisplayName());
        if (value != null) {
          FileType type = value.getAssociatedFileType();
          if (type != null) {
            label.setIcon(type.getIcon());
          }
        }
      }));
      myLanguageCombo.setSelectedItem(templateDataLanguage);
    }
    else {
      myLanguageCombo.setVisible(false);
      myTemplateDataLanguageButton.setVisible(false);
    }
  }

  @NotNull
  JTextField getPatternField() {
    return myPatternField;
  }

  @NotNull
  JPanel getMainPanel() {
    return myMainPanel;
  }

  Language getTemplateDataLanguage() {
    return (Language)myLanguageCombo.getSelectedItem();
  }
}
