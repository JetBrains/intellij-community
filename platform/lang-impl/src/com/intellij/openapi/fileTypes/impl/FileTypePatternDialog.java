/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.fileTypes.impl;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.TemplateLanguageFileType;
import com.intellij.psi.templateLanguages.TemplateDataLanguageMappings;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author peter
 */
public class FileTypePatternDialog {
  private JTextField myPatternField;
  private JComboBox myLanguageCombo;
  private JLabel myTemplateDataLanguageButton;
  private JPanel myMainPanel;

  public FileTypePatternDialog(@Nullable String initialPatterns, FileType fileType, Language templateDataLanguage) {
    myPatternField.setText(initialPatterns);

    if (fileType instanceof TemplateLanguageFileType) {
      final DefaultComboBoxModel model = (DefaultComboBoxModel) myLanguageCombo.getModel();
      model.addElement(null);
      final List<Language> languages = TemplateDataLanguageMappings.getTemplateableLanguages();
      Collections.sort(languages, new Comparator<Language>() {
        public int compare(final Language o1, final Language o2) {
          return o1.getID().compareTo(o2.getID());
        }
      });
      for (Language language : languages) {
        model.addElement(language);
      }
      myLanguageCombo.setRenderer(new DefaultListCellRenderer() {
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
          super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
          setText(value == null ? "" : ((Language) value).getDisplayName());
          if (value != null) {
            final FileType type = ((Language)value).getAssociatedFileType();
            if (type != null) {
              setIcon(type.getIcon());
            }
          }
          return this;
        }
      });
      myLanguageCombo.setSelectedItem(templateDataLanguage);
    } else {
      myLanguageCombo.setVisible(false);
      myTemplateDataLanguageButton.setVisible(false);
    }
  }

  public JTextField getPatternField() {
    return myPatternField;
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }

  public Language getTemplateDataLanguage() {
    return (Language)myLanguageCombo.getSelectedItem();
  }
}
