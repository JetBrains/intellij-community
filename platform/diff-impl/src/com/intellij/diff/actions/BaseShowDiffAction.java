/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.actions;

import com.intellij.diff.DiffManager;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class BaseShowDiffAction extends AnAction implements DumbAware {
  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    boolean canShow = isAvailable(e);
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      presentation.setVisible(canShow);
    }
    else {
      presentation.setVisible(true);
      presentation.setEnabled(canShow);
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    DiffRequest request = getDiffRequest(e);
    if (request == null) return;

    DiffManager.getInstance().showDiff(project, request);
  }

  protected abstract boolean isAvailable(@NotNull AnActionEvent e);

  @Nullable
  protected abstract DiffRequest getDiffRequest(@NotNull AnActionEvent e);
}
