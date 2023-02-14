/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.debugger.memory.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.memory.ui.TypeInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ClassesActionBase extends AnAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(isEnabled(e));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    perform(e);
  }

  protected boolean isEnabled(AnActionEvent e) {
    final Project project = e.getProject();
    return project != null && !project.isDisposed();
  }

  protected abstract void perform(AnActionEvent e);

  @Nullable
  protected TypeInfo getSelectedClass(AnActionEvent e) {
    return DebuggerActionUtil.getSelectedTypeInfo(e);
  }
}
