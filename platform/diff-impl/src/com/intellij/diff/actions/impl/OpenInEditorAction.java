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
package com.intellij.diff.actions.impl;

import com.intellij.diff.DiffContext;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUtil;
import com.intellij.ide.actions.EditSourceAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.EmptyAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OpenInEditorAction extends EditSourceAction implements DumbAware {
  public static DataKey<OpenInEditorAction> KEY = DataKey.create("DiffOpenInEditorAction");

  @Nullable private final Runnable myAfterRunnable;

  public OpenInEditorAction(@Nullable Runnable afterRunnable) {
    EmptyAction.setupAction(this, "EditSource", null);
    myAfterRunnable = afterRunnable;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    DiffRequest request = e.getData(DiffDataKeys.DIFF_REQUEST);
    DiffContext context = e.getData(DiffDataKeys.DIFF_CONTEXT);

    if (DiffUtil.isUserDataFlagSet(DiffUserDataKeys.GO_TO_SOURCE_DISABLE, request, context)) {
      e.getPresentation().setVisible(false);
      e.getPresentation().setEnabled(false);
    }

    if (e.getProject() == null) {
      e.getPresentation().setVisible(true);
      e.getPresentation().setEnabled(false);
      return;
    }

    if (getDescriptor(e.getDataContext()) == null) {
      e.getPresentation().setVisible(true);
      e.getPresentation().setEnabled(false);
      return;
    }

    e.getPresentation().setEnabledAndVisible(true);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    assert project != null;

    OpenFileDescriptor descriptor = getDescriptor(e.getDataContext());
    assert descriptor != null;

    openEditor(project, descriptor);
  }

  public void openEditor(@NotNull Project project, @NotNull OpenFileDescriptor descriptor) {
    FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
    if (myAfterRunnable != null) myAfterRunnable.run();
  }

  @Nullable
  public static OpenFileDescriptor getDescriptor(@NotNull DataContext context) {
    return DiffDataKeys.OPEN_FILE_DESCRIPTOR.getData(context);
  }
}
