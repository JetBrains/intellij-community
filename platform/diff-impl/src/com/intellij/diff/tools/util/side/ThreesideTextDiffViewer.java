// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util.side;

import com.intellij.diff.DiffContext;
import com.intellij.diff.EditorDiffViewer;
import com.intellij.diff.actions.impl.FocusOppositePaneAction;
import com.intellij.diff.actions.impl.OpenInEditorWithMouseAction;
import com.intellij.diff.actions.impl.SetEditorSettingsAction;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.tools.holders.EditorHolderFactory;
import com.intellij.diff.tools.holders.TextEditorHolder;
import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.diff.tools.util.SyncScrollSupport;
import com.intellij.diff.tools.util.SyncScrollSupport.ThreesideSyncScrollSupport;
import com.intellij.diff.tools.util.base.InitialScrollPositionSupport;
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings;
import com.intellij.diff.tools.util.base.TextDiffViewerUtil;
import com.intellij.diff.tools.util.breadcrumbs.SimpleDiffBreadcrumbsPanel;
import com.intellij.diff.util.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.Navigatable;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public abstract class ThreesideTextDiffViewer extends ThreesideDiffViewer<TextEditorHolder> implements EditorDiffViewer {
  private @Nullable List<? extends EditorEx> myEditors;
  private final @NotNull List<? extends EditorEx> myEditableEditors;

  private final @NotNull MyVisibleAreaListener myVisibleAreaListener = new MyVisibleAreaListener();
  protected @Nullable ThreesideSyncScrollSupport mySyncScrollSupport;

  protected final @NotNull SetEditorSettingsAction myEditorSettingsAction;

  public ThreesideTextDiffViewer(@NotNull DiffContext context, @NotNull ContentDiffRequest request) {
    super(context, request, TextEditorHolder.TextEditorHolderFactory.INSTANCE);

    new MyFocusOppositePaneAction(true).install(myPanel);
    new MyFocusOppositePaneAction(false).install(myPanel);

    myEditorSettingsAction = new SetEditorSettingsAction(getTextSettings(), getEditors());
    myEditorSettingsAction.applyDefaults();

    new MyOpenInEditorWithMouseAction().install(getEditors());

    myEditableEditors = TextDiffViewerUtil.getEditableEditors(getEditors());

    TextDiffViewerUtil.checkDifferentDocuments(myRequest);

    for (ThreeSide side : ThreeSide.values()) {
      DiffUtil.installLineConvertor(getEditor(side), getContent(side));
    }

    if (getProject() != null) {
      for (ThreeSide side : ThreeSide.values()) {
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

  @Override
  protected @NotNull List<TextEditorHolder> createEditorHolders(@NotNull EditorHolderFactory<TextEditorHolder> factory) {
    List<TextEditorHolder> holders = super.createEditorHolders(factory);

    boolean[] forceReadOnly = TextDiffViewerUtil.checkForceReadOnly(myContext, myRequest);
    for (int i = 0; i < 3; i++) {
      if (forceReadOnly[i]) holders.get(i).getEditor().setViewer(true);
    }

    ThreeSide.LEFT.select(holders).getEditor().setVerticalScrollbarOrientation(EditorEx.VERTICAL_SCROLLBAR_LEFT);

    for (TextEditorHolder holder : holders) {
      DiffUtil.disableBlitting(holder.getEditor());
    }

    return holders;
  }

  @Override
  protected @NotNull List<JComponent> createTitles() {
    return DiffUtil.createTextTitles(this, myRequest, getEditors());
  }

  //
  // Listeners
  //

  @RequiresEdt
  protected void installEditorListeners() {
    new TextDiffViewerUtil.EditorActionsPopup(createEditorPopupActions()).install(getEditors(), myPanel);

    new TextDiffViewerUtil.EditorFontSizeSynchronizer(getEditors()).install(this);

    getEditor(ThreeSide.LEFT).getScrollingModel().addVisibleAreaListener(myVisibleAreaListener);
    getEditor(ThreeSide.BASE).getScrollingModel().addVisibleAreaListener(myVisibleAreaListener);
    getEditor(ThreeSide.RIGHT).getScrollingModel().addVisibleAreaListener(myVisibleAreaListener);

    SyncScrollSupport.SyncScrollable scrollable1 = getSyncScrollable(Side.LEFT);
    SyncScrollSupport.SyncScrollable scrollable2 = getSyncScrollable(Side.RIGHT);
    if (scrollable1 != null && scrollable2 != null) {
      mySyncScrollSupport = new ThreesideSyncScrollSupport(getEditors(), scrollable1, scrollable2);
      myEditorSettingsAction.setSyncScrollSupport(mySyncScrollSupport);
    }
  }

  @RequiresEdt
  public void destroyEditorListeners() {
    getEditor(ThreeSide.LEFT).getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener);
    getEditor(ThreeSide.BASE).getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener);
    getEditor(ThreeSide.RIGHT).getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener);

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
  // Diff
  //
  public @NotNull TextDiffSettings getTextSettings() {
    return TextDiffViewerUtil.getTextSettings(myContext);
  }

  protected @NotNull List<AnAction> createEditorPopupActions() {
    return TextDiffViewerUtil.createEditorPopupActions();
  }

  @Override
  protected void onDocumentChange(@NotNull DocumentEvent event) {
    super.onDocumentChange(event);
    myContentPanel.repaintDividers();
  }

  //
  // Getters
  //

  @Override
  public @NotNull EditorEx getCurrentEditor() {
    return getEditor(getCurrentSide());
  }

  public @NotNull DocumentContent getCurrentContent() {
    return getContent(getCurrentSide());
  }

  public @NotNull List<? extends DocumentContent> getContents() {
    //noinspection unchecked,rawtypes
    return (List)myRequest.getContents();
  }

  @Override
  public @NotNull List<? extends EditorEx> getEditors() {
    if (myEditors == null) {
      myEditors = ContainerUtil.map(getEditorHolders(), holder -> holder.getEditor());
    }
    return myEditors;
  }

  protected @NotNull List<? extends EditorEx> getEditableEditors() {
    return myEditableEditors;
  }

  public @NotNull EditorEx getEditor(@NotNull ThreeSide side) {
    return side.select(getEditors());
  }

  public @NotNull DocumentContent getContent(@NotNull ThreeSide side) {
    return side.select(getContents());
  }

  public @Nullable ThreeSide getEditorSide(@Nullable Editor editor) {
    if (getEditor(ThreeSide.BASE) == editor) return ThreeSide.BASE;
    if (getEditor(ThreeSide.RIGHT) == editor) return ThreeSide.RIGHT;
    if (getEditor(ThreeSide.LEFT) == editor) return ThreeSide.LEFT;
    return null;
  }

  //
  // Abstract
  //

  @RequiresEdt
  protected void scrollToLine(@NotNull ThreeSide side, int line) {
    DiffUtil.scrollEditor(getEditor(side), line, false);
    setCurrentSide(side);
  }

  protected abstract @Nullable SyncScrollSupport.SyncScrollable getSyncScrollable(@NotNull Side side);

  @RequiresEdt
  protected @NotNull LogicalPosition transferPosition(@NotNull ThreeSide baseSide,
                                                      @NotNull ThreeSide targetSide,
                                                      @NotNull LogicalPosition position) {
    if (mySyncScrollSupport == null) return position;
    if (baseSide == targetSide) return position;

    SyncScrollSupport.SyncScrollable scrollable12 = mySyncScrollSupport.getScrollable12();
    SyncScrollSupport.SyncScrollable scrollable23 = mySyncScrollSupport.getScrollable23();

    int baseLine; // line number in BASE
    if (baseSide == ThreeSide.LEFT) {
      baseLine = scrollable12.transfer(Side.LEFT, position.line);
    }
    else if (baseSide == ThreeSide.RIGHT) {
      baseLine = scrollable23.transfer(Side.RIGHT, position.line);
    }
    else {
      baseLine = position.line;
    }

    int targetLine;
    if (targetSide == ThreeSide.LEFT) {
      targetLine = scrollable12.transfer(Side.RIGHT, baseLine);
    }
    else if (targetSide == ThreeSide.RIGHT) {
      targetLine = scrollable23.transfer(Side.LEFT, baseLine);
    }
    else {
      targetLine = baseLine;
    }

    return new LogicalPosition(targetLine, position.column);
  }

  //
  // Misc
  //

  @Override
  public @Nullable Navigatable getNavigatable() {
    return getCurrentContent().getNavigatable(LineCol.fromCaret(getCurrentEditor()));
  }

  public static boolean canShowRequest(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return ThreesideDiffViewer.canShowRequest(context, request, TextEditorHolder.TextEditorHolderFactory.INSTANCE);
  }

  //
  // Actions
  //

  private class MyOpenInEditorWithMouseAction extends OpenInEditorWithMouseAction {
    @Override
    protected Navigatable getNavigatable(@NotNull Editor editor, int line) {
      ThreeSide side = getEditorSide(editor);
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

  @ApiStatus.Internal
  protected abstract class MyInitialScrollPositionHelper extends InitialScrollPositionSupport.ThreesideInitialScrollHelper {
    @Override
    protected @NotNull List<? extends Editor> getEditors() {
      return ThreesideTextDiffViewer.this.getEditors();
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

  @ApiStatus.Internal
  protected class TextShowPartialDiffAction extends ShowPartialDiffAction {
    public TextShowPartialDiffAction(@NotNull PartialDiffMode mode, boolean hasFourSides) {
      super(mode, hasFourSides);
    }

    @Override
    protected @NotNull SimpleDiffRequest createRequest() {
      SimpleDiffRequest request = super.createRequest();

      ThreeSide currentSide = getCurrentSide();
      LogicalPosition currentPosition = DiffUtil.getCaretPosition(getCurrentEditor());

      // we won't use DiffUserDataKeysEx.EDITORS_CARET_POSITION to avoid desync scroll position (as they can point to different places)
      // TODO: pass EditorsVisiblePositions in case if view was scrolled without changing caret position ?
      if (currentSide == mySide1) {
        request.putUserData(DiffUserDataKeys.SCROLL_TO_LINE, Pair.create(Side.LEFT, currentPosition.line));
      }
      else if (currentSide == mySide2) {
        request.putUserData(DiffUserDataKeys.SCROLL_TO_LINE, Pair.create(Side.RIGHT, currentPosition.line));
      }
      else {
        LogicalPosition position1 = transferPosition(currentSide, mySide1, currentPosition);
        LogicalPosition position2 = transferPosition(currentSide, mySide2, currentPosition);
        request.putUserData(DiffUserDataKeysEx.EDITORS_CARET_POSITION, new LogicalPosition[]{position1, position2});
      }

      return request;
    }
  }

  private class MyFocusOppositePaneAction extends FocusOppositePaneAction {
    MyFocusOppositePaneAction(boolean scrollToPosition) {
      super(scrollToPosition);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      ThreeSide currentSide = getCurrentSide();
      ThreeSide targetSide = currentSide.select(ThreeSide.BASE, ThreeSide.RIGHT, ThreeSide.LEFT); // cycle right

      EditorEx targetEditor = getEditor(targetSide);

      if (myScrollToPosition) {
        LogicalPosition currentPosition = DiffUtil.getCaretPosition(getCurrentEditor());
        LogicalPosition position = transferPosition(currentSide, targetSide, currentPosition);
        targetEditor.getCaretModel().moveToLogicalPosition(position);
      }

      setCurrentSide(targetSide);
      targetEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);

      DiffUtil.requestFocus(getProject(), getPreferredFocusedComponent());
    }
  }
}
