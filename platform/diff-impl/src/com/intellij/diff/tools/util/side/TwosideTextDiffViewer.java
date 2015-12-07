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
import com.intellij.diff.actions.ProxyUndoRedoAction;
import com.intellij.diff.actions.impl.FocusOppositePaneAction;
import com.intellij.diff.actions.impl.OpenInEditorWithMouseAction;
import com.intellij.diff.actions.impl.SetEditorSettingsAction;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.holders.EditorHolderFactory;
import com.intellij.diff.tools.holders.TextEditorHolder;
import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.diff.tools.util.SyncScrollSupport;
import com.intellij.diff.tools.util.SyncScrollSupport.TwosideSyncScrollSupport;
import com.intellij.diff.tools.util.base.InitialScrollPositionSupport;
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder;
import com.intellij.diff.tools.util.base.TextDiffViewerUtil;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Side;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public abstract class TwosideTextDiffViewer extends TwosideDiffViewer<TextEditorHolder> {
  public static final Logger LOG = Logger.getInstance(TwosideTextDiffViewer.class);

  @NotNull private final List<? extends EditorEx> myEditableEditors;
  @Nullable private List<? extends EditorEx> myEditors;

  @NotNull protected final SetEditorSettingsAction myEditorSettingsAction;

  @NotNull private final MyVisibleAreaListener myVisibleAreaListener = new MyVisibleAreaListener();

  @Nullable private TwosideSyncScrollSupport mySyncScrollSupport;

  public TwosideTextDiffViewer(@NotNull DiffContext context, @NotNull ContentDiffRequest request) {
    super(context, request, TextEditorHolder.TextEditorHolderFactory.INSTANCE);

    new MyFocusOppositePaneAction(true).setupAction(myPanel);
    new MyFocusOppositePaneAction(false).setupAction(myPanel);

    myEditorSettingsAction = new SetEditorSettingsAction(getTextSettings(), getEditors());
    myEditorSettingsAction.applyDefaults();

    new MyOpenInEditorWithMouseAction().register(getEditors());

    myEditableEditors = TextDiffViewerUtil.getEditableEditors(getEditors());

    TextDiffViewerUtil.checkDifferentDocuments(myRequest);

    boolean editable1 = DiffUtil.canMakeWritable(getContent1().getDocument());
    boolean editable2 = DiffUtil.canMakeWritable(getContent2().getDocument());
    if (editable1 ^ editable2) {
      ProxyUndoRedoAction.register(getProject(), editable1 ? getEditor1() : getEditor2(), myPanel);
    }
  }

  @Override
  @CalledInAwt
  protected void onInit() {
    super.onInit();
    installEditorListeners();
  }

  @Override
  @CalledInAwt
  protected void onDispose() {
    destroyEditorListeners();
    super.onDispose();
  }

  @NotNull
  @Override
  protected List<TextEditorHolder> createEditorHolders(@NotNull EditorHolderFactory<TextEditorHolder> factory) {
    List<TextEditorHolder> holders = super.createEditorHolders(factory);

    boolean[] forceReadOnly = TextDiffViewerUtil.checkForceReadOnly(myContext, myRequest);
    for (int i = 0; i < 2; i++) {
      if (forceReadOnly[i]) holders.get(i).getEditor().setViewer(true);
    }

    Side.LEFT.select(holders).getEditor().setVerticalScrollbarOrientation(EditorEx.VERTICAL_SCROLLBAR_LEFT);

    if (Registry.is("diff.divider.repainting.disable.blitting")) {
      for (TextEditorHolder holder : holders) {
        holder.getEditor().getScrollPane().getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
      }
    }

    return holders;
  }

  @NotNull
  @Override
  protected List<JComponent> createTitles() {
    return DiffUtil.createSyncHeightComponents(DiffUtil.createTextTitles(myRequest, getEditors()));
  }

  //
  // Diff
  //

  @NotNull
  public TextDiffSettingsHolder.TextDiffSettings getTextSettings() {
    return TextDiffViewerUtil.getTextSettings(myContext);
  }

  @NotNull
  protected List<AnAction> createEditorPopupActions() {
    return TextDiffViewerUtil.createEditorPopupActions();
  }

  @Override
  protected void onDocumentChange(@NotNull DocumentEvent event) {
    super.onDocumentChange(event);
    myContentPanel.repaintDivider();
  }

  //
  // Listeners
  //

  @CalledInAwt
  protected void installEditorListeners() {
    new TextDiffViewerUtil.EditorActionsPopup(createEditorPopupActions()).install(getEditors());

    new TextDiffViewerUtil.EditorFontSizeSynchronizer(getEditors()).install(this);


    getEditor(Side.LEFT).getScrollingModel().addVisibleAreaListener(myVisibleAreaListener);
    getEditor(Side.RIGHT).getScrollingModel().addVisibleAreaListener(myVisibleAreaListener);

    SyncScrollSupport.SyncScrollable scrollable = getSyncScrollable();
    if (scrollable != null) {
      mySyncScrollSupport = new TwosideSyncScrollSupport(getEditors(), scrollable);
    }
  }

  @CalledInAwt
  protected void destroyEditorListeners() {
    getEditor(Side.LEFT).getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener);
    getEditor(Side.RIGHT).getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener);

    mySyncScrollSupport = null;
  }

  protected void disableSyncScrollSupport(boolean disable) {
    if (mySyncScrollSupport != null) {
      if (disable) {
        mySyncScrollSupport.enterDisableScrollSection();
      }
      else {
        mySyncScrollSupport.exitDisableScrollSection();
      }
    }
  }

  //
  // Getters
  //


  @NotNull
  protected List<? extends DocumentContent> getContents() {
    //noinspection unchecked
    return (List)myRequest.getContents();
  }

  @NotNull
  public List<? extends EditorEx> getEditors() {
    if (myEditors == null) {
      myEditors = ContainerUtil.map(getEditorHolders(), new Function<TextEditorHolder, EditorEx>() {
        @Override
        public EditorEx fun(TextEditorHolder holder) {
          return holder.getEditor();
        }
      });
    }
    return myEditors;
  }

  @NotNull
  protected List<? extends EditorEx> getEditableEditors() {
    return myEditableEditors;
  }

  @NotNull
  public EditorEx getCurrentEditor() {
    return getEditor(getCurrentSide());
  }

  @NotNull
  public DocumentContent getCurrentContent() {
    return getContent(getCurrentSide());
  }

  @NotNull
  public EditorEx getEditor1() {
    return getEditor(Side.LEFT);
  }

  @NotNull
  public EditorEx getEditor2() {
    return getEditor(Side.RIGHT);
  }


  @NotNull
  public EditorEx getEditor(@NotNull Side side) {
    return side.select(getEditors());
  }

  @NotNull
  public DocumentContent getContent(@NotNull Side side) {
    return side.select(getContents());
  }

  @NotNull
  public DocumentContent getContent1() {
    return getContent(Side.LEFT);
  }

  @NotNull
  public DocumentContent getContent2() {
    return getContent(Side.RIGHT);
  }

  @Nullable
  public TwosideSyncScrollSupport getSyncScrollSupport() {
    return mySyncScrollSupport;
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
    DiffUtil.scrollEditor(getEditor(side), line, false);
    setCurrentSide(side);
  }

  @Nullable
  protected abstract SyncScrollSupport.SyncScrollable getSyncScrollable();

  //
  // Misc
  //

  @Nullable
  @Override
  protected OpenFileDescriptor getOpenFileDescriptor() {
    Side side = getCurrentSide();
    int offset = getEditor(side).getCaretModel().getOffset();
    OpenFileDescriptor descriptor = getContent(side).getOpenFileDescriptor(offset);
    if (descriptor != null) return descriptor;

    LogicalPosition otherPosition = transferPosition(side, getEditor(side).getCaretModel().getLogicalPosition());
    int otherOffset = getEditor(side.other()).logicalPositionToOffset(otherPosition);
    return getContent(side.other()).getOpenFileDescriptor(otherOffset);
  }

  public static boolean canShowRequest(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return TwosideDiffViewer.canShowRequest(context, request, TextEditorHolder.TextEditorHolderFactory.INSTANCE);
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
      Side currentSide = getCurrentSide();
      Side targetSide = currentSide.other();

      EditorEx currentEditor = getEditor(currentSide);
      EditorEx targetEditor = getEditor(targetSide);

      if (myScrollToPosition) {
        LogicalPosition position = transferPosition(currentSide, currentEditor.getCaretModel().getLogicalPosition());
        targetEditor.getCaretModel().moveToLogicalPosition(position);
      }

      setCurrentSide(targetSide);
      currentEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);

      DiffUtil.requestFocus(getProject(), getPreferredFocusedComponent());
    }
  }

  private class MyOpenInEditorWithMouseAction extends OpenInEditorWithMouseAction {
    @Override
    protected OpenFileDescriptor getDescriptor(@NotNull Editor editor, int line) {
      Side side = null;
      if (editor == getEditor(Side.LEFT)) side = Side.LEFT;
      if (editor == getEditor(Side.RIGHT)) side = Side.RIGHT;
      if (side == null) return null;

      int offset = editor.logicalPositionToOffset(new LogicalPosition(line, 0));
      return getContent(side).getOpenFileDescriptor(offset);
    }
  }

  protected class MyToggleAutoScrollAction extends TextDiffViewerUtil.ToggleAutoScrollAction {
    public MyToggleAutoScrollAction() {
      super(getTextSettings());
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
    return super.getData(dataId);
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

      scrollToLine(myScrollToLine.first, myScrollToLine.second);
      return true;
    }
  }
}
