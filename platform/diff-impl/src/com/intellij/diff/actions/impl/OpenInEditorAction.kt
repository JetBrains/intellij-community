// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.actions.impl;

import com.intellij.diff.DiffContext;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUtil;
import com.intellij.ide.actions.EditSourceAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.fileEditor.FileNavigator;
import com.intellij.openapi.fileEditor.FileNavigatorImpl;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OpenInEditorAction extends EditSourceAction implements DumbAware {
  public static final DataKey<Runnable> AFTER_NAVIGATE_CALLBACK = DataKey.create("diff_after_navigate_callback");

  public OpenInEditorAction() {
    ActionUtil.copyFrom(this, "EditSource");
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (!e.isFromActionToolbar()) {
      e.getPresentation().setEnabledAndVisible(true);
      return;
    }

    DiffRequest request = e.getData(DiffDataKeys.DIFF_REQUEST);
    DiffContext context = e.getData(DiffDataKeys.DIFF_CONTEXT);
    if (DiffUtil.isUserDataFlagSet(DiffUserDataKeys.GO_TO_SOURCE_DISABLE, request, context)) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    if (e.getProject() == null) {
      e.getPresentation().setVisible(true);
      e.getPresentation().setEnabled(false);
      return;
    }

    Navigatable[] navigatables = e.getData(DiffDataKeys.NAVIGATABLE_ARRAY);
    if (navigatables == null || !ContainerUtil.exists(navigatables, (it) -> it.canNavigate())) {
      e.getPresentation().setVisible(true);
      e.getPresentation().setEnabled(false);
      return;
    }

    e.getPresentation().setEnabledAndVisible(true);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DiffRequest request = e.getData(DiffDataKeys.DIFF_REQUEST);
    DiffContext context = e.getData(DiffDataKeys.DIFF_CONTEXT);
    if (DiffUtil.isUserDataFlagSet(DiffUserDataKeys.GO_TO_SOURCE_DISABLE, request, context)) return;

    Project project = e.getProject();
    if (project == null) return;

    Runnable callback = e.getData(AFTER_NAVIGATE_CALLBACK);
    Navigatable[] navigatables = e.getData(DiffDataKeys.NAVIGATABLE_ARRAY);
    if (navigatables == null) return;

    openEditor(project, navigatables, callback);
  }

  public static boolean openEditor(@NotNull Project project, @NotNull Navigatable navigatable, @Nullable Runnable callback) {
    return openEditor(project, new Navigatable[]{navigatable}, callback);
  }

  public static boolean openEditor(@NotNull Project project, Navigatable @NotNull [] navigatables, @Nullable Runnable callback) {
    FileNavigatorImpl fileNavigator = (FileNavigatorImpl)FileNavigator.getInstance();
    boolean success = false;
    for (Navigatable navigatable : navigatables) {
      success |= fileNavigator.navigateIgnoringContextEditor(navigatable);
    }
    if (success && callback != null) {
      callback.run();
    }
    return success;
  }
}
