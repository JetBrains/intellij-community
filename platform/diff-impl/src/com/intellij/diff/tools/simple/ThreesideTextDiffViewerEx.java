// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.simple;

import com.intellij.diff.DiffContext;
import com.intellij.diff.fragments.MergeLineFragment;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.tools.util.*;
import com.intellij.diff.tools.util.base.TextDiffViewerUtil;
import com.intellij.diff.tools.util.side.ThreesideContentPanel;
import com.intellij.diff.tools.util.side.ThreesideTextDiffViewer;
import com.intellij.diff.tools.util.text.LineOffsets;
import com.intellij.diff.util.*;
import com.intellij.diff.util.DiffDividerDrawUtil.DividerPaintable;
import com.intellij.diff.util.DiffUserDataKeysEx.ScrollToPolicy;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Iterator;
import java.util.List;

import static com.intellij.diff.util.DiffUtil.getLineCount;

public abstract class ThreesideTextDiffViewerEx extends ThreesideTextDiffViewer {
  @NotNull private final SyncScrollSupport.SyncScrollable mySyncScrollable1;
  @NotNull private final SyncScrollSupport.SyncScrollable mySyncScrollable2;

  @NotNull private final PrevNextDifferenceIterable myPrevNextDifferenceIterable;
  @NotNull private final PrevNextDifferenceIterable myPrevNextConflictIterable;
  @NotNull protected final StatusPanel myStatusPanel;

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
    myStatusPanel = createStatusPanel();
    myFoldingModel = new MyFoldingModel(getProject(), getEditors().toArray(new EditorEx[0]), myContentPanel, this);

    for (ThreeSide side : ThreeSide.values()) {
      DiffUtil.installLineConvertor(getEditor(side), getContent(side), myFoldingModel, side.getIndex());
    }

    DiffUtil.registerAction(new PrevConflictAction(), myPanel);
    DiffUtil.registerAction(new NextConflictAction(), myPanel);
  }

  protected @NotNull StatusPanel createStatusPanel() {
    return new MyStatusPanel();
  }

  @Override
  @RequiresEdt
  protected void onInit() {
    super.onInit();
    myContentPanel.setPainter(new MyDividerPainter(Side.LEFT), Side.LEFT);
    myContentPanel.setPainter(new MyDividerPainter(Side.RIGHT), Side.RIGHT);
  }

  @Override
  @RequiresEdt
  protected void processContextHints() {
    super.processContextHints();
    myInitialScrollHelper.processContext(myRequest);
  }

  @Override
  @RequiresEdt
  protected void updateContextHints() {
    super.updateContextHints();
    myFoldingModel.updateContext(myRequest, getFoldingModelSettings());
    myInitialScrollHelper.updateContext(myRequest);
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

  @RequiresEdt
  protected void clearDiffPresentation() {
    myStatusPanel.setBusy(false);
    myPanel.resetNotifications();
    destroyChangedBlocks();

    myContentPanel.repaintDividers();
    myStatusPanel.update();
  }

  @RequiresEdt
  protected void destroyChangedBlocks() {
  }

  //
  // Impl
  //

  @RequiresEdt
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
  public List<? extends ThreesideDiffChangeBase> getAllChanges() {
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
  @RequiresEdt
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

  private class PrevConflictAction extends DumbAwareAction {
    PrevConflictAction() {
      ActionUtil.copyFrom(this, "Diff.PreviousConflict");
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      if (!myPrevNextConflictIterable.canGoPrev()) return;
      myPrevNextConflictIterable.goPrev();
    }
  }

  private class NextConflictAction extends DumbAwareAction {
    NextConflictAction() {
      ActionUtil.copyFrom(this, "Diff.NextConflict");
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
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
      super(getTextSettings(), myFoldingModel);
    }
  }

  //
  // Helpers
  //

  @Override
  public @Nullable PrevNextDifferenceIterable getDifferenceIterable() {
    return myPrevNextDifferenceIterable;
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    super.uiDataSnapshot(sink);
    ThreesideDiffChangeBase change = getSelectedChange(getCurrentSide());
    if (change != null) {
      sink.set(DiffDataKeys.CURRENT_CHANGE_RANGE, new LineRange(
        change.getStartLine(getCurrentSide()), change.getEndLine(getCurrentSide())));
    }
    sink.set(DiffDataKeys.EDITOR_CHANGED_RANGE_PROVIDER, new MyChangedRangeProvider());
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
      return DiffBundle.message("merge.differences.status.text", myChangesCount, myConflictsCount);
    }
  }

  protected static class MyFoldingModel extends FoldingModelSupport {
    private final MyPaintable myPaintable1 = new MyPaintable(0, 1);
    private final MyPaintable myPaintable2 = new MyPaintable(1, 2);
    private final ThreesideContentPanel myContentPanel;

    public MyFoldingModel(@Nullable Project project,
                          EditorEx @NotNull [] editors,
                          @NotNull ThreesideContentPanel contentPanel,
                          @NotNull Disposable disposable) {
      super(project, editors, disposable);
      myContentPanel = contentPanel;
      assert editors.length == 3;
    }

    @Override
    protected void repaintSeparators() {
      myContentPanel.repaint();
    }

    @Nullable
    public Data createState(@Nullable List<? extends MergeLineFragment> fragments,
                            @NotNull FoldingModelSupport.Settings settings) {
      return createState(fragments, countLines(myEditors), settings);
    }

    @Nullable
    public Data createState(@Nullable List<? extends MergeLineFragment> fragments,
                            @NotNull List<? extends LineOffsets> lineOffsets,
                            @NotNull FoldingModelSupport.Settings settings) {
      int[] lineCount = new int[myEditors.length];
      for (int i = 0; i < myEditors.length; i++) {
        lineCount[i] = lineOffsets.get(i).getLineCount();
      }
      return createState(fragments, lineCount, settings);
    }

    @Nullable
    private Data createState(@Nullable List<? extends MergeLineFragment> fragments,
                             int @NotNull [] lineCount,
                             @NotNull FoldingModelSupport.Settings settings) {
      Iterator<int[]> it = map(fragments, fragment -> new int[]{
        fragment.getStartLine(ThreeSide.LEFT),
        fragment.getEndLine(ThreeSide.LEFT),
        fragment.getStartLine(ThreeSide.BASE),
        fragment.getEndLine(ThreeSide.BASE),
        fragment.getStartLine(ThreeSide.RIGHT),
        fragment.getEndLine(ThreeSide.RIGHT)
      });
      return computeFoldedRanges(it, lineCount, settings);
    }

    @Nullable
    private Data computeFoldedRanges(@Nullable final Iterator<int[]> changedLines,
                                     int @NotNull [] lineCount,
                                     @NotNull final Settings settings) {
      if (changedLines == null || settings.range == -1) return null;

      FoldingBuilderBase builder = new MyFoldingBuilder(myEditors, lineCount, settings);
      return builder.build(changedLines);
    }

    public void paintOnDivider(@NotNull Graphics2D gg, @NotNull Component divider, @NotNull Side side) {
      MyPaintable paintable = side.select(myPaintable1, myPaintable2);
      paintable.paintOnDivider(gg, divider);
    }

    private static final class MyFoldingBuilder extends FoldingBuilderBase {
      private final EditorEx @NotNull [] myEditors;

      private MyFoldingBuilder(EditorEx @NotNull [] editors, int @NotNull [] lineCount, @NotNull Settings settings) {
        super(lineCount, settings);
        myEditors = editors;
      }

      @Nullable
      @Override
      protected FoldedRangeDescription getDescription(@NotNull Project project, int lineNumber, int index) {
        return getLineSeparatorDescription(project, myEditors[index].getDocument(), lineNumber);
      }
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

  private class MyChangedRangeProvider implements DiffChangedRangeProvider {
    @Override
    public @Nullable List<TextRange> getChangedRanges(@NotNull Editor editor) {
      ThreeSide side = ThreeSide.fromValue(getEditors(), editor);
      if (side == null) return null;

      return ContainerUtil.map(getAllChanges(), change -> {
        return DiffUtil.getLinesRange(editor.getDocument(), change.getStartLine(side), change.getEndLine(side));
      });
    }
  }
}
