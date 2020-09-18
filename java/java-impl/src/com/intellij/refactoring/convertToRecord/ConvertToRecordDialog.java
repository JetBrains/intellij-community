// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.convertToRecord;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.convertToRecord.ConvertToRecordHandler.PsiClassDefinition;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

class ConvertToRecordDialog extends RefactoringDialog {
  private final PsiClassDefinition myPsiClassDef;

  private JCheckBox mySearchWeakenVisibilityCheckBox;

  ConvertToRecordDialog(@NotNull PsiClassDefinition psiClassDef) {
    super(psiClassDef.getProject(), false);
    myPsiClassDef = psiClassDef;

    setTitle(JavaRefactoringBundle.message("convert.to.record.title"));
    init();
  }

  @Override
  protected void doAction() {
    invokeRefactoring(new ConvertToRecordProcessor(myPsiClassDef, mySearchWeakenVisibilityCheckBox.isSelected()));
  }

  @Override
  protected boolean hasPreviewButton() {
    return false;
  }

  @Override
  protected @NonNls @Nullable String getHelpId() {
    return HelpID.CONVERT_TO_RECORD;
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    mySearchWeakenVisibilityCheckBox = new JCheckBox();
    mySearchWeakenVisibilityCheckBox.setSelected(true);
    mySearchWeakenVisibilityCheckBox.setText(JavaRefactoringBundle.message("convert.to.record.search.weakened.visibility"));

    JPanel panel = new JPanel(new BorderLayout(UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP));
    panel.add(mySearchWeakenVisibilityCheckBox, BorderLayout.WEST);
    return panel;
  }
}
