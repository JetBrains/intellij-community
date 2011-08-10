/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.ui.components.JBList;
import com.intellij.ui.speedSearch.NameFilteringListModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
* User: anna
*/
public abstract class ListBackgroundUpdaterTask extends BackgroundUpdaterTask {
  private JBList myList;

  public ListBackgroundUpdaterTask(@Nullable final Project project,
                                   @NotNull final String title,
                                   final boolean canBeCancelled,
                                   @Nullable final PerformInBackgroundOption backgroundOption) {
    super(project, title, canBeCancelled, backgroundOption);
  }

  public ListBackgroundUpdaterTask(@Nullable final Project project, @NotNull final String title, final boolean canBeCancelled) {
    super(project, title, canBeCancelled);
  }

  public ListBackgroundUpdaterTask(@Nullable final Project project, @NotNull final String title) {
    super(project, title);
  }

  public void setList(JBList list) {
    myList = list;
  }

  @Override
  protected void paintBusy(final boolean paintBusy) {
    myList.setPaintBusy(paintBusy);
  }

  @Override
  protected void replaceModel(ArrayList<PsiElement> data) {
    ((NameFilteringListModel)myList.getModel()).replaceAll(data);
  }
}
