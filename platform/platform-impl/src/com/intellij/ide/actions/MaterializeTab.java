/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.Nullable;

public class MaterializeTab extends AnAction implements DumbAware {
  public MaterializeTab() {
    super("MaterializeTab");
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);    //To change body of overridden methods use File | Settings | File Templates.
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    FileEditor fileEditor = PlatformDataKeys.FILE_EDITOR.getData(e.getDataContext());
    if (fileEditor == null)
      return;
    FileEditorManager.getInstance(getEventProject(e)).materializeNavigationTab(fileEditor);
  }
}
