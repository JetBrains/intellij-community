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
  private List<? extends EditorEx> myEditors;

  @NotNull protected final SetEditorSettingsAction myEditorSettingsAction;

  @NotNull private final MyVisibleAreaListener myVisibleAreaListener = new MyVisibleAreaListener();

  @Nullable protected TwosideSyncScrollSupport mySyncScrollSupport;

  public TwosideTextDiffViewer(@NotNull DiffContext context, @NotNull ContentDiffRequest request) {
    super(context, request, TextEditorHolder.TextEditorHolderFactory.INSTANCE);

    new MyFocusOppositePaneAction(true).setupAction(myPanel);
    new MyFocusOppositePaneAction(false).setupAction(myPanel);

    myEditorSettingsAction = new SetEditorSettingsAction(getTextSettings(), getEditors());
    myEditorSettingsAction.applyDefaults();

    new MyOpenInEditorWithMouseAction().register(getEditors());

    myEditableEditors = TextDiffViewerUtil.getEditableEditors(getEditors());
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
    TextEditorHolder holder1 = holders.get(0);
    TextEditorHolder holder2 = holders.get(1);

    if (holder1 != null && forceReadOnly[0]) holder1.getEditor().setViewer(true);
    if (holder2 != null && forceReadOnly[1]) holder2.getEditor().setViewer(true);

    if (holder1 != null && holder2 != null) {
      holder1.getEditor().setVerticalScrollbarOrientation(EditorEx.VERTICAL_SCROLLBAR_LEFT);

      if (Registry.is("diff.divider.repainting.disable.blitting")) {
        holder1.getEditor().getScrollPane().getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        holder2.getEditor().getScrollPane().getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
      }
    }

    return holders;
  }

  @NotNull
  @Override
  protected List<JComponent> createTitles() {
    return DiffUtil.createTextTitles(myRequest, getEditors());
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


    if (getEditor1() != null) {
      getEditor1().getScrollingModel().addVisibleAreaListener(myVisibleAreaListener);
    }
    if (getEditor2() != null) {
      getEditor2().getScrollingModel().addVisibleAreaListener(myVisibleAreaListener);
    }
    if (getEditor1() != null && getEditor2() != null) {
      SyncScrollSupport.SyncScrollable scrollable = getSyncScrollable();
      if (scrollable != null) {
        mySyncScrollSupport = new TwosideSyncScrollSupport(getEditor1(), getEditor2(), scrollable);
      }
    }
  }

  @CalledInAwt
  protected void destroyEditorListeners() {
    if (getEditor1() != null) {
      getEditor1().getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener);
    }
    if (getEditor2() != null) {
      getEditor2().getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener);
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
  protected List<DocumentContent> getActualContents() {
    //noinspection unchecked
    return (List<DocumentContent>)super.getActualContents();
  }

  @NotNull
  protected List<? extends EditorEx> getEditors() {
    if (myEditors == null) {
      myEditors = ContainerUtil.map(getEditorHolders(), new Function<TextEditorHolder, EditorEx>() {
        @Override
        public EditorEx fun(TextEditorHolder holder) {
          return holder != null ? holder.getEditor() : null;
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
    //noinspection ConstantConditions
    return getEditor(getCurrentSide());
  }

  @NotNull
  public DocumentContent getCurrentContent() {
    //noinspection ConstantConditions
    return getActualContent(getCurrentSide());
  }

  @Nullable
  protected EditorEx getEditor1() {
    return getEditors().get(0);
  }

  @Nullable
  protected EditorEx getEditor2() {
    return getEditors().get(1);
  }

  @Nullable
  protected EditorEx getEditor(@NotNull Side side) {
    return side.select(getEditor1(), getEditor2());
  }

  @Nullable
  public DocumentContent getActualContent1() {
    return getActualContent(Side.LEFT);
  }

  @Nullable
  public DocumentContent getActualContent2() {
    return getActualContent(Side.RIGHT);
  }

  @Nullable
  protected DocumentContent getActualContent(@NotNull Side side) {
    return side.select(getActualContents());
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
    Editor editor = getEditor(side);
    if (editor == null) return;
    DiffUtil.scrollEditor(editor, line, false);
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
    EditorEx editor = getCurrentEditor();

    int offset = editor.getCaretModel().getOffset();
    return getCurrentContent().getOpenFileDescriptor(offset);
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
      if (getEditor1() == null || getEditor2() == null) return;

      if (myScrollToPosition) {
        EditorEx currentEditor = getCurrentSide().select(getEditor1(), getEditor2());
        EditorEx targetEditor = getCurrentSide().other().select(getEditor1(), getEditor2());
        LogicalPosition position = transferPosition(getCurrentSide(), currentEditor.getCaretModel().getLogicalPosition());
        targetEditor.getCaretModel().moveToLogicalPosition(position);
      }

      setCurrentSide(getCurrentSide().other());
      myContext.requestFocus();
      getCurrentEditor().getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    }
  }

  private class MyOpenInEditorWithMouseAction extends OpenInEditorWithMouseAction {
    @Override
    protected OpenFileDescriptor getDescriptor(@NotNull Editor editor, int line) {
      if (editor != getEditor1() && editor != getEditor2()) return null;
      Side side = Side.fromLeft(editor == getEditor1());

      DocumentContent content = getActualContent(side);
      if (content == null) return null;

      int offset = editor.logicalPositionToOffset(new LogicalPosition(line, 0));

      return content.getOpenFileDescriptor(offset);
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
      Side side = myScrollToLine.first;
      Integer line = myScrollToLine.second;
      if (getEditor(side) == null) return false;

      scrollToLine(side, line);
      return true;
    }
  }
}
