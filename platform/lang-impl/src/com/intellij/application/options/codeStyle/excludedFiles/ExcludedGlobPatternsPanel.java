// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.excludedFiles;

import com.intellij.CodeStyleBundle;
import com.intellij.formatting.fileSet.FileSetDescriptor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.ui.components.fields.ExpandableTextField;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ExcludedGlobPatternsPanel extends JPanel {

  private final static String PATTERN_SEPARATOR = ";";

  private final ExpandableTextField myPatternsField;

  public ExcludedGlobPatternsPanel() {
    setLayout(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.weightx = 0;
    c.gridx = 0;
    c.gridy = 0;
    c.anchor = GridBagConstraints.LINE_START;
    c.insets = JBUI.insetsRight(5);
    add(new JLabel(CodeStyleBundle.message("excluded.files.glob.patterns.label")), c);
    c.weightx = 1;
    c.gridx = 1;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.insets = JBUI.emptyInsets();
    myPatternsField = new ExpandableTextField(s -> toStringList(s), strings -> StringUtil.join(strings, PATTERN_SEPARATOR));
    add(myPatternsField, c);
    c.gridy = 1;
    c.insets = JBUI.insetsLeft(5);
    JLabel hintLabel = new JLabel(CodeStyleBundle.message("excluded.files.glob.patterns.hint"));
    hintLabel.setFont(JBUI.Fonts.smallFont());
    hintLabel.setForeground(UIUtil.getContextHelpForeground());
    add(hintLabel, c);
  }


  public void apply(@NotNull CodeStyleSettings settings) {
    settings.getExcludedFiles().setDescriptors(GlobPatternDescriptor.TYPE, getDescriptors());
  }

  public void reset(@NotNull CodeStyleSettings settings) {
    myPatternsField.setText(getPatternsText(settings));
  }

  public boolean isModified(@NotNull CodeStyleSettings settings) {
    return !settings.getExcludedFiles().getDescriptors(GlobPatternDescriptor.TYPE).equals(getDescriptors());
  }

  private static String getPatternsText(@NotNull CodeStyleSettings settings) {
    List<String> patterns =
      ContainerUtil.map(settings.getExcludedFiles().getDescriptors(GlobPatternDescriptor.TYPE), d -> d.getPattern());
    return StringUtil.join(patterns, PATTERN_SEPARATOR);
  }

  private List<FileSetDescriptor> getDescriptors() {
    String patternsText = myPatternsField.getText();
    return toStringList(patternsText).stream()
                                     .sorted()
                                     .map(s -> new GlobPatternDescriptor(s)).collect(Collectors.toList());
  }

  private static List<String> toStringList(@NotNull String patternsText) {
    return StringUtil.isEmpty(patternsText)
           ? Collections.emptyList()
           : Arrays.stream(patternsText.split(PATTERN_SEPARATOR))
                   .map(s -> s.trim())
                   .filter(s -> !s.isEmpty()).collect(Collectors.toList());
  }

}
