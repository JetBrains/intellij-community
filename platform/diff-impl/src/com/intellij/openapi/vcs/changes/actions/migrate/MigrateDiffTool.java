// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.migrate;

import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestPanel;
import com.intellij.diff.InvalidDiffRequestException;
import com.intellij.diff.merge.MergeRequest;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.DiffTool;
import com.intellij.openapi.diff.DiffViewer;
import com.intellij.openapi.diff.impl.mergeTool.MergeRequestImpl;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.WindowWrapper;
import com.intellij.openapi.ui.ex.MessagesEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;

public final class MigrateDiffTool implements DiffTool {
  public static final MigrateDiffTool INSTANCE = new MigrateDiffTool();

  private MigrateDiffTool() {
  }

  @Override
  public void show(DiffRequest request) {
    if (isMergeRequest(request)) {
      try {
        MergeRequest newRequest = MigrateToNewDiffUtil.convertMergeRequest((MergeRequestImpl)request);
        DiffManager.getInstance().showMerge(request.getProject(), newRequest);
      }
      catch (InvalidDiffRequestException e) {
        MessagesEx.error(request.getProject(), e.getMessage()).showNow();
      }
    }
    else {
      com.intellij.diff.requests.DiffRequest newRequest = MigrateToNewDiffUtil.convertRequest(request);

      Runnable onOkRunnable = request.getOnOkRunnable();
      if (onOkRunnable == null) {
        DiffManager.getInstance().showDiff(request.getProject(), newRequest, new DiffDialogHints(getWindowMode(request.getHints())));
      }
      else {
        DialogBuilder builder = new DialogBuilder(request.getProject());
        DiffRequestPanel diffPanel = DiffManager.getInstance().createRequestPanel(request.getProject(), builder, builder.getWindow());
        diffPanel.setRequest(newRequest);

        builder.setCenterPanel(diffPanel.getComponent());
        builder.setPreferredFocusComponent(diffPanel.getPreferredFocusedComponent());
        builder.setTitle(request.getWindowTitle());
        builder.setDimensionServiceKey(request.getGroupKey());

        builder.setOkOperation(() -> {
          builder.getDialogWrapper().close(DialogWrapper.OK_EXIT_CODE);
          onOkRunnable.run();
        });

        boolean useNonModal = request.getHints().contains(DiffTool.HINT_SHOW_NOT_MODAL_DIALOG);
        builder.showModal(!useNonModal);
      }
    }
  }

  @Override
  public boolean canShow(DiffRequest request) {
    return request.getContents().length == 2 || request.getContents().length == 3;
  }

  private static boolean isMergeRequest(DiffRequest request) {
    return request instanceof MergeRequestImpl && ((MergeRequestImpl)request).getMergeContent() != null;
  }

  @Override
  public DiffViewer createComponent(String title, DiffRequest request, Window window, @NotNull Disposable parentDisposable) {
    return null;
  }

  @Nullable
  private static WindowWrapper.Mode getWindowMode(Collection hints) {
    if (hints.contains(DiffTool.HINT_SHOW_MODAL_DIALOG)) return WindowWrapper.Mode.MODAL;
    if (hints.contains(DiffTool.HINT_SHOW_NOT_MODAL_DIALOG)) return WindowWrapper.Mode.NON_MODAL;
    if (hints.contains(DiffTool.HINT_SHOW_FRAME)) return WindowWrapper.Mode.FRAME;
    return null;
  }
}
