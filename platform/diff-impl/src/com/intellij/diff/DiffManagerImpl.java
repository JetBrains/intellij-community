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
package com.intellij.diff;

import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.chains.SimpleDiffRequestChain;
import com.intellij.diff.editor.ChainDiffVirtualFile;
import com.intellij.diff.impl.DiffRequestPanelImpl;
import com.intellij.diff.impl.DiffWindow;
import com.intellij.diff.merge.*;
import com.intellij.diff.merge.external.AutomaticExternalMergeTool;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.binary.BinaryDiffTool;
import com.intellij.diff.tools.dir.DirDiffTool;
import com.intellij.diff.tools.external.ExternalDiffTool;
import com.intellij.diff.tools.external.ExternalMergeTool;
import com.intellij.diff.tools.fragmented.UnifiedDiffTool;
import com.intellij.diff.tools.simple.SimpleDiffTool;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.WindowWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DiffManagerImpl extends DiffManagerEx {
  @Override
  public void showDiff(@Nullable Project project, @NotNull DiffRequest request) {
    showDiff(project, request, DiffDialogHints.DEFAULT);
  }

  @Override
  public void showDiff(@Nullable Project project, @NotNull DiffRequest request, @NotNull DiffDialogHints hints) {
    DiffRequestChain requestChain = new SimpleDiffRequestChain(request);
    showDiff(project, requestChain, hints);
  }

  @Override
  public void showDiff(@Nullable Project project, @NotNull DiffRequestChain requests, @NotNull DiffDialogHints hints) {
    if (ExternalDiffTool.isDefault()) {
      ExternalDiffTool.show(project, requests, hints);
      return;
    }

    showDiffBuiltin(project, requests, hints);
  }

  @Override
  public void showDiffBuiltin(@Nullable Project project, @NotNull DiffRequest request) {
    showDiffBuiltin(project, request, DiffDialogHints.DEFAULT);
  }

  @Override
  public void showDiffBuiltin(@Nullable Project project, @NotNull DiffRequest request, @NotNull DiffDialogHints hints) {
    DiffRequestChain requestChain = new SimpleDiffRequestChain(request);
    showDiffBuiltin(project, requestChain, hints);
  }

  @Override
  public void showDiffBuiltin(@Nullable Project project, @NotNull DiffRequestChain requests, @NotNull DiffDialogHints hints) {
    if (Registry.is("show.diff.as.editor.tab") &&
        project != null &&
        DiffUtil.getWindowMode(hints) == WindowWrapper.Mode.FRAME &&
        hints.getWindowConsumer() == null) {
      ChainDiffVirtualFile diffFile = new ChainDiffVirtualFile(requests, DiffBundle.message("label.default.diff.editor.tab.name"));
      FileEditorManager.getInstance(project).openFile(diffFile, true);
      return;
    }
    new DiffWindow(project, requests, hints).show();
  }

  @NotNull
  @Override
  public DiffRequestPanel createRequestPanel(@Nullable Project project, @NotNull Disposable parent, @Nullable Window window) {
    DiffRequestPanelImpl panel = new DiffRequestPanelImpl(project, window);
    Disposer.register(parent, panel);
    return panel;
  }

  @NotNull
  @Override
  public List<DiffTool> getDiffTools() {
    List<DiffTool> result = new ArrayList<>();
    Collections.addAll(result, DiffTool.EP_NAME.getExtensions());
    result.add(SimpleDiffTool.INSTANCE);
    result.add(UnifiedDiffTool.INSTANCE);
    result.add(BinaryDiffTool.INSTANCE);
    result.add(DirDiffTool.INSTANCE);
    return result;
  }

  @NotNull
  @Override
  public List<MergeTool> getMergeTools() {
    List<MergeTool> result = new ArrayList<>();
    Collections.addAll(result, MergeTool.EP_NAME.getExtensions());
    result.add(TextMergeTool.INSTANCE);
    result.add(BinaryMergeTool.INSTANCE);
    return result;
  }

  @Override
  @RequiresEdt
  public void showMerge(@Nullable Project project, @NotNull MergeRequest request) {
    // plugin may provide a better tool for this MergeRequest
    AutomaticExternalMergeTool tool = AutomaticExternalMergeTool.EP_NAME.findFirstSafe(mergeTool -> mergeTool.canShow(project, request));
    if (tool!=null) {
      tool.show(project, request);
      return;
    }

    if (ExternalMergeTool.isDefault()) {
      ExternalMergeTool.show(project, request);
      return;
    }

    showMergeBuiltin(project, request);
  }

  @Override
  @RequiresEdt
  public void showMergeBuiltin(@Nullable Project project, @NotNull MergeRequest request) {
    new MergeWindow.ForRequest(project, request, DiffDialogHints.MODAL).show();
  }

  @Override
  @RequiresEdt
  public void showMergeBuiltin(@Nullable Project project, @NotNull MergeRequestProducer requestProducer, @NotNull DiffDialogHints hints) {
    new MergeWindow.ForProducer(project, requestProducer, hints).show();
  }
}
