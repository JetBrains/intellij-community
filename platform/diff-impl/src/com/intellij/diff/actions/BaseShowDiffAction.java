// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.actions;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestFactory;
import com.intellij.diff.actions.impl.MutableDiffRequestChain;
import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.util.BlankDiffWindowUtil;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BaseShowDiffAction extends DumbAwareAction {
  BaseShowDiffAction() {
    setEnabledInModalContext(true);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    boolean canShow = isAvailable(e);
    presentation.setEnabled(canShow);
    if (e.isFromContextMenu()) {
      presentation.setVisible(canShow);
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    DiffRequestChain chain = getDiffRequestChain(e);
    if (chain == null) return;

    DiffManager.getInstance().showDiff(project, chain, DiffDialogHints.DEFAULT);
  }

  protected abstract boolean isAvailable(@NotNull AnActionEvent e);

  protected static boolean hasContent(@NotNull VirtualFile file) {
    return !DiffUtil.isFileWithoutContent(file);
  }

  protected abstract @Nullable DiffRequestChain getDiffRequestChain(@NotNull AnActionEvent e);

  public static @NotNull MutableDiffRequestChain createMutableChainFromFiles(@Nullable Project project,
                                                                             @NotNull VirtualFile file1,
                                                                             @NotNull VirtualFile file2) {
    return createMutableChainFromFiles(project, file1, file2, null);
  }

  public static @NotNull MutableDiffRequestChain createMutableChainFromFiles(@Nullable Project project,
                                                                             @NotNull VirtualFile file1,
                                                                             @NotNull VirtualFile file2,
                                                                             @Nullable VirtualFile baseFile) {
    DiffContentFactory contentFactory = DiffContentFactory.getInstance();
    DiffRequestFactory requestFactory = DiffRequestFactory.getInstance();

    DiffContent content1 = contentFactory.create(project, file1);
    DiffContent content2 = contentFactory.create(project, file2);
    DiffContent baseContent = baseFile != null ? contentFactory.create(project, baseFile) : null;

    MutableDiffRequestChain chain;
    if (content1 instanceof DocumentContent && content2 instanceof DocumentContent &&
        (baseContent == null || baseContent instanceof DocumentContent)) {
      chain = BlankDiffWindowUtil.createBlankDiffRequestChain((DocumentContent)content1,
                                                              (DocumentContent)content2,
                                                              (DocumentContent)baseContent,
                                                              project);
    }
    else {
      chain = new MutableDiffRequestChain(content1, baseContent, content2, project);
    }

    if (baseFile != null) {
      chain.setWindowTitle(requestFactory.getTitle(baseFile));
    }
    else {
      chain.setWindowTitle(requestFactory.getTitleForComparison(file1, file2));
    }
    return chain;
  }
}
