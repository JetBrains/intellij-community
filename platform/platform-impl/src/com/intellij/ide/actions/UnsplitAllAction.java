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
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;

/**
 * @author Vladimir Kondratyev
 */
public final class UnsplitAllAction extends SplitterActionBase {
  public void actionPerformed(final AnActionEvent event) {
    final Project project = event.getData(CommonDataKeys.PROJECT);
    final FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
    //VirtualFile file = fileEditorManager.getSelectedFiles()[0];
    fileEditorManager.unsplitAllWindow();
  }

  @Override
  protected boolean isActionEnabled(Project project) {
    final FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
    return fileEditorManager.getWindowSplitCount() > 2;
  }
}
