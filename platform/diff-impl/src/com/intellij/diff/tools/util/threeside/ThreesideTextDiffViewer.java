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
package com.intellij.diff.tools.util.threeside;

import com.intellij.diff.DiffContext;
import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManager;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.diff.tools.util.FocusTrackerSupport.ThreesideFocusTrackerSupport;
import com.intellij.diff.tools.util.SimpleDiffPanel;
import com.intellij.diff.tools.util.SyncScrollSupport;
import com.intellij.diff.tools.util.SyncScrollSupport.ThreesideSyncScrollSupport;
import com.intellij.diff.tools.util.base.InitialScrollPositionSupport;
import com.intellij.diff.tools.util.base.TextDiffViewerBase;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Side;
import com.intellij.diff.util.ThreeSide;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public abstract class ThreesideTextDiffViewer extends TextDiffViewerBase {
  public static final Logger LOG = Logger.getInstance(ThreesideTextDiffViewer.class);

  @NotNull private final EditorFactory myEditorFactory = EditorFactory.getInstance();

  @NotNull protected final SimpleDiffPanel myPanel;
  @NotNull protected final ThreesideTextContentPanel myContentPanel;

  @NotNull private final List<EditorEx> myEditors;
  @NotNull private final List<DocumentContent> myActualContents;

  @NotNull private final MyVisibleAreaListener myVisibleAreaListener1 = new MyVisibleAreaListener(Side.LEFT);
  @NotNull private final MyVisibleAreaListener myVisibleAreaListener2 = new MyVisibleAreaListener(Side.RIGHT);

  @NotNull protected final MySetEditorSettingsAction myEditorSettingsAction;

  @NotNull private final ThreesideFocusTrackerSupport myFocusTrackerSupport;

  @Nullable private ThreesideSyncScrollSupport mySyncScrollSupport;

  public ThreesideTextDiffViewer(@NotNull DiffContext context, @NotNull ContentDiffRequest request) {
    super(context, request);

    List<DiffContent> contents = myRequest.getContents();
    myActualContents = ContainerUtil.newArrayList((DocumentContent)contents.get(0),
                                                  (DocumentContent)contents.get(1),
                                                  (DocumentContent)contents.get(2));


    myEditors = createEditors();
    List<JComponent> titlePanel = DiffUtil.createTextTitles(myRequest, getEditors());

    myFocusTrackerSupport = new ThreesideFocusTrackerSupport(getEditors());
    myContentPanel = new ThreesideTextContentPanel(getEditors(), titlePanel);

    myPanel = new SimpleDiffPanel(myContentPanel, this, context);


    //new MyFocusOppositePaneAction().setupAction(myPanel, this); // TODO

    myEditorSettingsAction = new MySetEditorSettingsAction();
    myEditorSettingsAction.applyDefaults();
  }

  @Override
  @CalledInAwt
  protected void onDispose() {
    super.onDispose();
    destroyEditors();
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
  protected List<EditorEx> createEditors() {
    boolean[] forceReadOnly = checkForceReadOnly();
    List<EditorEx> editors = new ArrayList<EditorEx>(3);

    for (int i = 0; i < getActualContents().size(); i++) {
      DocumentContent content = getActualContents().get(i);
      EditorEx editor = DiffUtil.createEditor(content.getDocument(), myProject, forceReadOnly[i], true);
      DiffUtil.configureEditor(editor, content, myProject);
      editors.add(editor);

      if (Registry.is("diff.divider.repainting.disable.blitting")) {
        editor.getScrollPane().getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
      }
    }

    editors.get(0).setVerticalScrollbarOrientation(EditorEx.VERTICAL_SCROLLBAR_LEFT);
    ((EditorMarkupModel)editors.get(1).getMarkupModel()).setErrorStripeVisible(false);

    return editors;
  }

  private void destroyEditors() {
    for (EditorEx editor : getEditors()) {
      myEditorFactory.releaseEditor(editor);
    }
  }

  //
  // Listeners
  //

  @CalledInAwt
  @Override
  protected void installEditorListeners() {
    super.installEditorListeners();

    getEditor(0).getScrollingModel().addVisibleAreaListener(myVisibleAreaListener1);
    getEditor(1).getScrollingModel().addVisibleAreaListener(myVisibleAreaListener1);

    getEditor(1).getScrollingModel().addVisibleAreaListener(myVisibleAreaListener2);
    getEditor(2).getScrollingModel().addVisibleAreaListener(myVisibleAreaListener2);

    SyncScrollSupport.SyncScrollable scrollable1 = getSyncScrollable(Side.LEFT);
    SyncScrollSupport.SyncScrollable scrollable2 = getSyncScrollable(Side.RIGHT);
    if (scrollable1 != null && scrollable2 != null) {
      mySyncScrollSupport = new ThreesideSyncScrollSupport(getEditors(), scrollable1, scrollable2);
    }
  }

  @CalledInAwt
  @Override
  public void destroyEditorListeners() {
    super.destroyEditorListeners();

    getEditor(0).getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener1);
    getEditor(1).getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener1);

    getEditor(1).getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener2);
    getEditor(2).getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener2);

    mySyncScrollSupport = null;
  }

  protected void disableSyncScrollSupport(boolean disable) {
    if (mySyncScrollSupport != null) {
      mySyncScrollSupport.setDisabled(disable);
    }
  }

  //
  // Diff
  //

  @Override
  protected void onDocumentChange(@NotNull DocumentEvent event) {
    super.onDocumentChange(event);

    myContentPanel.repaintDividers();
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
    return getCurrentEditor().getContentComponent();
  }

  @NotNull
  public EditorEx getCurrentEditor() {
    return getCurrentSide().select(getEditors());
  }

  @NotNull
  public DocumentContent getCurrentContent() {
    return getCurrentSide().select(getActualContents());
  }

  @NotNull
  @Override
  protected List<? extends EditorEx> getEditors() {
    return myEditors;
  }

  @NotNull
  protected EditorEx getEditor(int index) {
    return myEditors.get(index);
  }

  @NotNull
  public List<DocumentContent> getActualContents() {
    return myActualContents;
  }

  @NotNull
  public ThreeSide getCurrentSide() {
    return myFocusTrackerSupport.getCurrentSide();
  }

  public void setCurrentSide(@NotNull ThreeSide side) {
    myFocusTrackerSupport.setCurrentSide(side);
  }

  //
  // Abstract
  //

  @CalledInAwt
  protected void scrollToLine(@NotNull ThreeSide side, int line) {
    Editor editor = side.select(getEditors());
    DiffUtil.scrollEditor(editor, line, false);
    setCurrentSide(side);
  }

  @Nullable
  protected abstract SyncScrollSupport.SyncScrollable getSyncScrollable(@NotNull Side side);

  //
  // Misc
  //

  @Nullable
  @Override
  protected OpenFileDescriptor getOpenFileDescriptor() {
    EditorEx editor = getCurrentEditor();

    int offset = editor.getCaretModel().getOffset();
    return getCurrentContent().getOpenFileDescriptor(offset);
  }

  public static boolean canShowRequest(@NotNull DiffContext context, @NotNull DiffRequest request) {
    if (!(request instanceof ContentDiffRequest)) return false;

    List<DiffContent> contents = ((ContentDiffRequest)request).getContents();
    if (contents.size() != 3) return false;

    boolean canShow = true;
    boolean wantShow = false;
    for (DiffContent content : contents) {
      canShow &= canShowContent(content);
      wantShow |= wantShowContent(content);
    }
    return canShow && wantShow;
  }

  public static boolean canShowContent(@NotNull DiffContent content) {
    if (content instanceof DocumentContent) return true;
    return false;
  }

  public static boolean wantShowContent(@NotNull DiffContent content) {
    if (content instanceof DocumentContent) return true;
    return false;
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

    public ShowPartialDiffAction(@NotNull ThreeSide side1, @NotNull ThreeSide side2,
                                 @NotNull String text, @Nullable String description, @NotNull Icon icon) {
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
                                                  mySide1.select(titles), mySide1.select(titles));
      DiffManager.getInstance().showDiff(myProject, request, new DiffDialogHints(null, myPanel));
    }
  }

  //
  // Helpers
  //

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (DiffDataKeys.CURRENT_EDITOR.is(dataId)) {
      return getCurrentEditor();
    }
    else if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
      return DiffUtil.getVirtualFile(myRequest, getCurrentSide());
    }
    else if (DiffDataKeys.CURRENT_CONTENT.is(dataId)) {
      return getCurrentContent();
    }
    return super.getData(dataId);
  }
  
  private class MyVisibleAreaListener implements VisibleAreaListener {
    @NotNull Side mySide;

    public MyVisibleAreaListener(@NotNull Side side) {
      mySide = side;
    }

    @Override
    public void visibleAreaChanged(VisibleAreaEvent e) {
      if (mySyncScrollSupport != null) mySyncScrollSupport.visibleAreaChanged(e);
      if (Registry.is("diff.divider.repainting.fix")) {
        myContentPanel.repaint();
      }
      else {
        myContentPanel.repaintDivider(mySide);
      }
    }
  }

  protected abstract class MyInitialScrollPositionHelper extends InitialScrollPositionSupport.ThreesideInitialScrollHelper {
    @NotNull
    @Override
    protected List<? extends Editor> getEditors() {
      return ThreesideTextDiffViewer.this.getEditors();
    }

    @Override
    protected void disableSyncScroll(boolean value) {
      disableSyncScrollSupport(value);
    }

    @Override
    protected boolean doScrollToLine() {
      if (myScrollToLine == null) return false;
      ThreeSide side = myScrollToLine.first;
      Integer line = myScrollToLine.second;
      if (side.select(getEditors()) == null) return false;

      scrollToLine(side, line);
      return true;
    }
  }
}
