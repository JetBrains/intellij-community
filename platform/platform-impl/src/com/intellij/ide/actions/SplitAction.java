/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author Vladimir Kondratyev
 * @author Konstantin Bulenkov
 */
public abstract class SplitAction extends AnAction implements DumbAware {
  private final int myOrientation;
  private final boolean myCloseSource;

  protected SplitAction(final int orientation) {
    this(orientation, false);
  }

  protected SplitAction(final int orientation, boolean closeSource) {
    myOrientation = orientation;
    myCloseSource = closeSource;
  }

  public void actionPerformed(final AnActionEvent event) {
    final Project project = event.getData(CommonDataKeys.PROJECT);
    final FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
    final EditorWindow window = event.getData(EditorWindow.DATA_KEY);
    final VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);

    fileEditorManager.createSplitter(myOrientation, window);
    
    if (myCloseSource && window != null && file != null) {
      window.closeFile(file, false, false);
    }
  }

  public void update(final AnActionEvent event) {
    final Project project = event.getData(CommonDataKeys.PROJECT);
    final EditorWindow window = event.getData(EditorWindow.DATA_KEY);
    final int minimum = myCloseSource ? 2 : 1;
    final boolean enabled = project != null
                            && window != null
                            && window.getTabCount() >= minimum
                            && !window.getOwner().isPreview();
    event.getPresentation().setEnabledAndVisible(enabled);
  }
}
