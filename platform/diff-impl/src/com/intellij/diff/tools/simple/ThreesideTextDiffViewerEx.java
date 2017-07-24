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
package com.intellij.diff.tools.simple;

import com.intellij.diff.DiffContext;
import com.intellij.diff.actions.impl.NextConflictAction;
import com.intellij.diff.actions.impl.PrevConflictAction;
import com.intellij.diff.fragments.MergeLineFragment;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.tools.util.*;
import com.intellij.diff.tools.util.base.TextDiffViewerUtil;
import com.intellij.diff.tools.util.side.ThreesideTextDiffViewer;
import com.intellij.diff.util.*;
import com.intellij.diff.util.DiffDividerDrawUtil.DividerPaintable;
import com.intellij.diff.util.DiffUserDataKeysEx.ScrollToPolicy;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.intellij.diff.util.DiffUtil.getLineCount;
import static com.intellij.util.ArrayUtil.toObjectArray;

public abstract class ThreesideTextDiffViewerEx extends ThreesideTextDiffViewer {
  @NotNull private final SyncScrollSupport.SyncScrollable mySyncScrollable1;
  @NotNull private final SyncScrollSupport.SyncScrollable mySyncScrollable2;

  @NotNull private final PrevNextDifferenceIterable myPrevNextDifferenceIterable;
  @NotNull private final PrevNextDifferenceIterable myPrevNextConflictIterable;
  @NotNull protected final MyStatusPanel myStatusPanel;

  @NotNull protected final MyFoldingModel myFoldingModel;
  @NotNull protected final MyInitialScrollHelper myInitialScrollHelper = new MyInitialScrollHelper();

  private int myChangesCount = -1;
  private int myConflictsCount = -1;

  public ThreesideTextDiffViewerEx(@NotNull DiffContext context, @NotNull ContentDiffRequest request) {
    super(context, request);

    mySyncScrollable1 = new MySyncScrollable(Side.LEFT);
    mySyncScrollable2 = new MySyncScrollable(Side.RIGHT);
    myPrevNextDifferenceIterable = new MyPrevNextDifferenceIterable();
    myPrevNextConflictIterable = new MyPrevNextConflictIterable();
    myStatusPanel = new MyStatusPanel();
    myFoldingModel = new MyFoldingModel(toObjectArray(getEditors(), EditorEx.class), this);

    DiffUtil.registerAction(ActionManager.getInstance().getAction(IdeActions.ACTION_PREVIOUS_CONFLICT), myPanel);
    DiffUtil.registerAction(ActionManager.getInstance().getAction(IdeActions.ACTION_NEXT_CONFLICT), myPanel);
  }

  @Override
  @CalledInAwt
  protected void onInit() {
    super.onInit();
    myContentPanel.setPainter(new MyDividerPainter(Side.LEFT), Side.LEFT);
    myContentPanel.setPainter(new MyDividerPainter(Side.RIGHT), Side.RIGHT);
  }

  @Override
  @CalledInAwt
  protected void onDispose() {
    destroyChangedBlocks();
    myFoldingModel.destroy();
    super.onDispose();
  }

  @Override
  @CalledInAwt
  protected void processContextHints() {
    super.processContextHints();
    myInitialScrollHelper.processContext(myRequest);
  }

  @Override
  @CalledInAwt
  protected void updateContextHints() {
    super.updateContextHints();
    myFoldingModel.updateContext(myRequest, getFoldingModelSettings());
    myInitialScrollHelper.updateContext(myRequest);
  }

  @NotNull
  @Override
  protected List<AnAction> createEditorPopupActions() {
    List<AnAction> group = new ArrayList<>();

    DefaultActionGroup navigateGroup = new DefaultActionGroup("Navigate", true);
    navigateGroup.add(ActionManager.getInstance().getAction(IdeActions.ACTION_PREVIOUS_DIFF));
    navigateGroup.add(ActionManager.getInstance().getAction(IdeActions.ACTION_NEXT_DIFF));
    navigateGroup.add(ActionManager.getInstance().getAction(IdeActions.ACTION_PREVIOUS_CONFLICT));
    navigateGroup.add(ActionManager.getInstance().getAction(IdeActions.ACTION_NEXT_CONFLICT));
    group.add(navigateGroup);

    group.add(Separator.getInstance());
    group.addAll(super.createEditorPopupActions());

    return group;
  }

  //
  // Diff
  //

  @NotNull
  public FoldingModelSupport.Settings getFoldingModelSettings() {
    return TextDiffViewerUtil.getFoldingModelSettings(myContext);
  }

  @NotNull
  protected Runnable applyNotification(@Nullable final JComponent notification) {
    return () -> {
      clearDiffPresentation();
      myFoldingModel.destroy();
      if (notification != null) myPanel.addNotification(notification);
    };
  }

  @CalledInAwt
  protected void clearDiffPresentation() {
    myStatusPanel.setBusy(false);
    myPanel.resetNotifications();
    destroyChangedBlocks();

    myContentPanel.repaintDividers();
    myStatusPanel.update();
  }

  @CalledInAwt
  protected void destroyChangedBlocks() {
  }

  //
  // Impl
  //

  @CalledInAwt
  protected boolean doScrollToChange(@NotNull ScrollToPolicy scrollToPolicy) {
    ThreesideDiffChangeBase targetChange = scrollToPolicy.select(getChanges());
    if (targetChange == null) return false;

    doScrollToChange(targetChange, false);
    return true;
  }

  protected void doScrollToChange(@NotNull ThreesideDiffChangeBase change, boolean animated) {
    int[] startLines = new int[3];
    int[] endLines = new int[3];

    for (int i = 0; i < 3; i++) {
      ThreeSide side = ThreeSide.fromIndex(i);
      startLines[i] = change.getStartLine(side);
      endLines[i] = change.getEndLine(side);
      DiffUtil.moveCaret(getEditor(side), startLines[i]);
    }

    getSyncScrollSupport().makeVisible(getCurrentSide(), startLines, endLines, animated);
  }

  //
  // Counters
  //

  public int getChangesCount() {
    return myChangesCount;
  }

  public int getConflictsCount() {
    return myConflictsCount;
  }

  protected void resetChangeCounters() {
    myChangesCount = 0;
    myConflictsCount = 0;
  }

  protected void onChangeAdded(@NotNull ThreesideDiffChangeBase change) {
    if (change.isConflict()) {
      myConflictsCount++;
    }
    else {
      myChangesCount++;
    }
    myStatusPanel.update();
  }

  protected void onChangeRemoved(@NotNull ThreesideDiffChangeBase change) {
    if (change.isConflict()) {
      myConflictsCount--;
    }
    else {
      myChangesCount--;
    }
    myStatusPanel.update();
  }

  //
  // Getters
  //

  @NotNull
  protected abstract DividerPaintable getDividerPaintable(@NotNull Side side);

  /*
   * Some changes (ex: applied ones) can be excluded from general processing, but should be painted/used for synchronized scrolling
   */
  @NotNull
  protected List<? extends ThreesideDiffChangeBase> getAllChanges() {
    return getChanges();
  }

  @NotNull
  protected abstract List<? extends ThreesideDiffChangeBase> getChanges();

  @NotNull
  @Override
  protected SyncScrollSupport.SyncScrollable getSyncScrollable(@NotNull Side side) {
    return side.select(mySyncScrollable1, mySyncScrollable2);
  }

  @NotNull
  @Override
  protected JComponent getStatusPanel() {
    return myStatusPanel;
  }

  @NotNull
  public SyncScrollSupport.ThreesideSyncScrollSupport getSyncScrollSupport() {
    //noinspection ConstantConditions
    return mySyncScrollSupport;
  }

  //
  // Misc
  //

  @Nullable
  @CalledInAwt
  protected ThreesideDiffChangeBase getSelectedChange(@NotNull ThreeSide side) {
    int caretLine = getEditor(side).getCaretModel().getLogicalPosition().line;

    for (ThreesideDiffChangeBase change : getChanges()) {
      int line1 = change.getStartLine(side);
      int line2 = change.getEndLine(side);

      if (DiffUtil.isSelectedByLine(caretLine, line1, line2)) return change;
    }
    return null;
  }

  //
  // Actions
  //

  private class MyPrevConflictAction implements AnActionExtensionProvider {
    @Override
    public boolean isActive(@NotNull AnActionEvent e) {
      return true;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      if (!e.isFromActionToolbar() && !e.isFromContextMenu()) {
        e.getPresentation().setEnabled(true);
      }
      else {
        e.getPresentation().setEnabled(myPrevNextConflictIterable.canGoPrev());
      }
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      if (!myPrevNextConflictIterable.canGoPrev()) return;
      myPrevNextConflictIterable.goPrev();
    }
  }

  private class MyNextConflictAction implements AnActionExtensionProvider {
    @Override
    public boolean isActive(@NotNull AnActionEvent e) {
      return true;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      if (!e.isFromActionToolbar() && !e.isFromContextMenu()) {
        e.getPresentation().setEnabled(true);
      }
      else {
        e.getPresentation().setEnabled(myPrevNextConflictIterable.canGoNext());
      }
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      if (!myPrevNextConflictIterable.canGoNext()) return;
      myPrevNextConflictIterable.goNext();
    }
  }

  private class MyPrevNextConflictIterable extends MyPrevNextDifferenceIterable {
    @NotNull
    @Override
    protected List<? extends ThreesideDiffChangeBase> getChanges() {
      List<? extends ThreesideDiffChangeBase> changes = ThreesideTextDiffViewerEx.this.getChanges();
      return ContainerUtil.filter(changes, change -> change.isConflict());
    }
  }

  protected class MyPrevNextDifferenceIterable extends PrevNextDifferenceIterableBase<ThreesideDiffChangeBase> {
    @NotNull
    @Override
    protected List<? extends ThreesideDiffChangeBase> getChanges() {
      List<? extends ThreesideDiffChangeBase> changes = ThreesideTextDiffViewerEx.this.getChanges();
      final ThreeSide currentSide = getCurrentSide();
      if (currentSide == ThreeSide.BASE) return changes;
      return ContainerUtil.filter(changes, change -> change.isChange(currentSide));
    }

    @NotNull
    @Override
    protected EditorEx getEditor() {
      return getCurrentEditor();
    }

    @Override
    protected int getStartLine(@NotNull ThreesideDiffChangeBase change) {
      return change.getStartLine(getCurrentSide());
    }

    @Override
    protected int getEndLine(@NotNull ThreesideDiffChangeBase change) {
      return change.getEndLine(getCurrentSide());
    }

    @Override
    protected void scrollToChange(@NotNull ThreesideDiffChangeBase change) {
      doScrollToChange(change, true);
    }
  }

  protected class MyToggleExpandByDefaultAction extends TextDiffViewerUtil.ToggleExpandByDefaultAction {
    public MyToggleExpandByDefaultAction() {
      super(getTextSettings());
    }

    @Override
    protected void expandAll(boolean expand) {
      myFoldingModel.expandAll(expand);
    }
  }

  //
  // Helpers
  //

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE.is(dataId)) {
      return myPrevNextDifferenceIterable;
    }
    else if (DiffDataKeys.CURRENT_CHANGE_RANGE.is(dataId)) {
      ThreesideDiffChangeBase change = getSelectedChange(getCurrentSide());
      if (change != null) {
        return new LineRange(change.getStartLine(getCurrentSide()), change.getEndLine(getCurrentSide()));
      }
    }

    if (PrevConflictAction.DATA_KEY.is(dataId)) {
      return new MyPrevConflictAction();
    }
    else if (NextConflictAction.DATA_KEY.is(dataId)) {
      return new MyNextConflictAction();
    }

    return super.getData(dataId);
  }

  protected class MySyncScrollable extends BaseSyncScrollable {
    @NotNull private final Side mySide;

    public MySyncScrollable(@NotNull Side side) {
      mySide = side;
    }

    @Override
    public boolean isSyncScrollEnabled() {
      return getTextSettings().isEnableSyncScroll();
    }

    @Override
    protected void processHelper(@NotNull ScrollHelper helper) {
      ThreeSide left = mySide.select(ThreeSide.LEFT, ThreeSide.BASE);
      ThreeSide right = mySide.select(ThreeSide.BASE, ThreeSide.RIGHT);

      if (!helper.process(0, 0)) return;
      for (ThreesideDiffChangeBase diffChange : getAllChanges()) {
        if (!helper.process(diffChange.getStartLine(left), diffChange.getStartLine(right))) return;
        if (!helper.process(diffChange.getEndLine(left), diffChange.getEndLine(right))) return;
      }
      helper.process(getLineCount(getEditor(left).getDocument()), getLineCount(getEditor(right).getDocument()));
    }
  }

  protected class MyDividerPainter implements DiffSplitter.Painter {
    @NotNull private final Side mySide;
    @NotNull private final DividerPaintable myPaintable;

    public MyDividerPainter(@NotNull Side side) {
      mySide = side;
      myPaintable = getDividerPaintable(side);
    }

    @Override
    public void paint(@NotNull Graphics g, @NotNull JComponent divider) {
      Graphics2D gg = DiffDividerDrawUtil.getDividerGraphics(g, divider, getEditor(ThreeSide.BASE).getComponent());

      gg.setColor(DiffDrawUtil.getDividerColor(getEditor(ThreeSide.BASE)));
      gg.fill(gg.getClipBounds());

      Editor editor1 = mySide.select(getEditor(ThreeSide.LEFT), getEditor(ThreeSide.BASE));
      Editor editor2 = mySide.select(getEditor(ThreeSide.BASE), getEditor(ThreeSide.RIGHT));

      DiffDividerDrawUtil.paintPolygons(gg, divider.getWidth(), editor1, editor2, myPaintable);

      myFoldingModel.paintOnDivider(gg, divider, mySide);

      gg.dispose();
    }
  }

  protected class MyStatusPanel extends StatusPanel {
    @Nullable
    @Override
    protected String getMessage() {
      if (myChangesCount < 0 || myConflictsCount < 0) return null;
      if (myChangesCount == 0 && myConflictsCount == 0) {
        return DiffBundle.message("merge.dialog.all.conflicts.resolved.message.text");
      }
      return makeCounterWord(myChangesCount, "change") + ". " + makeCounterWord(myConflictsCount, "conflict");
    }

    @NotNull
    private String makeCounterWord(int number, @NotNull String word) {
      if (number == 0) {
        return "No " + StringUtil.pluralize(word);
      }
      return number + " " + StringUtil.pluralize(word, number);
    }
  }

  protected static class MyFoldingModel extends FoldingModelSupport {
    private final MyPaintable myPaintable1 = new MyPaintable(0, 1);
    private final MyPaintable myPaintable2 = new MyPaintable(1, 2);

    public MyFoldingModel(@NotNull EditorEx[] editors, @NotNull Disposable disposable) {
      super(editors, disposable);
      assert editors.length == 3;
    }

    public void install(@Nullable List<? extends MergeLineFragment> fragments,
                        @NotNull UserDataHolder context,
                        @NotNull FoldingModelSupport.Settings settings) {
      Iterator<int[]> it = map(fragments, fragment -> new int[]{
        fragment.getStartLine(ThreeSide.LEFT),
        fragment.getEndLine(ThreeSide.LEFT),
        fragment.getStartLine(ThreeSide.BASE),
        fragment.getEndLine(ThreeSide.BASE),
        fragment.getStartLine(ThreeSide.RIGHT),
        fragment.getEndLine(ThreeSide.RIGHT)
      });
      install(it, context, settings);
    }

    public void paintOnDivider(@NotNull Graphics2D gg, @NotNull Component divider, @NotNull Side side) {
      MyPaintable paintable = side.select(myPaintable1, myPaintable2);
      paintable.paintOnDivider(gg, divider);
    }
  }

  protected class MyInitialScrollHelper extends MyInitialScrollPositionHelper {
    @Override
    protected boolean doScrollToChange() {
      if (myScrollToChange == null) return false;
      return ThreesideTextDiffViewerEx.this.doScrollToChange(myScrollToChange);
    }

    @Override
    protected boolean doScrollToFirstChange() {
      return ThreesideTextDiffViewerEx.this.doScrollToChange(ScrollToPolicy.FIRST_CHANGE);
    }
  }
}
