// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.jshell;

import com.intellij.execution.console.ConsoleHistoryController;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public class LaunchJShellConsoleAction extends AnAction{
  public LaunchJShellConsoleAction() {
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
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
    final FileEditor[] editors = FileEditorManager.getInstance(project).openFile(contentFile, true);

    ApplicationManager.getApplication().invokeLater(() -> {
      try {
        Sdk alternateSdk = null;
        Module module = null;
        for (FileEditor editor : editors) {
          final SnippetEditorDecorator.ConfigurationPane config = SnippetEditorDecorator.ConfigurationPane.getJShellConfiguration(editor);
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
    }, ModalityState.any(), project.getDisposed());
  }
}
