// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.tools;

import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
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
    String toolName = tool.getName();
    String text = StringUtil.isNotEmpty(toolName) ? toolName :
                  ToolsBundle.message("action.text.external.tool");
    getTemplatePresentation().setText(text, false);
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

  private static @Nullable Tool findTool(@NotNull String actionId) {
    for (Tool tool : ToolsProvider.getAllTools()) {
      if (actionId.equals(tool.getActionId())) {
        return tool;
      }
    }
    return null;
  }

  public static void runTool(@NotNull String actionId, @NotNull DataContext context) {
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

  public static @NotNull DataContext getToolDataContext(@NotNull DataContext dataContext) {
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