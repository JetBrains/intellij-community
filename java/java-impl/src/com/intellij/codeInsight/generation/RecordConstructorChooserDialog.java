// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.psi.PsiClass;
import com.intellij.ui.components.JBRadioButton;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

final class RecordConstructorChooserDialog extends DialogWrapper {
  private final @NotNull JBRadioButton myCompact;
  private final @NotNull JBRadioButton myCanonical;
  private final @NotNull JBRadioButton myCustom;
  private final @NotNull PsiClass myRecordClass;

  RecordConstructorChooserDialog(@NotNull PsiClass recordClass) {
    super(recordClass.getProject());
    myRecordClass = recordClass;
    setTitle(JavaBundle.message("generate.record.constructor.title"));
    setOKButtonText(JavaBundle.message("generate.button.title"));
    myCompact = new JBRadioButton(JavaErrorBundle.message("record.compact.constructor"), true);
    myCanonical = new JBRadioButton(JavaErrorBundle.message("record.canonical.constructor"), false);
    myCustom = new JBRadioButton(ApplicationBundle.message("custom.option"), false);
    init();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myCompact;
  }
  
  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new VerticalFlowLayout());
    ButtonGroup group = new ButtonGroup();
    group.add(myCompact);
    group.add(myCanonical);
    group.add(myCustom);
    panel.add(myCompact);
    panel.add(myCanonical);
    panel.add(myCustom);
    return panel;
  }
  
  ClassMember getClassMember() {
    if (myCompact.isSelected()) {
      return new RecordConstructorMember(myRecordClass, true);
    }
    if (myCanonical.isSelected()) {
      return new RecordConstructorMember(myRecordClass, false);
    }
    return null;
  }
}
