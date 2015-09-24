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
package com.intellij.diff.merge;

import com.intellij.diff.DiffContext;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.tools.binary.ThreesideBinaryDiffViewer;
import com.intellij.diff.tools.holders.BinaryEditorHolder;
import com.intellij.openapi.util.BooleanGetter;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.List;

public class BinaryMergeTool implements MergeTool {
  public static final BinaryMergeTool INSTANCE = new BinaryMergeTool();

  @NotNull
  @Override
  public MergeViewer createComponent(@NotNull MergeContext context, @NotNull MergeRequest request) {
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
    @NotNull private final MergeContext myMergeContext;
    @NotNull private final ThreesideMergeRequest myMergeRequest;

    @NotNull private final DiffContext myDiffContext;
    @NotNull private final ContentDiffRequest myDiffRequest;

    @NotNull private final MyThreesideViewer myViewer;

    public BinaryMergeViewer(@NotNull MergeContext context, @NotNull ThreesideMergeRequest request) {
      myMergeContext = context;
      myMergeRequest = request;

      myDiffContext = new MergeUtil.ProxyDiffContext(myMergeContext);
      myDiffRequest = new SimpleDiffRequest(myMergeRequest.getTitle(),
                                            getDiffContents(myMergeRequest),
                                            getDiffContentTitles(myMergeRequest));

      myViewer = new MyThreesideViewer(myDiffContext, myDiffRequest);
    }

    @NotNull
    private static List<DiffContent> getDiffContents(@NotNull ThreesideMergeRequest mergeRequest) {
      return ContainerUtil.newArrayList(mergeRequest.getContents());
    }

    @NotNull
    private static List<String> getDiffContentTitles(@NotNull ThreesideMergeRequest mergeRequest) {
      return MergeUtil.notNullizeContentTitles(mergeRequest.getContentTitles());
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
    public ToolbarComponents init() {
      ToolbarComponents components = new ToolbarComponents();

      FrameDiffTool.ToolbarComponents init = myViewer.init();
      components.statusPanel = init.statusPanel;
      components.toolbarActions = init.toolbarActions;

      components.closeHandler = new BooleanGetter() {
        @Override
        public boolean get() {
          return MergeUtil.showExitWithoutApplyingChangesDialog(BinaryMergeViewer.this, myMergeRequest, myMergeContext);
        }
      };

      return components;
    }

    @Nullable
    @Override
    public Action getResolveAction(@NotNull final MergeResult result) {
      if (result == MergeResult.RESOLVED) return null;

      String caption = MergeUtil.getResolveActionTitle(result, myMergeRequest, myMergeContext);
      return new AbstractAction(caption) {
        @Override
        public void actionPerformed(ActionEvent e) {
          if (result == MergeResult.CANCEL &&
              !MergeUtil.showExitWithoutApplyingChangesDialog(BinaryMergeViewer.this, myMergeRequest, myMergeContext)) {
            return;
          }
          myMergeContext.finishMerge(result);
        }
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
    public MyThreesideViewer getViewer() {
      return myViewer;
    }

    //
    // Viewer
    //

    private static class MyThreesideViewer extends ThreesideBinaryDiffViewer {
      public MyThreesideViewer(@NotNull DiffContext context, @NotNull DiffRequest request) {
        super(context, request);
      }

      @Override
      @CalledInAwt
      public void rediff(boolean trySync) {
      }
    }
  }
}
