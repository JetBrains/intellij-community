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
import com.intellij.diff.tools.util.SyncScrollSupport;
import com.intellij.diff.tools.util.SyncScrollSupport.ThreesideSyncScrollSupport;
import com.intellij.diff.tools.util.base.TextDiffViewerBase;
import com.intellij.diff.util.*;
import com.intellij.diff.util.DiffUserDataKeysEx.ScrollToPolicy;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.List;

public abstract class ThreesideTextDiffViewer extends TextDiffViewerBase {
  public static final Logger LOG = Logger.getInstance(ThreesideTextDiffViewer.class);

  @NotNull private final EditorFactory myEditorFactory = EditorFactory.getInstance();

  @NotNull protected final ThreesideTextDiffPanel myPanel;
  @NotNull protected final ThreesideTextContentPanel myContentPanel;

  @NotNull protected final List<EditorEx> myEditors;

  @NotNull protected final List<DocumentContent> myActualContents;

  @NotNull private final List<MyEditorFocusListener> myEditorFocusListeners =
    ContainerUtil.newArrayList(new MyEditorFocusListener(ThreeSide.LEFT),
                               new MyEditorFocusListener(ThreeSide.BASE),
                               new MyEditorFocusListener(ThreeSide.RIGHT));
  @NotNull private final MyVisibleAreaListener myVisibleAreaListener1 = new MyVisibleAreaListener(Side.LEFT);
  @NotNull private final MyVisibleAreaListener myVisibleAreaListener2 = new MyVisibleAreaListener(Side.RIGHT);

  @NotNull protected final MySetEditorSettingsAction myEditorSettingsAction;

  @NotNull private final MyScrollToLineHelper myScrollToLineHelper = new MyScrollToLineHelper();

  @Nullable private ThreesideSyncScrollSupport mySyncScrollListener;

  @NotNull private ThreeSide myCurrentSide;

  public ThreesideTextDiffViewer(@NotNull DiffContext context, @NotNull ContentDiffRequest request) {
    super(context, request);

    DiffContent[] contents = myRequest.getContents();
    myActualContents = ContainerUtil.newArrayList((DocumentContent)contents[0], (DocumentContent)contents[1], (DocumentContent)contents[2]);


    myEditors = createEditors();
    List<JComponent> titlePanel = DiffUtil.createTextTitles(myRequest, myEditors);

    myCurrentSide = ThreeSide.BASE;

    myContentPanel = new ThreesideTextContentPanel(myEditors, titlePanel);

    myPanel = new ThreesideTextDiffPanel(this, myContentPanel, this, context);


    //new MyFocusOppositePaneAction().setupAction(myPanel, this); // FIXME

    myEditorSettingsAction = new MySetEditorSettingsAction();
    myEditorSettingsAction.applyDefaults();
  }

  @Override
  protected void onInit() {
    super.onInit();
    processContextHints();
  }

  @Override
  protected void onDispose() {
    updateContextHints();
    super.onDispose();
  }

  @Override
  protected void onDisposeAwt() {
    destroyEditors();
    super.onDisposeAwt();
  }

  protected void processContextHints() {
    ThreeSide side = myContext.getUserData(DiffUserDataKeys.PREFERRED_FOCUS_THREESIDE);
    if (side != null) myCurrentSide = side;

    myScrollToLineHelper.processContext();
  }

  protected void updateContextHints() {
    myContext.putUserData(DiffUserDataKeys.PREFERRED_FOCUS_THREESIDE, myCurrentSide);

    myScrollToLineHelper.updateContext();
  }

  @NotNull
  protected List<EditorEx> createEditors() {
    boolean[] forceReadOnly = checkForceReadOnly();
    List<EditorEx> editors = new ArrayList<EditorEx>(3);

    for (int i = 0; i < myActualContents.size(); i++) {
      DocumentContent content = myActualContents.get(i);
      EditorEx editor = DiffUtil.createEditor(content.getDocument(), myProject, forceReadOnly[i], true);
      DiffUtil.configureEditor(editor, content, myProject);
      editors.add(editor);
    }

    editors.get(0).setVerticalScrollbarOrientation(EditorEx.VERTICAL_SCROLLBAR_LEFT);
    ((EditorMarkupModel)editors.get(1).getMarkupModel()).setErrorStripeVisible(false);

    return editors;
  }

  private void destroyEditors() {
    for (EditorEx editor : myEditors) {
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
    for (int i = 0; i < 3; i++) {
      myEditors.get(i).getContentComponent().addFocusListener(myEditorFocusListeners.get(i));
    }

    myEditors.get(0).getScrollingModel().addVisibleAreaListener(myVisibleAreaListener1);
    myEditors.get(1).getScrollingModel().addVisibleAreaListener(myVisibleAreaListener1);

    myEditors.get(1).getScrollingModel().addVisibleAreaListener(myVisibleAreaListener2);
    myEditors.get(2).getScrollingModel().addVisibleAreaListener(myVisibleAreaListener2);

    SyncScrollSupport.SyncScrollable scrollable1 = getSyncScrollable(Side.LEFT);
    SyncScrollSupport.SyncScrollable scrollable2 = getSyncScrollable(Side.RIGHT);
    if (scrollable1 != null && scrollable2 != null) {
      mySyncScrollListener = new ThreesideSyncScrollSupport(myEditors, scrollable1, scrollable2);
    }
  }

  @CalledInAwt
  @Override
  public void destroyEditorListeners() {
    super.destroyEditorListeners();

    for (int i = 0; i < 3; i++) {
      myEditors.get(i).getContentComponent().removeFocusListener(myEditorFocusListeners.get(i));
    }

    myEditors.get(0).getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener1);
    myEditors.get(1).getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener1);

    myEditors.get(1).getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener2);
    myEditors.get(2).getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener2);

    if (mySyncScrollListener != null) {
      mySyncScrollListener = null;
    }
  }

  protected void disableSyncScrollSupport(boolean disable) {
    if (mySyncScrollListener != null) {
      mySyncScrollListener.myDuringSyncScroll = disable;
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

  @CalledInAwt
  protected void scrollOnRediff() {
    myScrollToLineHelper.onRediff();
  }

  @Override
  protected void onSlowRediff() {
    super.onSlowRediff();
    myScrollToLineHelper.onSlowRediff();
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
    return myPanel.getPreferredFocusedComponent();
  }

  @NotNull
  public EditorEx getCurrentEditor() {
    return myCurrentSide.selectN(myEditors);
  }

  @NotNull
  @Override
  protected List<? extends EditorEx> getEditors() {
    return myEditors;
  }

  @NotNull
  public ThreeSide getCurrentSide() {
    return myCurrentSide;
  }

  //
  // Abstract
  //

  @CalledInAwt
  protected void scrollToLine(@NotNull ThreeSide side, int line) {
    Editor editor = side.selectN(myEditors);
    DiffUtil.scrollEditor(editor, line);
    myCurrentSide = side;
  }

  @CalledInAwt
  protected boolean doScrollToChange(@NotNull ScrollToPolicy scrollToChangePolicy) {
    return false;
  }

  @Nullable
  protected abstract SyncScrollSupport.SyncScrollable getSyncScrollable(@NotNull Side side);

  //
  // Misc
  //

  @Override
  protected boolean tryRediffSynchronously() {
    return myPanel.isWindowFocused();
  }

  @Nullable
  @Override
  protected OpenFileDescriptor getOpenFileDescriptor() {
    EditorEx editor = getCurrentEditor();

    DocumentContent content = getCurrentSide().selectN(myActualContents);

    int offset = editor.getCaretModel().getOffset();
    return content.getOpenFileDescriptor(offset);
  }

  public static boolean canShowRequest(@NotNull DiffContext context, @NotNull DiffRequest request) {
    if (!(request instanceof ContentDiffRequest)) return false;

    DiffContent[] contents = ((ContentDiffRequest)request).getContents();
    if (contents.length != 3) return false;

    if (!canShowContent(contents[0])) return false;
    if (!canShowContent(contents[1])) return false;
    if (!canShowContent(contents[2])) return false;

    return true;
  }

  public static boolean canShowContent(@NotNull DiffContent content) {
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
      DiffContent[] contents = myRequest.getContents();
      String[] titles = myRequest.getContentTitles();

      DiffRequest request = new SimpleDiffRequest(myRequest.getTitle(), mySide1.selectN(contents), mySide2.selectN(contents),
                                                  mySide1.selectN(titles), mySide1.selectN(titles));
      DiffManager.getInstance().showDiff(myProject, request, new DiffDialogHints(null, myPanel));
    }
  }

  //
  // Helpers
  //

  @NotNull
  protected Graphics2D getDividerGraphics(@NotNull Graphics g, @NotNull Component divider) {
    int width = divider.getWidth();
    int editorHeight = myEditors.get(0).getComponent().getHeight();
    int dividerOffset = divider.getLocationOnScreen().y;
    int editorOffset = myEditors.get(0).getComponent().getLocationOnScreen().y;
    return (Graphics2D)g.create(0, editorOffset - dividerOffset, width, editorHeight);
  }

  private class MyEditorFocusListener extends FocusAdapter {
    @NotNull private final ThreeSide mySide;

    private MyEditorFocusListener(@NotNull ThreeSide side) {
      mySide = side;
    }

    public void focusGained(FocusEvent e) {
      myCurrentSide = mySide;
    }
  }

  private class MyVisibleAreaListener implements VisibleAreaListener {
    @NotNull Side mySide;

    public MyVisibleAreaListener(@NotNull Side side) {
      mySide = side;
    }

    @Override
    public void visibleAreaChanged(VisibleAreaEvent e) {
      if (mySyncScrollListener != null) mySyncScrollListener.visibleAreaChanged(e);
      if (Registry.is("diff.divider.repainting.fix")) {
        myContentPanel.repaint();
      }
      else {
        myContentPanel.repaintDivider(mySide);
      }
    }
  }

  private class MyScrollToLineHelper {
    protected boolean myShouldScroll = true;

    @Nullable private ScrollToPolicy myScrollToChange;
    @Nullable private EditorsPosition myEditorsPosition;
    @Nullable private LogicalPosition[] myCaretPosition;
    @Nullable private Pair<ThreeSide, Integer> myScrollToLine;

    public void processContext() {
      myScrollToChange = myRequest.getUserData(DiffUserDataKeysEx.SCROLL_TO_CHANGE);
      myEditorsPosition = myRequest.getUserData(EditorsPosition.KEY);
      myCaretPosition = myRequest.getUserData(DiffUserDataKeysEx.EDITORS_CARET_POSITION);
      myScrollToLine = myRequest.getUserData(DiffUserDataKeys.SCROLL_TO_LINE_THREESIDE);
    }

    public void updateContext() {
      LogicalPosition[] carets = new LogicalPosition[3];
      carets[0] = getPosition(myEditors.get(0));
      carets[1] = getPosition(myEditors.get(1));
      carets[2] = getPosition(myEditors.get(2));

      Point[] points = new Point[3];
      points[0] = DiffUtil.getScrollingPoint(myEditors.get(0));
      points[1] = DiffUtil.getScrollingPoint(myEditors.get(1));
      points[2] = DiffUtil.getScrollingPoint(myEditors.get(2));

      EditorsPosition editorsPosition = new EditorsPosition(carets, points);

      myRequest.putUserData(DiffUserDataKeysEx.SCROLL_TO_CHANGE, null);
      myRequest.putUserData(EditorsPosition.KEY, editorsPosition);
      myRequest.putUserData(DiffUserDataKeysEx.EDITORS_CARET_POSITION, carets);
      myRequest.putUserData(DiffUserDataKeys.SCROLL_TO_LINE_THREESIDE, null);
    }

    public void onSlowRediff() {
      if (myScrollToChange != null) return;
      if (myShouldScroll && myScrollToLine != null) {
        myShouldScroll = !doScrollToLine();
      }
      if (myShouldScroll && myCaretPosition != null) {
        myShouldScroll = !doScrollToPosition();
      }
    }

    public void onRediff() {
      if (myShouldScroll && myScrollToChange != null) {
        myShouldScroll = !doScrollToChange(myScrollToChange);
      }
      if (myShouldScroll && myScrollToLine != null) {
        myShouldScroll = !doScrollToLine();
      }
      if (myShouldScroll && myCaretPosition != null) {
        myShouldScroll = !doScrollToPosition();
      }
      if (myShouldScroll) {
        doScrollToChange(ScrollToPolicy.FIRST_CHANGE);
      }
      myShouldScroll = false;
    }

    private boolean doScrollToPosition() {
      if (myCaretPosition == null || myCaretPosition.length != 3) return false;

      myEditors.get(0).getCaretModel().moveToLogicalPosition(myCaretPosition[0]);
      myEditors.get(1).getCaretModel().moveToLogicalPosition(myCaretPosition[1]);
      myEditors.get(2).getCaretModel().moveToLogicalPosition(myCaretPosition[2]);

      if (myEditorsPosition != null && myEditorsPosition.isSame(myCaretPosition)) {
        try {
          disableSyncScrollSupport(true);

          DiffUtil.scrollToPoint(myEditors.get(0), myEditorsPosition.myPoints[0]);
          DiffUtil.scrollToPoint(myEditors.get(1), myEditorsPosition.myPoints[1]);
          DiffUtil.scrollToPoint(myEditors.get(2), myEditorsPosition.myPoints[2]);
        }
        finally {
          disableSyncScrollSupport(false);
        }
      }
      else {
        getCurrentEditor().getScrollingModel().scrollToCaret(ScrollType.CENTER);
      }
      return true;
    }

    private boolean doScrollToLine() {
      if (myScrollToLine == null) return false;
      ThreeSide side = myScrollToLine.first;
      Integer line = myScrollToLine.second;
      if (side.select(getEditors()) == null) return false;

      myCurrentSide = side;
      DiffUtil.scrollEditor(getCurrentEditor(), line);
      return true;
    }

    @NotNull
    private LogicalPosition getPosition(@Nullable Editor editor) {
      return editor != null ? editor.getCaretModel().getLogicalPosition() : new LogicalPosition(0, 0);
    }
  }

  private static class EditorsPosition {
    public static final Key<EditorsPosition> KEY = Key.create("Diff.EditorsPosition");

    @NotNull public final LogicalPosition[] myCaretPosition;
    @NotNull public final Point[] myPoints;

    public EditorsPosition(@NotNull LogicalPosition[] caretPosition, @NotNull Point[] points) {
      myCaretPosition = caretPosition;
      myPoints = points;
    }

    public boolean isSame(@Nullable LogicalPosition[] caretPosition) {
      // TODO: allow small fluctuations ?
      if (caretPosition == null) return true;
      if (caretPosition.length != 3) return false;
      if (!caretPosition[0].equals(myCaretPosition[0])) return false;
      if (!caretPosition[1].equals(myCaretPosition[1])) return false;
      if (!caretPosition[2].equals(myCaretPosition[2])) return false;
      return true;
    }
  }
}
