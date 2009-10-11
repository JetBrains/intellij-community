/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.ui.popup.util;

import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.TreePopupStep;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class BaseTreePopupStep<T> extends BaseStep<T> implements TreePopupStep<T> {

  private final String myTitle;
  private final AbstractTreeStructure myStructure;
  private final Project myProject;

  public BaseTreePopupStep(Project project, String aTitle, AbstractTreeStructure aStructure) {
    myTitle = aTitle;
    myStructure = aStructure;
    myProject = project;
  }

  public AbstractTreeStructure getStructure() {
    return myStructure;
  }

  public boolean isRootVisible() {
    return false;
  }

  public Project getProject() {
    return myProject;
  }

  public String getTitle() {
    return myTitle;
  }

  public boolean isSelectable(T node, T userData) {
    return true;
  }

  public boolean hasSubstep(T selectedValue) {
    return false;
  }

  public PopupStep onChosen(T selectedValue, final boolean finalChoice) {
    return FINAL_CHOICE;
  }

  public void canceled() {
  }

  public String getTextFor(T value) {
    return value.toString();
  }

  @NotNull
  public List<T> getValues() {
    return Collections.emptyList();
  }

  public boolean isSpeedSearchEnabled() {
    return true;
  }

}
