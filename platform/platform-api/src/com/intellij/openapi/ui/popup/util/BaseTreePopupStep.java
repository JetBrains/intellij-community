// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.popup.util;

import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.TreePopupStep;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class BaseTreePopupStep<T> extends BaseStep<T> implements TreePopupStep<T> {

  private final @NlsContexts.PopupTitle String myTitle;
  private final AbstractTreeStructure myStructure;
  private final Project myProject;

  public BaseTreePopupStep(Project project, @NlsContexts.PopupTitle String aTitle, AbstractTreeStructure aStructure) {
    myTitle = aTitle;
    myStructure = aStructure;
    myProject = project;
  }

  @Override
  public AbstractTreeStructure getStructure() {
    return myStructure;
  }

  @Override
  public boolean isRootVisible() {
    return false;
  }

  @Override
  public Project getProject() {
    return myProject;
  }

  @Override
  public String getTitle() {
    return myTitle;
  }

  @Override
  public boolean isSelectable(T node, T userData) {
    return true;
  }

  @Override
  public boolean hasSubstep(T selectedValue) {
    return false;
  }

  @Override
  public PopupStep onChosen(T selectedValue, final boolean finalChoice) {
    return FINAL_CHOICE;
  }

  @Override
  public void canceled() {
  }

  @Override
  public String getTextFor(T value) {
    return value.toString();
  }

  @Override
  @NotNull
  public List<T> getValues() {
    return Collections.emptyList();
  }

  @Override
  public boolean isSpeedSearchEnabled() {
    return true;
  }

}
