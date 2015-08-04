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
package com.intellij.diff.tools.util.side;

import com.intellij.diff.DiffContext;
import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManager;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.tools.holders.EditorHolder;
import com.intellij.diff.tools.holders.EditorHolderFactory;
import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.diff.tools.util.FocusTrackerSupport.ThreesideFocusTrackerSupport;
import com.intellij.diff.tools.util.SimpleDiffPanel;
import com.intellij.diff.tools.util.base.ListenerDiffViewerBase;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.ThreeSide;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public abstract class ThreesideDiffViewer<T extends EditorHolder> extends ListenerDiffViewerBase {
  @NotNull protected final SimpleDiffPanel myPanel;
  @NotNull protected final ThreesideContentPanel myContentPanel;

  @NotNull private final List<T> myHolders;

  @NotNull private final ThreesideFocusTrackerSupport myFocusTrackerSupport;

  public ThreesideDiffViewer(@NotNull DiffContext context, @NotNull ContentDiffRequest request, @NotNull EditorHolderFactory<T> factory) {
    super(context, request);

    myHolders = createEditorHolders(factory);

    List<JComponent> titlePanel = createTitles();
    myFocusTrackerSupport = new ThreesideFocusTrackerSupport(myHolders);
    myContentPanel = new ThreesideContentPanel(myHolders, titlePanel);

    myPanel = new SimpleDiffPanel(myContentPanel, this, context);
  }

  @Override
  @CalledInAwt
  protected void onDispose() {
    destroyEditorHolders();
    super.onDispose();
  }

  @Override
  @CalledInAwt
  protected void processContextHints() {
    super.processContextHints();
    myFocusTrackerSupport.processContextHints(myRequest, myContext);
  }

  @Override
  @CalledInAwt
  protected void updateContextHints() {
    super.updateContextHints();
    myFocusTrackerSupport.updateContextHints(myRequest, myContext);
  }

  @NotNull
  protected List<T> createEditorHolders(@NotNull EditorHolderFactory<T> factory) {
    List<DiffContent> contents = myRequest.getContents();

    List<T> holders = new ArrayList<T>(3);
    for (int i = 0; i < 3; i++) {
      DiffContent content = contents.get(i);
      holders.add(factory.create(content, myContext));
    }
    return holders;
  }

  private void destroyEditorHolders() {
    for (T holder : myHolders) {
      Disposer.dispose(holder);
    }
  }

  @NotNull
  protected List<JComponent> createTitles() {
    return DiffUtil.createSimpleTitles(myRequest);
  }

  //
  // Getters
  //

  @NotNull
  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    if (!myPanel.isGoodContent()) return null;
    return getCurrentEditorHolder().getPreferredFocusedComponent();
  }

  @NotNull
  public ThreeSide getCurrentSide() {
    return myFocusTrackerSupport.getCurrentSide();
  }

  protected void setCurrentSide(@NotNull ThreeSide side) {
    myFocusTrackerSupport.setCurrentSide(side);
  }

  @NotNull
  protected List<T> getEditorHolders() {
    return myHolders;
  }

  @NotNull
  protected T getCurrentEditorHolder() {
    return getCurrentSide().select(getEditorHolders());
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
      return DiffUtil.getVirtualFile(myRequest, getCurrentSide());
    }
    else if (DiffDataKeys.CURRENT_CONTENT.is(dataId)) {
      return getCurrentSide().select(myRequest.getContents());
    }
    return super.getData(dataId);
  }

  //
  // Misc
  //

  @Nullable
  @Override
  protected OpenFileDescriptor getOpenFileDescriptor() {
    return getCurrentSide().select(getRequest().getContents()).getOpenFileDescriptor();
  }

  public static <T extends EditorHolder> boolean canShowRequest(@NotNull DiffContext context,
                                                                @NotNull DiffRequest request,
                                                                @NotNull EditorHolderFactory<T> factory) {
    if (!(request instanceof ContentDiffRequest)) return false;

    List<DiffContent> contents = ((ContentDiffRequest)request).getContents();
    if (contents.size() != 3) return false;

    boolean canShow = true;
    boolean wantShow = false;
    for (DiffContent content : contents) {
      canShow &= factory.canShowContent(content, context);
      wantShow |= factory.wantShowContent(content, context);
    }
    return canShow && wantShow;
  }

  //
  // Actions
  //

  protected class ShowLeftBasePartialDiffAction extends ShowPartialDiffAction {
    public ShowLeftBasePartialDiffAction() {
      super(ThreeSide.LEFT, ThreeSide.BASE, DiffBundle.message("merge.partial.diff.action.name.0.1"), null, AllIcons.Diff.LeftDiff);
    }
  }

  protected class ShowBaseRightPartialDiffAction extends ShowPartialDiffAction {
    public ShowBaseRightPartialDiffAction() {
      super(ThreeSide.BASE, ThreeSide.RIGHT, DiffBundle.message("merge.partial.diff.action.name.1.2"), null, AllIcons.Diff.RightDiff);
    }
  }

  protected class ShowLeftRightPartialDiffAction extends ShowPartialDiffAction {
    public ShowLeftRightPartialDiffAction() {
      super(ThreeSide.LEFT, ThreeSide.RIGHT, DiffBundle.message("merge.partial.diff.action.name"), null, AllIcons.Diff.BranchDiff);
    }
  }

  protected class ShowPartialDiffAction extends DumbAwareAction {
    @NotNull private final ThreeSide mySide1;
    @NotNull private final ThreeSide mySide2;

    public ShowPartialDiffAction(@NotNull ThreeSide side1,
                                 @NotNull ThreeSide side2,
                                 @NotNull String text,
                                 @Nullable String description,
                                 @NotNull Icon icon) {
      super(text, description, icon);
      mySide1 = side1;
      mySide2 = side2;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      List<DiffContent> contents = myRequest.getContents();
      List<String> titles = myRequest.getContentTitles();

      DiffRequest request = new SimpleDiffRequest(myRequest.getTitle(),
                                                  mySide1.select(contents), mySide2.select(contents),
                                                  mySide1.select(titles), mySide2.select(titles));
      DiffManager.getInstance().showDiff(myProject, request, new DiffDialogHints(null, myPanel));
    }
  }
}
