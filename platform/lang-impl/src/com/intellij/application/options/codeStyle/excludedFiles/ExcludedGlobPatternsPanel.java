// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.excludedFiles;

import com.intellij.CodeStyleBundle;
import com.intellij.formatting.fileSet.FileSetDescriptor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.ui.components.BrowserLink;
import com.intellij.ui.components.fields.ExpandableTextField;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class ExcludedGlobPatternsPanel extends ExcludedFilesPanelBase {

  private static final String PATTERN_SEPARATOR = ";";

  private final ExpandableTextField myPatternsField;
  private final JComponent          myConversionMessage;

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
    c.insets = JBInsets.emptyInsets();
    myPatternsField = new ExpandableTextField(s -> toStringList(s), strings -> StringUtil.join(strings, PATTERN_SEPARATOR));
    add(myPatternsField, c);
    c.gridy = 1;
    add(createLinkComponent(), c);
    c.gridy ++;
    c.gridx = 1;
    c.insets = JBUI.insetsTop(5);
    myConversionMessage = createWarningMessage(CodeStyleBundle.message("excluded.files.migration.message"));
    add(myConversionMessage, c);
    myConversionMessage.setVisible(false);
  }

  private static JComponent createLinkComponent() {
    JPanel linkPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    String message = CodeStyleBundle.message("excluded.files.glob.patterns.hint");
    String textPart = message.replaceFirst("<a>.*</a>", "").trim();
    String linkPart = message.replaceFirst("^.*<a>","").replaceFirst("</a>.*$","");
    JLabel hintLabel = new JLabel(textPart);
    hintLabel.setFont(JBUI.Fonts.smallFont());
    hintLabel.setForeground(UIUtil.getContextHelpForeground());
    linkPanel.add(hintLabel);
    BrowserLink link = new BrowserLink(linkPart, "https://en.wikipedia.org/wiki/Glob_(programming)");
    link.setFont(JBUI.Fonts.smallFont());
    link.setIconTextGap(0);
    link.setHorizontalTextPosition(SwingConstants.LEFT);
    linkPanel.add(link);
    return linkPanel;
  }

  public void apply(@NotNull CodeStyleSettings settings) {
    settings.getExcludedFiles().setDescriptors(GlobPatternDescriptor.TYPE, getDescriptors());
  }

  public void reset(@NotNull CodeStyleSettings settings) {
    myPatternsField.setText(getPatternsText(settings));
  }

  public boolean isModified(@NotNull CodeStyleSettings settings) {
    boolean modified = !settings.getExcludedFiles().getDescriptors(GlobPatternDescriptor.TYPE).equals(getDescriptors());
    if (!modified) myConversionMessage.setVisible(false);
    return modified;
  }

  private String getPatternsText(@NotNull CodeStyleSettings settings) {
    List<String> patterns =
      new ArrayList<>(ContainerUtil.map(settings.getExcludedFiles().getDescriptors(GlobPatternDescriptor.TYPE), d -> d.getPattern()));
    List<String> convertedPatterns = getConvertedPatterns(settings);
    myConversionMessage.setVisible(convertedPatterns.size() > 0);
    patterns.addAll(convertedPatterns);
    return StringUtil.join(patterns, PATTERN_SEPARATOR);
  }

  private static @NotNull List<String> getConvertedPatterns(@NotNull CodeStyleSettings settings) {
    return settings.getExcludedFiles().getDescriptors(NamedScopeDescriptor.NAMED_SCOPE_TYPE).stream()
                   .map(descriptor -> NamedScopeToGlobConverter.convert((NamedScopeDescriptor)descriptor))
                   .filter(descriptor -> descriptor != null)
                   .map(descriptor -> descriptor.getPattern())
                   .collect(Collectors.toList());
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
