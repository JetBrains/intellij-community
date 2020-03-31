// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.move;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.refactoring.ui.RefactoringDialog;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class MoveDialogBase extends RefactoringDialog {

  private JCheckBox myOpenEditorCb;

  /**
   * @deprecated override {@link #getRefactoringId()} instead
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  protected String getMovePropertySuffix() {
    return getClass().getName();
  }

  /**
   * @deprecated use {@link MoveDialogBase#MoveDialogBase(Project, boolean, boolean)} instead
   */
  @Deprecated
  protected String getCbTitle() {
    return null;
  }

  /**
   * Checkbox enabled via the constructor parameters provides a better UX.
   *
   * @deprecated use {@link MoveDialogBase#MoveDialogBase(Project, boolean, boolean)} instead
   */
  @Deprecated
  protected JCheckBox initOpenInEditorCb() {
    myOpenEditorCb = new JCheckBox(getCbTitle(), PropertiesComponent.getInstance().getBoolean(getRefactoringId() + ".OpenInEditor", true));
    return myOpenEditorCb;
  }

  /**
   * There's no need so save state explicitly if the constructor parameter is used to create a checkbox.
   *
   * @deprecated use {@link MoveDialogBase#MoveDialogBase(Project, boolean, boolean)} instead
   */
  @Deprecated
  protected void saveOpenInEditorOption() {
    if (myOpenEditorCb != null) {
      PropertiesComponent.getInstance().setValue(getRefactoringId() + ".OpenInEditor", myOpenEditorCb.isSelected(), true);
    }
  }

  @Override
  public boolean isOpenInEditor() {
    return myOpenEditorCb != null && myOpenEditorCb.isSelected() || super.isOpenInEditor();
  }

  @Override
  protected @NotNull String getRefactoringId() {
    return "Move" + getMovePropertySuffix();
  }

  @SuppressWarnings("unused")
  protected MoveDialogBase(@NotNull Project project, boolean canBeParent) {
    this(project, canBeParent, false);
  }

  protected MoveDialogBase(@NotNull Project project, boolean canBeParent, boolean addOpenInEditorCheckbox) {
    super(project, canBeParent, addOpenInEditorCheckbox);
  }
}
