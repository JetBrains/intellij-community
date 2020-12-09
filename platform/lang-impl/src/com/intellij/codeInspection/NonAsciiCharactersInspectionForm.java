// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

class NonAsciiCharactersInspectionForm {
  private final NonAsciiCharactersInspection myInspection;
  private JBCheckBox myASCIIIdentifiers;
  private JBCheckBox myASCIIComments;
  private JBCheckBox myASCIIStringLiterals;
  private JBCheckBox myAlienIdentifiers;
  JPanel myPanel;
  private JBCheckBox myFilesContainingBOM;
  private JBCheckBox myDifferentLanguagesInStrings;
  private final Map<JCheckBox, String> myBindings = new HashMap<>();

  NonAsciiCharactersInspectionForm(@NotNull NonAsciiCharactersInspection inspection) {
    myInspection = inspection;
    bind(myASCIIIdentifiers, "CHECK_FOR_NOT_ASCII_IDENTIFIER_NAME");
    bind(myASCIIStringLiterals, "CHECK_FOR_NOT_ASCII_STRING_LITERAL");
    bind(myASCIIComments, "CHECK_FOR_NOT_ASCII_COMMENT");
    bind(myAlienIdentifiers, "CHECK_FOR_DIFFERENT_LANGUAGES_IN_IDENTIFIER_NAME");
    bind(myDifferentLanguagesInStrings, "CHECK_FOR_DIFFERENT_LANGUAGES_IN_STRING");
    bind(myFilesContainingBOM, "CHECK_FOR_FILES_CONTAINING_BOM");

    reset();
  }

  private void bind(@NotNull JCheckBox checkBox, @NotNull String property) {
    myBindings.put(checkBox, property);
    reset(checkBox, property);
    checkBox.addChangeListener(__ -> {
      boolean selected = checkBox.isSelected();
      ReflectionUtil.setField(myInspection.getClass(), myInspection, boolean.class, property, selected);
    });
  }

  private void reset(@NotNull JCheckBox checkBox, @NotNull String property) {
    checkBox.setSelected(ReflectionUtil.getField(myInspection.getClass(), myInspection, boolean.class, property));
  }

  private void reset() {
    for (Map.Entry<JCheckBox, String> entry : myBindings.entrySet()) {
      JCheckBox checkBox = entry.getKey();
      String property = entry.getValue();
      reset(checkBox, property);
    }
  }
}
