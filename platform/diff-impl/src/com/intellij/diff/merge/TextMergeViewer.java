// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge;

import com.intellij.diff.DiffContext;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.ProxySimpleDiffRequest;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.ThreeSide;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;

@ApiStatus.Internal
public class TextMergeViewer implements MergeTool.MergeViewer {
  private final @NotNull MergeContext myMergeContext;
  private final @NotNull TextMergeRequest myMergeRequest;

  protected final @NotNull MergeThreesideViewer myViewer;

  private final Action myCancelResolveAction;
  private final Action myLeftResolveAction;
  private final Action myRightResolveAction;
  private final Action myAcceptResolveAction;

  public TextMergeViewer(@NotNull MergeContext context, @NotNull TextMergeRequest request) {
    myMergeContext = context;
    myMergeRequest = request;

    DiffContext diffContext = new MergeUtil.ProxyDiffContext(myMergeContext);
    ContentDiffRequest diffRequest = new ProxySimpleDiffRequest(myMergeRequest.getTitle(),
                                                                getDiffContents(myMergeRequest),
                                                                getDiffContentTitles(myMergeRequest),
                                                                myMergeRequest);
    diffRequest.putUserData(DiffUserDataKeys.FORCE_READ_ONLY_CONTENTS, new boolean[]{true, false, true});

    myViewer = loadThreeSideViewer(diffContext, diffRequest, myMergeContext, myMergeRequest, this);

    myCancelResolveAction = myViewer.getLoadedResolveAction(MergeResult.CANCEL);
    myLeftResolveAction = myViewer.getLoadedResolveAction(MergeResult.LEFT);
    myRightResolveAction = myViewer.getLoadedResolveAction(MergeResult.RIGHT);
    myAcceptResolveAction = myViewer.getLoadedResolveAction(MergeResult.RESOLVED);
  }

  private static @NotNull List<DiffContent> getDiffContents(@NotNull TextMergeRequest mergeRequest) {
    List<DocumentContent> contents = mergeRequest.getContents();

    final DocumentContent left = ThreeSide.LEFT.select(contents);
    final DocumentContent right = ThreeSide.RIGHT.select(contents);
    final DocumentContent output = mergeRequest.getOutputContent();

    return Arrays.asList(left, output, right);
  }

  private static @NotNull List<String> getDiffContentTitles(@NotNull TextMergeRequest mergeRequest) {
    List<String> titles = MergeUtil.notNullizeContentTitles(mergeRequest.getContentTitles());
    titles.set(ThreeSide.BASE.getIndex(), DiffBundle.message("merge.version.title.merged.result"));
    return titles;
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
  public @NotNull MergeTool.ToolbarComponents init() {
    MergeTool.ToolbarComponents components = new MergeTool.ToolbarComponents();

    FrameDiffTool.ToolbarComponents init = myViewer.init();
    components.statusPanel = init.statusPanel;
    components.toolbarActions = init.toolbarActions;

    components.closeHandler = () -> {
      boolean exit = MergeUtil.showExitWithoutApplyingChangesDialog(this, myMergeRequest, myMergeContext, myViewer.isContentModified());
      if (exit) {
        myViewer.logMergeCancelled();
      }
      return exit;
    };

    return components;
  }

  @Override
  public @Nullable Action getResolveAction(@NotNull MergeResult result) {
    return switch (result) {
      case CANCEL -> myCancelResolveAction;
      case LEFT -> myLeftResolveAction;
      case RIGHT -> myRightResolveAction;
      case RESOLVED -> myAcceptResolveAction;
    };
  }

  @Override
  public void dispose() {
    Disposer.dispose(myViewer);
  }

  //
  // Getters
  //

  public @NotNull MergeThreesideViewer getViewer() {
    return myViewer;
  }

  //
  // Viewer
  //

  protected MergeThreesideViewer loadThreeSideViewer(@NotNull DiffContext context,
                                                     @NotNull ContentDiffRequest request,
                                                     @NotNull MergeContext mergeContext,
                                                     @NotNull TextMergeRequest mergeRequest,
                                                     @NotNull TextMergeViewer mergeViewer) {
    return new MergeThreesideViewer(context, request, mergeContext, mergeRequest, mergeViewer);
  }
}
