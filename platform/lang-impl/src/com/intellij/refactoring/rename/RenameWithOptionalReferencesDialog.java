// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.ui.NonFocusableCheckBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;


public abstract class RenameWithOptionalReferencesDialog extends RenameDialog {
  private JCheckBox myCbSearchForReferences;

  public RenameWithOptionalReferencesDialog(@NotNull Project project,
                                            @NotNull PsiElement psiElement,
                                            @Nullable PsiElement nameSuggestionContext,
                                            Editor editor) {
    super(project, psiElement, nameSuggestionContext, editor);
  }

  @Override
  protected void createCheckboxes(JPanel panel, GridBagConstraints gbConstraints) {
    gbConstraints.insets = new Insets(0, 0, 4, 0);
    gbConstraints.gridwidth = 1;
    gbConstraints.gridx = 0;
    gbConstraints.weighty = 0;
    gbConstraints.weightx = 1;
    gbConstraints.fill = GridBagConstraints.BOTH;
    myCbSearchForReferences = new NonFocusableCheckBox(RefactoringBundle.message("search.for.references"));
    myCbSearchForReferences.setSelected(getSearchForReferences());
    panel.add(myCbSearchForReferences, gbConstraints);

    super.createCheckboxes(panel, gbConstraints);
  }

  @Override
  protected void doAction() {
    setSearchForReferences(myCbSearchForReferences.isSelected());
    super.doAction();
  }

  protected abstract boolean getSearchForReferences();

  protected abstract void setSearchForReferences(boolean value);
}
