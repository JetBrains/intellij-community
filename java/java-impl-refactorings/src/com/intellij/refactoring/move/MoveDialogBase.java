// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.refactoring.ui.RefactoringDialog;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class MoveDialogBase extends RefactoringDialog {

  private JCheckBox myOpenEditorCb;

  /**
   * @deprecated override {@link #getRefactoringId()} instead
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  protected String getMovePropertySuffix() {
    return getClass().getName();
  }

  /**
   * @deprecated use {@link MoveDialogBase#MoveDialogBase(Project, boolean, boolean)} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  protected @Nls String getCbTitle() {
    return null;
  }

  /**
   * Checkbox enabled via the constructor parameters provides a better UX.
   *
   * @deprecated use {@link MoveDialogBase#MoveDialogBase(Project, boolean, boolean)} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
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
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
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

  protected MoveDialogBase(@NotNull Project project, boolean canBeParent) {
    this(project, canBeParent, false);
  }

  protected MoveDialogBase(@NotNull Project project, boolean canBeParent, boolean addOpenInEditorCheckbox) {
    super(project, canBeParent, addOpenInEditorCheckbox);
  }

  @Override
  protected boolean isOpenInEditorEnabledByDefault() {
    return false;
  }
}
