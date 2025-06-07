// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge;

import com.intellij.diff.DiffContext;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.ProxySimpleDiffRequest;
import com.intellij.diff.tools.binary.ThreesideBinaryDiffViewer;
import com.intellij.diff.tools.holders.BinaryEditorHolder;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public class BinaryMergeTool implements MergeTool {
  public static final BinaryMergeTool INSTANCE = new BinaryMergeTool();

  @Override
  public @NotNull MergeViewer createComponent(@NotNull MergeContext context, @NotNull MergeRequest request) {
    return new BinaryMergeViewer(context, (ThreesideMergeRequest)request);
  }

  @Override
  public boolean canShow(@NotNull MergeContext context, @NotNull MergeRequest request) {
    if (!(request instanceof ThreesideMergeRequest)) return false;

    MergeUtil.ProxyDiffContext diffContext = new MergeUtil.ProxyDiffContext(context);
    for (DiffContent diffContent : ((ThreesideMergeRequest)request).getContents()) {
      if (!BinaryEditorHolder.BinaryEditorHolderFactory.INSTANCE.canShowContent(diffContent, diffContext)) return false;
    }

    return true;
  }

  public static class BinaryMergeViewer implements MergeViewer {
    private final @NotNull MergeContext myMergeContext;
    private final @NotNull ThreesideMergeRequest myMergeRequest;

    private final @NotNull DiffContext myDiffContext;
    private final @NotNull ContentDiffRequest myDiffRequest;

    private final @NotNull MyThreesideViewer myViewer;

    public BinaryMergeViewer(@NotNull MergeContext context, @NotNull ThreesideMergeRequest request) {
      myMergeContext = context;
      myMergeRequest = request;

      myDiffContext = new MergeUtil.ProxyDiffContext(myMergeContext);
      myDiffRequest = new ProxySimpleDiffRequest(myMergeRequest.getTitle(),
                                                 getDiffContents(myMergeRequest),
                                                 getDiffContentTitles(myMergeRequest),
                                                 myMergeRequest);

      myViewer = new MyThreesideViewer(myDiffContext, myDiffRequest);
    }

    private static @NotNull List<DiffContent> getDiffContents(@NotNull ThreesideMergeRequest mergeRequest) {
      return new ArrayList<>(mergeRequest.getContents());
    }

    private static @NotNull List<String> getDiffContentTitles(@NotNull ThreesideMergeRequest mergeRequest) {
      return MergeUtil.notNullizeContentTitles(mergeRequest.getContentTitles());
    }

    //
    // Impl
    //

    @Override
    public @NotNull JComponent getComponent() {
      return myViewer.getComponent();
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
      return myViewer.getPreferredFocusedComponent();
    }

    @Override
    public @NotNull ToolbarComponents init() {
      ToolbarComponents components = new ToolbarComponents();

      FrameDiffTool.ToolbarComponents init = myViewer.init();
      components.statusPanel = init.statusPanel;
      components.toolbarActions = init.toolbarActions;

      components.closeHandler = () -> MergeUtil.showExitWithoutApplyingChangesDialog(this, myMergeRequest, myMergeContext, false);

      return components;
    }

    @Override
    public @Nullable Action getResolveAction(final @NotNull MergeResult result) {
      if (result == MergeResult.RESOLVED) return null;
      return MergeUtil.createSimpleResolveAction(result, myMergeRequest, myMergeContext, this, false);
    }

    @Override
    public void dispose() {
      Disposer.dispose(myViewer);
    }

    //
    // Getters
    //

    public @NotNull MyThreesideViewer getViewer() {
      return myViewer;
    }

    //
    // Viewer
    //

    private static class MyThreesideViewer extends ThreesideBinaryDiffViewer {
      MyThreesideViewer(@NotNull DiffContext context, @NotNull DiffRequest request) {
        super(context, request);
      }

      @Override
      @RequiresEdt
      public void rediff(boolean trySync) {
      }
    }
  }
}
