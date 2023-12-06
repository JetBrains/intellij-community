/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.intellij.tools;

import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.actionSystem.CommonDataKeys.*;
import static com.intellij.openapi.actionSystem.PlatformCoreDataKeys.MODULE;
import static com.intellij.openapi.actionSystem.PlatformCoreDataKeys.PROJECT_FILE_DIRECTORY;

/**
 * @author Eugene Belyaev
 */
public class ToolAction extends AnAction implements DumbAware {
  private final String myActionId;

  public ToolAction(@NotNull Tool tool) {
    super(ToolsBundle.message("action.text.external.tool"));
    myActionId = tool.getActionId();
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    runTool(myActionId, e.getDataContext(), e, 0L, null);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Tool tool = findTool(myActionId);
    if (tool == null) {
      presentation.setEnabledAndVisible(false);
      return;
    }
    presentation.setEnabledAndVisible(true);
    presentation.setText(ToolRunProfile.expandMacrosInName(tool, e.getDataContext()), false);
    presentation.setDescription(tool.getDescription());
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Nullable
  private static Tool findTool(@NotNull String actionId) {
    for (Tool tool : ToolsProvider.getAllTools()) {
      if (actionId.equals(tool.getActionId())) {
        return tool;
      }
    }
    return null;
  }

  static void runTool(@NotNull String actionId, @NotNull DataContext context) {
    runTool(actionId, context, null, 0L, null);
  }

  static void runTool(@NotNull String actionId,
                      @NotNull DataContext context,
                      @Nullable AnActionEvent e,
                      long executionId,
                      @Nullable ProcessListener processListener) {
    Tool tool = findTool(actionId);
    if (tool != null) {
      tool.execute(e, getToolDataContext(context), executionId, processListener);
    }
    else {
      Tool.notifyCouldNotStart(processListener);
    }
  }

  @NotNull
  public static DataContext getToolDataContext(@NotNull DataContext dataContext) {
    if (dataContext instanceof SimpleDataContext) return dataContext;

    SimpleDataContext.Builder builder = SimpleDataContext.builder()
      .addAll(dataContext, PROJECT, PROJECT_FILE_DIRECTORY, EDITOR, VIRTUAL_FILE, MODULE, PSI_FILE);
    VirtualFile virtualFile = dataContext.getData(VIRTUAL_FILE);
    if (virtualFile == null) {
      Project project = dataContext.getData(PROJECT);
      FileEditor editor = project == null ? null : FileEditorManager.getInstance(project).getSelectedEditor();
      VirtualFile editorFile = editor == null ? null : editor.getFile();
      builder.add(VIRTUAL_FILE, editorFile);
    }
    return builder.build();
  }
}