// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.merge;

import com.intellij.diff.DiffContext;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.ProxySimpleDiffRequest;
import com.intellij.diff.util.*;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.*;

public class TextMergeViewer implements MergeTool.MergeViewer {
  @NotNull private final MergeContext myMergeContext;
  @NotNull private final TextMergeRequest myMergeRequest;

  @NotNull protected final MergeThreesideViewer myViewer;

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

  @NotNull
  private static List<DiffContent> getDiffContents(@NotNull TextMergeRequest mergeRequest) {
    List<DocumentContent> contents = mergeRequest.getContents();

    final DocumentContent left = ThreeSide.LEFT.select(contents);
    final DocumentContent right = ThreeSide.RIGHT.select(contents);
    final DocumentContent output = mergeRequest.getOutputContent();

    return Arrays.asList(left, output, right);
  }

  @NotNull
  private static List<String> getDiffContentTitles(@NotNull TextMergeRequest mergeRequest) {
    List<String> titles = MergeUtil.notNullizeContentTitles(mergeRequest.getContentTitles());
    titles.set(ThreeSide.BASE.getIndex(), DiffBundle.message("merge.version.title.merged.result"));
    return titles;
  }

  //
  // Impl
  //

  @NotNull
  @Override
  public JComponent getComponent() {
    return myViewer.getComponent();
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myViewer.getPreferredFocusedComponent();
  }

  @NotNull
  @Override
  public MergeTool.ToolbarComponents init() {
    MergeTool.ToolbarComponents components = new MergeTool.ToolbarComponents();

    FrameDiffTool.ToolbarComponents init = myViewer.init();
    components.statusPanel = init.statusPanel;
    components.toolbarActions = init.toolbarActions;

    components.closeHandler =
      () -> MergeUtil.showExitWithoutApplyingChangesDialog(this, myMergeRequest, myMergeContext, myViewer.isContentModified());

    return components;
  }

  @Nullable
  @Override
  public Action getResolveAction(@NotNull MergeResult result) {
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

  @NotNull
  public MergeThreesideViewer getViewer() {
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
