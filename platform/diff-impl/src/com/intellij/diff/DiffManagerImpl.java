// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff;

import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.chains.SimpleDiffRequestChain;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.editor.ChainDiffVirtualFile;
import com.intellij.diff.editor.DiffEditorTabFilesManager;
import com.intellij.diff.impl.DiffRequestPanelImpl;
import com.intellij.diff.impl.DiffWindow;
import com.intellij.diff.merge.*;
import com.intellij.diff.merge.external.AutomaticExternalMergeTool;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.binary.BinaryDiffTool;
import com.intellij.diff.tools.dir.DirDiffTool;
import com.intellij.diff.tools.external.ExternalDiffSettings;
import com.intellij.diff.tools.external.ExternalDiffTool;
import com.intellij.diff.tools.external.ExternalMergeTool;
import com.intellij.diff.tools.fragmented.UnifiedDiffTool;
import com.intellij.diff.tools.simple.SimpleDiffTool;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.WindowWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
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
    List<String> files = ContainerUtil.map(requests.getRequests(), DiffRequestProducer::getName);
    FileType fileType = determineFileType(files);

    ExternalDiffSettings.ExternalTool diffTool = ExternalDiffSettings.findDiffTool(fileType);
    if (ExternalDiffTool.isEnabled() && diffTool != null) {
      ExternalDiffTool.show(project, requests, hints, diffTool);
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
    DiffEditorTabFilesManager diffEditorTabFilesManager = project != null ? DiffEditorTabFilesManager.getInstance(project) : null;
    if (diffEditorTabFilesManager != null &&
        !Registry.is("show.diff.as.frame") &&
        DiffUtil.getWindowMode(hints) == WindowWrapper.Mode.FRAME &&
        !isFromDialog(project) &&
        hints.getWindowConsumer() == null) {
      ChainDiffVirtualFile diffFile = new ChainDiffVirtualFile(requests, DiffBundle.message("label.default.diff.editor.tab.name"));
      diffEditorTabFilesManager.showDiffFile(diffFile, true);
      return;
    }
    new DiffWindow(project, requests, hints).show();
  }

  private static boolean isFromDialog(@Nullable Project project) {
    return DialogWrapper.findInstance(IdeFocusManager.getInstance(project).getFocusOwner()) != null;
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
    if (tool != null) {
      tool.show(project, request);
      return;
    }

    if (request instanceof ThreesideMergeRequest) {
      ThreesideMergeRequest mergeRequest = (ThreesideMergeRequest)request;
      DiffContent outputContent = mergeRequest.getOutputContent();
      FileType fileType = outputContent.getContentType();

      if (fileType != null) {
        ExternalDiffSettings.ExternalTool mergeTool = ExternalDiffSettings.findMergeTool(fileType);
        if (ExternalMergeTool.isEnabled() && mergeTool != null) {
          ExternalMergeTool.show(project, mergeTool, request);
          return;
        }
      }
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

  private static FileType determineFileType(@NotNull List<String> files) {
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();

    return files.stream()
      .filter(filePath -> !FileUtilRt.getExtension(filePath).equals("tmp"))
      .map(filePath -> fileTypeManager.getFileTypeByFileName(filePath))
      .findAny()
      .orElse(FileTypes.UNKNOWN);
  }
}
