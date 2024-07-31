// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util.side;

import com.intellij.diff.DiffContext;
import com.intellij.diff.EditorDiffViewer;
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
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings;
import com.intellij.diff.tools.util.base.TextDiffViewerUtil;
import com.intellij.diff.tools.util.breadcrumbs.SimpleDiffBreadcrumbsPanel;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.LineCol;
import com.intellij.diff.util.Side;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.pom.Navigatable;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public abstract class TwosideTextDiffViewer extends TwosideDiffViewer<TextEditorHolder> implements EditorDiffViewer {
  @NotNull private final List<? extends EditorEx> myEditableEditors;
  @Nullable private List<? extends EditorEx> myEditors;

  @NotNull protected final SetEditorSettingsAction myEditorSettingsAction;

  @NotNull private final MyVisibleAreaListener myVisibleAreaListener = new MyVisibleAreaListener();

  @Nullable private TwosideSyncScrollSupport mySyncScrollSupport;

  public TwosideTextDiffViewer(@NotNull DiffContext context, @NotNull ContentDiffRequest request) {
    super(context, request, TextEditorHolder.TextEditorHolderFactory.INSTANCE);

    new MyFocusOppositePaneAction(true).install(myPanel);
    new MyFocusOppositePaneAction(false).install(myPanel);

    myEditorSettingsAction = new SetEditorSettingsAction(getTextSettings(), getEditors());
    myEditorSettingsAction.applyDefaults();

    new MyOpenInEditorWithMouseAction().install(getEditors());

    myEditableEditors = TextDiffViewerUtil.getEditableEditors(getEditors());

    TextDiffViewerUtil.checkDifferentDocuments(myRequest);

    boolean editable1 = DiffUtil.canMakeWritable(getContent1().getDocument());
    boolean editable2 = DiffUtil.canMakeWritable(getContent2().getDocument());
    if (editable1 ^ editable2) {
      ProxyUndoRedoAction.register(getProject(), editable1 ? getEditor1() : getEditor2(), myPanel);
    }

    for (Side side : Side.values()) {
      DiffUtil.installLineConvertor(getEditor(side), getContent(side));
    }

    if (getProject() != null) {
      for (Side side : Side.values()) {
        myContentPanel.setBreadcrumbs(side, new SimpleDiffBreadcrumbsPanel(getEditor(side), this), getTextSettings());
      }
    }
  }

  @Override
  @RequiresEdt
  protected void onInit() {
    super.onInit();
    installEditorListeners();
  }

  @Override
  @RequiresEdt
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

    for (TextEditorHolder holder : holders) {
      DiffUtil.disableBlitting(holder.getEditor());
    }

    return holders;
  }

  @NotNull
  @Override
  protected List<JComponent> createTitles() {
    return DiffUtil.createTextTitles(this, myRequest, getEditors());
  }

  //
  // Diff
  //

  @NotNull
  public TextDiffSettings getTextSettings() {
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

  @RequiresEdt
  protected void installEditorListeners() {
    new TextDiffViewerUtil.EditorActionsPopup(createEditorPopupActions()).install(getEditors(), myPanel);

    new TextDiffViewerUtil.EditorFontSizeSynchronizer(getEditors()).install(this);


    getEditor(Side.LEFT).getScrollingModel().addVisibleAreaListener(myVisibleAreaListener);
    getEditor(Side.RIGHT).getScrollingModel().addVisibleAreaListener(myVisibleAreaListener);

    SyncScrollSupport.SyncScrollable scrollable = getSyncScrollable();
    if (scrollable != null) {
      mySyncScrollSupport = new TwosideSyncScrollSupport(getEditors(), scrollable);
      myEditorSettingsAction.setSyncScrollSupport(mySyncScrollSupport);
    }
  }

  @RequiresEdt
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
  public List<? extends DocumentContent> getContents() {
    //noinspection unchecked,rawtypes
    return (List)myRequest.getContents();
  }

  @NotNull
  @Override
  public List<? extends EditorEx> getEditors() {
    if (myEditors == null) {
      myEditors = ContainerUtil.map(getEditorHolders(), holder -> holder.getEditor());
    }
    return myEditors;
  }

  @NotNull
  protected List<? extends EditorEx> getEditableEditors() {
    return myEditableEditors;
  }

  @NotNull
  @Override
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

  @RequiresEdt
  @NotNull
  public LineCol transferPosition(@NotNull Side baseSide, @NotNull LineCol position) {
    if (mySyncScrollSupport == null) return position;
    int line = mySyncScrollSupport.getScrollable().transfer(baseSide, position.line);
    return new LineCol(line, position.column);
  }

  @RequiresEdt
  protected void scrollToLine(@NotNull Side side, int line) {
    LineCol otherCol = transferPosition(side, new LineCol(line));
    DiffUtil.moveCaret(getEditor(side.other()), otherCol.line);

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
  public Navigatable getNavigatable() {
    Side side = getCurrentSide();

    LineCol position = LineCol.fromCaret(getEditor(side));
    Navigatable navigatable = getContent(side).getNavigatable(position);
    if (navigatable != null) return navigatable;

    LineCol otherPosition = transferPosition(side, position);
    return getContent(side.other()).getNavigatable(otherPosition);
  }

  public static boolean canShowRequest(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return TwosideDiffViewer.canShowRequest(context, request, TextEditorHolder.TextEditorHolderFactory.INSTANCE);
  }

  //
  // Actions
  //

  private class MyFocusOppositePaneAction extends FocusOppositePaneAction {
    MyFocusOppositePaneAction(boolean scrollToPosition) {
      super(scrollToPosition);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Side currentSide = getCurrentSide();
      Side targetSide = currentSide.other();

      EditorEx currentEditor = getEditor(currentSide);
      EditorEx targetEditor = getEditor(targetSide);

      if (myScrollToPosition) {
        LineCol position = transferPosition(currentSide, LineCol.fromCaret(currentEditor));
        targetEditor.getCaretModel().moveToOffset(position.toOffset(targetEditor));
      }

      setCurrentSide(targetSide);
      targetEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);

      DiffUtil.requestFocus(getProject(), getPreferredFocusedComponent());
    }
  }

  private class MyOpenInEditorWithMouseAction extends OpenInEditorWithMouseAction {
    @Override
    protected Navigatable getNavigatable(@NotNull Editor editor, int line) {
      Side side = Side.fromValue(getEditors(), editor);
      if (side == null) return null;

      return getContent(side).getNavigatable(new LineCol(line));
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

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    super.uiDataSnapshot(sink);
    sink.set(DiffDataKeys.CURRENT_EDITOR, getCurrentEditor());
  }

  private class MyVisibleAreaListener implements VisibleAreaListener {
    @Override
    public void visibleAreaChanged(@NotNull VisibleAreaEvent e) {
      if (mySyncScrollSupport != null) mySyncScrollSupport.visibleAreaChanged(e);
      myContentPanel.repaint();
    }
  }

  protected abstract class MyInitialScrollPositionHelper extends InitialScrollPositionSupport.TwosideInitialScrollHelper {
    private boolean myShouldUpdateCaretAfterRediff = false;

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
    protected boolean doScrollToLine(boolean onSlowRediff) {
      if (myScrollToLine == null) return false;

      scrollToLine(myScrollToLine.first, myScrollToLine.second);
      myShouldUpdateCaretAfterRediff = onSlowRediff; // move other caret to correct position when we know it

      return true;
    }

    @Override
    public void onRediff() {
      super.onRediff();

      if (myShouldUpdateCaretAfterRediff && myScrollToLine != null) {
        myShouldUpdateCaretAfterRediff = false;
        Side currentSide = getCurrentSide();
        Side side = myScrollToLine.first;
        int line = myScrollToLine.second;
        if (currentSide == side) {
          LineCol otherCol = transferPosition(side, new LineCol(line));
          DiffUtil.moveCaret(getEditor(side.other()), otherCol.line);
        }
      }
    }
  }
}
