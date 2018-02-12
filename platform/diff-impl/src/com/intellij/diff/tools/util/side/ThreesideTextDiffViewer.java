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
import com.intellij.diff.util.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.Navigatable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public abstract class ThreesideTextDiffViewer extends ThreesideDiffViewer<TextEditorHolder> {
  @Nullable private List<? extends EditorEx> myEditors;
  @NotNull private final List<? extends EditorEx> myEditableEditors;

  @NotNull private final MyVisibleAreaListener myVisibleAreaListener1 = new MyVisibleAreaListener(Side.LEFT);
  @NotNull private final MyVisibleAreaListener myVisibleAreaListener2 = new MyVisibleAreaListener(Side.RIGHT);
  @Nullable protected ThreesideSyncScrollSupport mySyncScrollSupport;

  @NotNull protected final SetEditorSettingsAction myEditorSettingsAction;

  public ThreesideTextDiffViewer(@NotNull DiffContext context, @NotNull ContentDiffRequest request) {
    super(context, request, TextEditorHolder.TextEditorHolderFactory.INSTANCE);

    //new MyFocusOppositePaneAction().setupAction(myPanel, this); // TODO

    myEditorSettingsAction = new SetEditorSettingsAction(getTextSettings(), getEditors());
    myEditorSettingsAction.applyDefaults();

    new MyOpenInEditorWithMouseAction().install(getEditors());

    myEditableEditors = TextDiffViewerUtil.getEditableEditors(getEditors());

    TextDiffViewerUtil.checkDifferentDocuments(myRequest);

    for (ThreeSide side : ThreeSide.values()) {
      DiffUtil.installLineConvertor(getEditor(side), getContent(side));
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
    for (int i = 0; i < 3; i++) {
      if (forceReadOnly[i]) holders.get(i).getEditor().setViewer(true);
    }

    ThreeSide.LEFT.select(holders).getEditor().setVerticalScrollbarOrientation(EditorEx.VERTICAL_SCROLLBAR_LEFT);

    for (TextEditorHolder holder : holders) {
      DiffUtil.disableBlitting(holder.getEditor());
    }

    return holders;
  }

  @NotNull
  @Override
  protected List<JComponent> createTitles() {
    return DiffUtil.createSyncHeightComponents(DiffUtil.createTextTitles(myRequest, getEditors()));
  }

  //
  // Listeners
  //

  @CalledInAwt
  protected void installEditorListeners() {
    new TextDiffViewerUtil.EditorActionsPopup(createEditorPopupActions()).install(getEditors());

    new TextDiffViewerUtil.EditorFontSizeSynchronizer(getEditors()).install(this);

    getEditor(ThreeSide.LEFT).getScrollingModel().addVisibleAreaListener(myVisibleAreaListener1);
    getEditor(ThreeSide.BASE).getScrollingModel().addVisibleAreaListener(myVisibleAreaListener1);

    getEditor(ThreeSide.BASE).getScrollingModel().addVisibleAreaListener(myVisibleAreaListener2);
    getEditor(ThreeSide.RIGHT).getScrollingModel().addVisibleAreaListener(myVisibleAreaListener2);

    SyncScrollSupport.SyncScrollable scrollable1 = getSyncScrollable(Side.LEFT);
    SyncScrollSupport.SyncScrollable scrollable2 = getSyncScrollable(Side.RIGHT);
    if (scrollable1 != null && scrollable2 != null) {
      mySyncScrollSupport = new ThreesideSyncScrollSupport(getEditors(), scrollable1, scrollable2);
      myEditorSettingsAction.setSyncScrollSupport(mySyncScrollSupport);
    }
  }

  @CalledInAwt
  public void destroyEditorListeners() {
    getEditor(ThreeSide.LEFT).getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener1);
    getEditor(ThreeSide.BASE).getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener1);

    getEditor(ThreeSide.BASE).getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener2);
    getEditor(ThreeSide.RIGHT).getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener2);

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
    myContentPanel.repaintDividers();
  }

  //
  // Getters
  //

  @NotNull
  public EditorEx getCurrentEditor() {
    return getEditor(getCurrentSide());
  }

  @NotNull
  public DocumentContent getCurrentContent() {
    return getContent(getCurrentSide());
  }

  @NotNull
  protected List<? extends DocumentContent> getContents() {
    //noinspection unchecked
    return (List)myRequest.getContents();
  }

  @NotNull
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
  public EditorEx getEditor(@NotNull ThreeSide side) {
    return side.select(getEditors());
  }

  @NotNull
  public DocumentContent getContent(@NotNull ThreeSide side) {
    return side.select(getContents());
  }

  @Nullable
  public ThreeSide getEditorSide(@Nullable Editor editor) {
    if (getEditor(ThreeSide.BASE) == editor) return ThreeSide.BASE;
    if (getEditor(ThreeSide.RIGHT) == editor) return ThreeSide.RIGHT;
    if (getEditor(ThreeSide.LEFT) == editor) return ThreeSide.LEFT;
    return null;
  }

  //
  // Abstract
  //

  @CalledInAwt
  protected void scrollToLine(@NotNull ThreeSide side, int line) {
    DiffUtil.scrollEditor(getEditor(side), line, false);
    setCurrentSide(side);
  }

  @Nullable
  protected abstract SyncScrollSupport.SyncScrollable getSyncScrollable(@NotNull Side side);

  @CalledInAwt
  @NotNull
  protected LogicalPosition transferPosition(@NotNull ThreeSide baseSide,
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

  @Nullable
  @Override
  protected Navigatable getNavigatable() {
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

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (DiffDataKeys.CURRENT_EDITOR.is(dataId)) {
      return getCurrentEditor();
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
      myContentPanel.repaint();
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

      scrollToLine(myScrollToLine.first, myScrollToLine.second);
      return true;
    }
  }

  protected class TextShowPartialDiffAction extends ShowPartialDiffAction {
    public TextShowPartialDiffAction(@NotNull PartialDiffMode mode, boolean hasFourSides) {
      super(mode, hasFourSides);
    }

    @NotNull
    @Override
    protected SimpleDiffRequest createRequest() {
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
}
