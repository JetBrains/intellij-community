// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.InputRedirectAware;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public final class ProgramInputRedirectPanel extends JPanel implements PanelWithAnchor {
  private final JBCheckBox myCheckBox = new JBCheckBox(ExecutionBundle.message("redirect.input.from"));

  private final TextFieldWithBrowseButton myInputFile = new TextFieldWithBrowseButton();

  public ProgramInputRedirectPanel() {
    super(new BorderLayout(UIUtil.DEFAULT_HGAP, 2));
    var descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor();
    myInputFile.addBrowseFolderListener(null, descriptor, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
    add(myInputFile, BorderLayout.CENTER);
    myInputFile.setEnabled(false);
    add(myCheckBox, BorderLayout.WEST);
    setAnchor(myCheckBox);
    myCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myInputFile.setEnabled(myCheckBox.isSelected());
      }
    });
  }

  public @NotNull TextFieldWithBrowseButton getComponent() {
    return myInputFile;
  }

  @Override
  public JComponent getAnchor() {
    return myCheckBox.getAnchor();
  }

  @Override
  public void setAnchor(@Nullable JComponent anchor) {
    myCheckBox.setAnchor(anchor);
  }

  public void applyTo(@NotNull InputRedirectAware.InputRedirectOptions configuration) {
    configuration.setRedirectInput(myCheckBox.isSelected());
    final String filePath = myInputFile.getText();
    configuration.setRedirectInputPath(StringUtil.isEmpty(filePath) ? null : FileUtil.toSystemIndependentName(filePath));
  }

  public void reset(@Nullable InputRedirectAware.InputRedirectOptions configuration) {
    final boolean isRedirectInput = configuration != null && configuration.isRedirectInput();
    myCheckBox.setSelected(isRedirectInput);
    myInputFile.setEnabled(isRedirectInput);
    myInputFile.setText(configuration != null
                        ? FileUtil.toSystemDependentName(StringUtil.notNullize(configuration.getRedirectInputPath())) : "");
  }
}
