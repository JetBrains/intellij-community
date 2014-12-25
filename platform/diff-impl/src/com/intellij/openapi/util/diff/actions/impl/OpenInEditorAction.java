package com.intellij.openapi.util.diff.actions.impl;

import com.intellij.ide.actions.EditSourceAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.EmptyAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.diff.requests.DiffRequest;
import com.intellij.openapi.util.diff.tools.util.DiffDataKeys;
import com.intellij.openapi.util.diff.util.DiffUserDataKeys;
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
    if (request != null && request.getUserData(DiffUserDataKeys.GO_TO_SOURCE_DISABLE) != null) {
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
