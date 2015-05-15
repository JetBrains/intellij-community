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
package com.intellij.diff.tools.util.twoside;

import com.intellij.diff.DiffContext;
import com.intellij.diff.actions.impl.FocusOppositePaneAction;
import com.intellij.diff.actions.impl.OpenInEditorWithMouseAction;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.contents.EmptyContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.diff.tools.util.SimpleDiffPanel;
import com.intellij.diff.tools.util.SyncScrollSupport;
import com.intellij.diff.tools.util.SyncScrollSupport.TwosideSyncScrollSupport;
import com.intellij.diff.tools.util.base.InitialScrollPositionSupport;
import com.intellij.diff.tools.util.base.TextDiffViewerBase;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Side;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Collections;
import java.util.List;

public abstract class TwosideTextDiffViewer extends TextDiffViewerBase {
  public static final Logger LOG = Logger.getInstance(TwosideTextDiffViewer.class);

  @NotNull private final EditorFactory myEditorFactory = EditorFactory.getInstance();

  @NotNull protected final SimpleDiffPanel myPanel;
  @NotNull protected final TwosideTextContentPanel myContentPanel;

  @Nullable protected final EditorEx myEditor1;
  @Nullable protected final EditorEx myEditor2;

  @Nullable protected final DocumentContent myActualContent1;
  @Nullable protected final DocumentContent myActualContent2;

  @NotNull protected final MySetEditorSettingsAction myEditorSettingsAction;

  @NotNull private final MyEditorFocusListener myEditorFocusListener1 = new MyEditorFocusListener(Side.LEFT);
  @NotNull private final MyEditorFocusListener myEditorFocusListener2 = new MyEditorFocusListener(Side.RIGHT);
  @NotNull private final MyVisibleAreaListener myVisibleAreaListener = new MyVisibleAreaListener();

  @Nullable protected TwosideSyncScrollSupport mySyncScrollSupport;

  @NotNull private Side myCurrentSide;

  public TwosideTextDiffViewer(@NotNull DiffContext context, @NotNull ContentDiffRequest request) {
    super(context, request);

    List<DiffContent> contents = myRequest.getContents();
    myActualContent1 = contents.get(0) instanceof DocumentContent ? ((DocumentContent)contents.get(0)) : null;
    myActualContent2 = contents.get(1) instanceof DocumentContent ? ((DocumentContent)contents.get(1)) : null;
    assert myActualContent1 != null || myActualContent2 != null;


    List<EditorEx> editors = createEditors();
    List<JComponent> titlePanel = DiffUtil.createTextTitles(myRequest, editors);

    myEditor1 = editors.get(0);
    myEditor2 = editors.get(1);
    assert myEditor1 != null || myEditor2 != null;

    myCurrentSide = myEditor1 == null ? Side.RIGHT : Side.LEFT;

    myContentPanel = new TwosideTextContentPanel(titlePanel, myEditor1, myEditor2);

    myPanel = new SimpleDiffPanel(myContentPanel, this, context);


    new MyFocusOppositePaneAction(true).setupAction(myPanel);
    new MyFocusOppositePaneAction(false).setupAction(myPanel);

    myEditorSettingsAction = new MySetEditorSettingsAction();
    myEditorSettingsAction.applyDefaults();

    new MyOpenInEditorWithMouseAction().register(getEditors());
  }

  @Override
  @CalledInAwt
  protected void onInit() {
    super.onInit();
    processContextHints();
  }

  @Override
  @CalledInAwt
  protected void onDispose() {
    updateContextHints();
    super.onDispose();
    destroyEditors();
  }

  protected void processContextHints() {
    if (myEditor1 == null) {
      myCurrentSide = Side.RIGHT;
    }
    else if (myEditor2 == null) {
      myCurrentSide = Side.LEFT;
    }
    else {
      Side side = myContext.getUserData(DiffUserDataKeys.PREFERRED_FOCUS_SIDE);
      if (side != null) myCurrentSide = side;
    }
  }

  protected void updateContextHints() {
    if (myEditor1 != null && myEditor2 != null) {
      myContext.putUserData(DiffUserDataKeys.PREFERRED_FOCUS_SIDE, myCurrentSide);
    }
  }

  @NotNull
  protected List<EditorEx> createEditors() {
    boolean[] forceReadOnly = checkForceReadOnly();

    // TODO: we may want to set editor highlighter in init() to speedup editor initialization
    EditorEx editor1 = null;
    EditorEx editor2 = null;
    if (myActualContent1 != null) {
      editor1 = DiffUtil.createEditor(myActualContent1.getDocument(), myProject, forceReadOnly[0], true);
      DiffUtil.configureEditor(editor1, myActualContent1, myProject);
    }
    if (myActualContent2 != null) {
      editor2 = DiffUtil.createEditor(myActualContent2.getDocument(), myProject, forceReadOnly[1], true);
      DiffUtil.configureEditor(editor2, myActualContent2, myProject);
    }
    if (editor1 != null && editor2 != null) {
      editor1.setVerticalScrollbarOrientation(EditorEx.VERTICAL_SCROLLBAR_LEFT);
    }
    if (Registry.is("diff.divider.repainting.disable.blitting")) {
      if (editor1 != null) editor1.getScrollPane().getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
      if (editor2 != null) editor2.getScrollPane().getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
    }

    return ContainerUtil.newArrayList(editor1, editor2);
  }

  //
  // Diff
  //

  @Override
  protected void onDocumentChange(@NotNull DocumentEvent event) {
    super.onDocumentChange(event);
    myContentPanel.repaintDivider();
  }

  //
  // Listeners
  //

  private void destroyEditors() {
    if (myEditor1 != null) myEditorFactory.releaseEditor(myEditor1);
    if (myEditor2 != null) myEditorFactory.releaseEditor(myEditor2);
  }

  @CalledInAwt
  @Override
  protected void installEditorListeners() {
    super.installEditorListeners();
    if (myEditor1 != null) {
      myEditor1.getContentComponent().addFocusListener(myEditorFocusListener1);
      myEditor1.getScrollingModel().addVisibleAreaListener(myVisibleAreaListener);
    }
    if (myEditor2 != null) {
      myEditor2.getContentComponent().addFocusListener(myEditorFocusListener2);
      myEditor2.getScrollingModel().addVisibleAreaListener(myVisibleAreaListener);
    }
    if (myEditor1 != null && myEditor2 != null) {
      SyncScrollSupport.SyncScrollable scrollable = getSyncScrollable();
      if (scrollable != null) {
        mySyncScrollSupport = new TwosideSyncScrollSupport(myEditor1, myEditor2, scrollable);
      }
    }
  }

  @CalledInAwt
  @Override
  protected void destroyEditorListeners() {
    super.destroyEditorListeners();
    if (myEditor1 != null) {
      myEditor1.getContentComponent().removeFocusListener(myEditorFocusListener1);
      myEditor1.getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener);
    }
    if (myEditor2 != null) {
      myEditor2.getContentComponent().removeFocusListener(myEditorFocusListener2);
      myEditor2.getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener);
    }
    mySyncScrollSupport = null;
  }

  protected void disableSyncScrollSupport(boolean disable) {
    if (mySyncScrollSupport != null) {
      mySyncScrollSupport.setDisabled(disable);
    }
  }

  //
  // Getters
  //

  @NotNull
  @Override
  protected List<? extends EditorEx> getEditors() {
    if (myEditor1 != null && myEditor2 != null) {
      return ContainerUtil.list(myEditor1, myEditor2);
    }
    if (myEditor1 != null) {
      return Collections.singletonList(myEditor1);
    }
    if (myEditor2 != null) {
      return Collections.singletonList(myEditor2);
    }
    return Collections.emptyList();
  }

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
  public Side getCurrentSide() {
    return myCurrentSide;
  }

  @NotNull
  public EditorEx getCurrentEditor() {
    //noinspection ConstantConditions
    return getCurrentSide().select(myEditor1, myEditor2);
  }

  @NotNull
  public DocumentContent getCurrentContent() {
    //noinspection ConstantConditions
    return getCurrentSide().select(myActualContent1, myActualContent2);
  }

  @Nullable
  protected EditorEx getEditor1() {
    return myEditor1;
  }

  @Nullable
  protected EditorEx getEditor2() {
    return myEditor2;
  }

  //
  // Abstract
  //

  @CalledInAwt
  @NotNull
  protected LogicalPosition transferPosition(@NotNull Side baseSide, @NotNull LogicalPosition position) {
    if (mySyncScrollSupport == null) return position;
    int line = mySyncScrollSupport.getScrollable().transfer(baseSide, position.line);
    return new LogicalPosition(line, position.column);
  }

  @CalledInAwt
  protected void scrollToLine(@NotNull Side side, int line) {
    Editor editor = side.select(myEditor1, myEditor2);
    if (editor == null) return;
    DiffUtil.scrollEditor(editor, line, false);
    myCurrentSide = side;
  }

  @Nullable
  protected abstract SyncScrollSupport.SyncScrollable getSyncScrollable();

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
    if (contents.size() != 2) return false;

    boolean canShow = true;
    boolean wantShow = false;
    for (DiffContent content : contents) {
      canShow &= canShowContent(content);
      wantShow |= wantShowContent(content);
    }
    return canShow && wantShow;
  }

  public static boolean canShowContent(@NotNull DiffContent content) {
    if (content instanceof EmptyContent) return true;
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

  private class MyFocusOppositePaneAction extends FocusOppositePaneAction {
    public MyFocusOppositePaneAction(boolean scrollToPosition) {
      super(scrollToPosition);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      if (myEditor1 == null || myEditor2 == null) return;

      if (myScrollToPosition) {
        EditorEx currentEditor = myCurrentSide.select(myEditor1, myEditor2);
        EditorEx targetEditor = myCurrentSide.other().select(myEditor1, myEditor2);
        LogicalPosition position = transferPosition(myCurrentSide, currentEditor.getCaretModel().getLogicalPosition());
        targetEditor.getCaretModel().moveToLogicalPosition(position);
      }

      myCurrentSide = myCurrentSide.other();
      myPanel.requestFocus();
      getCurrentEditor().getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    }
  }

  private class MyOpenInEditorWithMouseAction extends OpenInEditorWithMouseAction {
    @Override
    protected OpenFileDescriptor getDescriptor(@NotNull Editor editor, int line) {
      if (editor != myEditor1 && editor != myEditor2) return null;
      Side side = Side.fromLeft(editor == myEditor1);

      DocumentContent content = side.select(myActualContent1, myActualContent2);
      if (content == null) return null;

      int offset = editor.logicalPositionToOffset(new LogicalPosition(line, 0));

      return content.getOpenFileDescriptor(offset);
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
    else if (DiffDataKeys.CURRENT_CONTENT.is(dataId)) {
      return getCurrentContent();
    }
    else if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
      return DiffUtil.getVirtualFile(myRequest, myCurrentSide);
    }

    return super.getData(dataId);
  }

  private class MyEditorFocusListener extends FocusAdapter {
    @NotNull private final Side mySide;

    private MyEditorFocusListener(@NotNull Side side) {
      mySide = side;
    }

    public void focusGained(FocusEvent e) {
      if (myEditor1 == null || myEditor2 == null) return;
      myCurrentSide = mySide;
    }
  }

  private class MyVisibleAreaListener implements VisibleAreaListener {
    @Override
    public void visibleAreaChanged(VisibleAreaEvent e) {
      if (mySyncScrollSupport != null) mySyncScrollSupport.visibleAreaChanged(e);
      if (Registry.is("diff.divider.repainting.fix")) {
        myContentPanel.repaint();
      }
      else {
        myContentPanel.repaintDivider();
      }
    }
  }

  protected abstract class MyInitialScrollPositionHelper extends InitialScrollPositionSupport.TwosideInitialScrollHelper {
    @NotNull
    @Override
    protected List<? extends Editor> getEditors() {
      return TwosideTextDiffViewer.this.getEditors();
    }

    @Override
    protected void disableSyncScroll(boolean value) {
      disableSyncScrollSupport(value);
    }

    @Override
    protected boolean doScrollToLine() {
      if (myScrollToLine == null) return false;
      Side side = myScrollToLine.first;
      Integer line = myScrollToLine.second;
      if (side.select(getEditors()) == null) return false;

      scrollToLine(side, line);
      return true;
    }
  }
}
