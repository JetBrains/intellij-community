/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.execution.jshell;

import com.intellij.execution.console.ConsoleHistoryController;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author Eugene Zhuravlev
 *         Date: 09-May-17
 */
public class LaunchJShellConsoleAction extends AnAction{
  public LaunchJShellConsoleAction() {
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      return;
    }

    final VirtualFile contentFile = ConsoleHistoryController.getContentFile(
      JShellRootType.getInstance(),
      JShellRootType.CONTENT_ID,
      ScratchFileService.Option.create_new_always
    );
    assert contentFile != null;
    try {
      final FileEditor[] editors = FileEditorManager.getInstance(project).openFile(contentFile, true);
      Sdk alternateSdk = null;
      Module module = null;
      for (FileEditor editor : editors) {
        final SnippetEditorDecorator.ConfigurationPane config = SnippetEditorDecorator.getJShellConfiguration(editor);
        if (config != null) {
          alternateSdk = config.getRuntimeSdk();
          module = config.getContextModule();
          break;
        }
      }
      JShellHandler.create(project, contentFile, module, alternateSdk);
    }
    catch (Exception ex) {
      JShellDiagnostic.notifyError(ex, project);
    }
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
  }
}
