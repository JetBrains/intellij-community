// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.pom.java;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

class LegalNoticeDialog extends DialogWrapper {
  static final String EXPERIMENTAL_FEATURE_ALERT = "Experimental Feature Alert";
  private final LanguageLevel myLanguageLevel;

  public LegalNoticeDialog(Component parentComponent, LanguageLevel languageLevel) {
    super(parentComponent, false);
    myLanguageLevel = languageLevel;
    setTitle(EXPERIMENTAL_FEATURE_ALERT);
    init();
    setOKButtonText("Accept");
    setCancelButtonText("Decline");
    pack();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    JPanel iconPanel = new JPanel(new BorderLayout());
    iconPanel.add(new JBLabel(AllIcons.General.WarningDialog), BorderLayout.NORTH);
    iconPanel.setBorder(JBUI.Borders.emptyRight(2));
    panel.add(iconPanel, BorderLayout.WEST);
    JEditorPane message = new JEditorPane();
    message.setEditorKit(UIUtil.getHTMLEditorKit());
    message.setEditable(false);
    message.setBackground(UIUtil.getOptionPaneBackground());
    message.setPreferredSize(JBUI.size(500, 100));
    message.setText(UIUtil.toHtml(getLegalNotice(myLanguageLevel)));
    panel.add(message, BorderLayout.CENTER);
    return panel;
  }

  static String getLegalNotice(LanguageLevel languageLevel) {
    return
      "You must accept the terms of legal notice of the beta Java specification to enable support for \"" +
      StringUtil.substringAfter(languageLevel.getPresentableText(), " - ") +
      "\".<br/><br/>" +
      "<b>The implementation of an early-draft specification developed under the Java Community Process (JCP) " +
      "is made available for testing and evaluation purposes only and is not compatible with any specification of the JCP.</b>";
  }

  @Override
  protected void doOKAction() {
    AcceptedLanguageLevelsSettings.acceptLanguageLevel(myLanguageLevel);
    super.doOKAction();
  }
}