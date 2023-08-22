/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.actions;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestFactory;
import com.intellij.diff.actions.impl.MutableDiffRequestChain;
import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.editor.DiffVirtualFile;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithoutContent;
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
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
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

  @Nullable
  protected abstract DiffRequestChain getDiffRequestChain(@NotNull AnActionEvent e);

  @NotNull
  protected static MutableDiffRequestChain createMutableChainFromFiles(@Nullable Project project,
                                                                       @NotNull VirtualFile file1,
                                                                       @NotNull VirtualFile file2) {
    return createMutableChainFromFiles(project, file1, file2, null);
  }

  @NotNull
  protected static MutableDiffRequestChain createMutableChainFromFiles(@Nullable Project project,
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
                                                              (DocumentContent)baseContent);
    }
    else {
      chain = new MutableDiffRequestChain(content1, baseContent, content2);
    }

    if (baseFile != null) {
      chain.setWindowTitle(requestFactory.getTitle(baseFile));
    }
    else {
      chain.setWindowTitle(requestFactory.getTitle(file1, file2));
    }
    return chain;
  }
}
