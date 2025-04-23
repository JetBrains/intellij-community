// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options;

import com.intellij.java.frontback.impl.JavaFrontbackBundle;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static com.intellij.psi.codeStyle.JavaCodeStyleSettings.*;

public class FullyQualifiedNamesInJavadocOptionProvider {

  private JPanel myPanel;
  private ComboBox<QualifyJavadocOptions> myComboBox;

  public FullyQualifiedNamesInJavadocOptionProvider() {
    composePanel();
  }

  public void reset(@NotNull CodeStyleSettings settings) {
    JavaCodeStyleSettings javaSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);
    QualifyJavadocOptions option = QualifyJavadocOptions.fromIntValue(javaSettings.CLASS_NAMES_IN_JAVADOC);
    myComboBox.setSelectedItem(option);
  }

  public void apply(@NotNull CodeStyleSettings settings) {
    JavaCodeStyleSettings javaSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);
    javaSettings.CLASS_NAMES_IN_JAVADOC = getSelectedIntOptionValue();
  }

  public boolean isModified(CodeStyleSettings settings) {
    JavaCodeStyleSettings javaSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);
    return javaSettings.CLASS_NAMES_IN_JAVADOC != getSelectedIntOptionValue();
  }

  private int getSelectedIntOptionValue() {
    QualifyJavadocOptions item = (QualifyJavadocOptions)myComboBox.getSelectedItem();
    return item.getIntOptionValue();
  }

  public @NotNull JPanel getPanel() {
    return myPanel;
  }

  private void composePanel() {
    myPanel = new JPanel(new GridBagLayout());

    myComboBox = new ComboBox<>();
    for (QualifyJavadocOptions options : QualifyJavadocOptions.values()) {
      myComboBox.addItem(options);
    }
    myComboBox.setRenderer(SimpleListCellRenderer.create("", QualifyJavadocOptions::getPresentableText));

    JLabel title = new JLabel(JavaFrontbackBundle.message("radio.use.fully.qualified.class.names.in.javadoc"));
    myPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));

    GridBagConstraints left = new GridBagConstraints();
    left.anchor = GridBagConstraints.WEST;

    GridBagConstraints right = new GridBagConstraints();
    right.anchor = GridBagConstraints.WEST;
    right.weightx = 1.0;
    right.insets = JBUI.insetsLeft(5);

    myPanel.add(title, left);
    myPanel.add(myComboBox, right);
  }
}

enum QualifyJavadocOptions {

  FQ_ALWAYS(FULLY_QUALIFY_NAMES_ALWAYS, JavaFrontbackBundle.message("radio.use.fully.qualified.class.names.in.javadoc.always")),
  SHORTEN_ALWAYS(SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT, JavaFrontbackBundle.message("radio.use.fully.qualified.class.names.in.javadoc.never")),
  FQ_WHEN_NOT_IMPORTED(FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED, JavaFrontbackBundle.message("radio.use.fully.qualified.class.names.in.javadoc.if.not.imported"));

  private final String myText;
  private final int myOption;

  public String getPresentableText() {
    return myText;
  }

  public int getIntOptionValue() {
    return myOption;
  }

  public static QualifyJavadocOptions fromIntValue(int value) {
    for (QualifyJavadocOptions option : values()) {
      if (option.myOption == value) return option;
    }
    return FQ_WHEN_NOT_IMPORTED;
  }

  QualifyJavadocOptions(int option, String text) {
    myOption = option;
    myText = text;
  }
}
