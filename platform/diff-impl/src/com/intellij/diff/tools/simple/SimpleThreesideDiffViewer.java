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
import com.intellij.diff.comparison.ByLine;
import com.intellij.diff.comparison.ComparisonMergeUtil;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.comparison.DiffTooBigException;
import com.intellij.diff.comparison.iterables.FairDiffIterable;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.fragments.MergeLineFragment;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.util.*;
import com.intellij.diff.tools.util.base.IgnorePolicy;
import com.intellij.diff.tools.util.base.TextDiffViewerUtil;
import com.intellij.diff.tools.util.side.ThreesideTextDiffViewer;
import com.intellij.diff.util.*;
import com.intellij.diff.util.DiffUserDataKeysEx.ScrollToPolicy;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ButtonlessScrollBarUI;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

// TODO: extract common methods with Twoside
public class SimpleThreesideDiffViewer extends ThreesideTextDiffViewer {
  public static final Logger LOG = Logger.getInstance(SimpleThreesideDiffViewer.class);

  @NotNull private final SyncScrollSupport.SyncScrollable mySyncScrollable1;
  @NotNull private final SyncScrollSupport.SyncScrollable mySyncScrollable2;

  @NotNull private final PrevNextDifferenceIterable myPrevNextDifferenceIterable;
  @NotNull private final MyStatusPanel myStatusPanel;

  @NotNull private final List<SimpleThreesideDiffChange> myDiffChanges = new ArrayList<SimpleThreesideDiffChange>();
  private int myChangesCount = -1;
  private int myConflictsCount = -1;

  @NotNull private final MyFoldingModel myFoldingModel;
  @NotNull private final MyInitialScrollHelper myInitialScrollHelper = new MyInitialScrollHelper();

  public SimpleThreesideDiffViewer(@NotNull DiffContext context, @NotNull DiffRequest request) {
    super(context, (ContentDiffRequest)request);

    mySyncScrollable1 = new MySyncScrollable(Side.LEFT);
    mySyncScrollable2 = new MySyncScrollable(Side.RIGHT);
    myPrevNextDifferenceIterable = new MyPrevNextDifferenceIterable();
    myStatusPanel = new MyStatusPanel();
    myFoldingModel = new MyFoldingModel(getEditors().toArray(new EditorEx[3]), this);
  }

  @Override
  @CalledInAwt
  protected void onInit() {
    super.onInit();
    myContentPanel.setPainter(new MyDividerPainter(Side.LEFT), Side.LEFT);
    myContentPanel.setPainter(new MyDividerPainter(Side.RIGHT), Side.RIGHT);
    myContentPanel.setScrollbarPainter(new MyScrollbarPainter());
  }

  @Override
  @CalledInAwt
  protected void onDispose() {
    destroyChangedBlocks();
    super.onDispose();
  }

  @NotNull
  @Override
  protected List<AnAction> createToolbarActions() {
    List<AnAction> group = new ArrayList<AnAction>();

    group.add(new MyIgnorePolicySettingAction());
    //group.add(new MyHighlightPolicySettingAction()); // TODO
    group.add(new MyToggleExpandByDefaultAction());
    group.add(new MyToggleAutoScrollAction());
    group.add(new MyEditorReadOnlyLockAction());
    group.add(myEditorSettingsAction);

    group.add(Separator.getInstance());
    group.add(new ShowLeftBasePartialDiffAction());
    group.add(new ShowBaseRightPartialDiffAction());
    group.add(new ShowLeftRightPartialDiffAction());

    return group;
  }

  @Nullable
  @Override
  protected List<AnAction> createPopupActions() {
    List<AnAction> group = new ArrayList<AnAction>();

    group.add(Separator.getInstance());
    group.add(new MyIgnorePolicySettingAction().getPopupGroup());
    //group.add(Separator.getInstance());
    //group.add(new MyHighlightPolicySettingAction().getPopupGroup());
    group.add(Separator.getInstance());
    group.add(new MyToggleAutoScrollAction());
    group.add(new MyToggleExpandByDefaultAction());

    return group;
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

  //
  // Diff
  //

  @NotNull
  public FoldingModelSupport.Settings getFoldingModelSettings() {
    return TextDiffViewerUtil.getFoldingModelSettings(myContext);
  }

  @Override
  protected void onSlowRediff() {
    super.onSlowRediff();
    myStatusPanel.setBusy(true);
    myInitialScrollHelper.onSlowRediff();
  }

  @Override
  @NotNull
  protected Runnable performRediff(@NotNull final ProgressIndicator indicator) {
    try {
      indicator.checkCanceled();

      final List<DiffContent> contents = myRequest.getContents();
      List<CharSequence> sequences = ApplicationManager.getApplication().runReadAction(new Computable<List<CharSequence>>() {
        @Override
        public List<CharSequence> compute() {
          return ContainerUtil.map(contents, new Function<DiffContent, CharSequence>() {
            @Override
            public CharSequence fun(DiffContent diffContent) {
              return ((DocumentContent)diffContent).getDocument().getImmutableCharSequence();
            }
          });
        }
      });

      ComparisonPolicy comparisonPolicy = getIgnorePolicy().getComparisonPolicy();
      FairDiffIterable fragments1 = ByLine.compareTwoStepFair(sequences.get(1), sequences.get(0), comparisonPolicy, indicator);
      FairDiffIterable fragments2 = ByLine.compareTwoStepFair(sequences.get(1), sequences.get(2), comparisonPolicy, indicator);
      List<MergeLineFragment> mergeFragments = ComparisonMergeUtil.buildFair(fragments1, fragments2, indicator);

      return apply(mergeFragments, comparisonPolicy);
    }
    catch (DiffTooBigException e) {
      return applyNotification(DiffNotifications.DIFF_TOO_BIG);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.error(e);
      return applyNotification(DiffNotifications.ERROR);
    }
  }

  @NotNull
  private Runnable apply(@NotNull final List<MergeLineFragment> fragments,
                         @NotNull final ComparisonPolicy comparisonPolicy) {
    return new Runnable() {
      @Override
      public void run() {
        myFoldingModel.updateContext(myRequest, getFoldingModelSettings());
        clearDiffPresentation();

        myChangesCount = 0;
        myConflictsCount = 0;
        for (MergeLineFragment fragment : fragments) {
          SimpleThreesideDiffChange change = new SimpleThreesideDiffChange(fragment, getEditors(), comparisonPolicy);
          myDiffChanges.add(change);
          if (change.getDiffType() != TextDiffType.CONFLICT) myChangesCount++;
          if (change.getDiffType() == TextDiffType.CONFLICT) myConflictsCount++;
        }

        myFoldingModel.install(fragments, myRequest, getFoldingModelSettings());

        myInitialScrollHelper.onRediff();

        myContentPanel.repaintDividers();
        myStatusPanel.update();
      }
    };
  }

  @NotNull
  private Runnable applyNotification(@Nullable final JComponent notification) {
    return new Runnable() {
      @Override
      public void run() {
        clearDiffPresentation();
        if (notification != null) myPanel.addNotification(notification);
      }
    };
  }

  private void clearDiffPresentation() {
    myStatusPanel.setBusy(false);
    myPanel.resetNotifications();
    destroyChangedBlocks();
  }

  private void destroyChangedBlocks() {
    for (SimpleThreesideDiffChange change : myDiffChanges) {
      change.destroyHighlighter();
    }
    myDiffChanges.clear();

    myChangesCount = -1;
    myConflictsCount = -1;

    myFoldingModel.destroy();

    myContentPanel.repaintDividers();
    myStatusPanel.update();
  }

  //
  // Impl
  //

  @Override
  @CalledInAwt
  protected void onBeforeDocumentChange(@NotNull DocumentEvent e) {
    super.onBeforeDocumentChange(e);
    if (myDiffChanges.isEmpty()) return;

    ThreeSide side = null;
    if (e.getDocument() == getEditor(ThreeSide.LEFT).getDocument()) side = ThreeSide.LEFT;
    if (e.getDocument() == getEditor(ThreeSide.RIGHT).getDocument()) side = ThreeSide.RIGHT;
    if (e.getDocument() == getEditor(ThreeSide.BASE).getDocument()) side = ThreeSide.BASE;
    if (side == null) {
      LOG.warn("Unknown document changed");
      return;
    }

    int offset1 = e.getOffset();
    int offset2 = e.getOffset() + e.getOldLength();

    if (StringUtil.endsWithChar(e.getOldFragment(), '\n') &&
        StringUtil.endsWithChar(e.getNewFragment(), '\n')) {
      offset2--;
    }

    int line1 = e.getDocument().getLineNumber(offset1);
    int line2 = e.getDocument().getLineNumber(offset2) + 1;
    int shift = StringUtil.countNewLines(e.getNewFragment()) - StringUtil.countNewLines(e.getOldFragment());

    List<SimpleThreesideDiffChange> invalid = new ArrayList<SimpleThreesideDiffChange>();
    for (SimpleThreesideDiffChange change : myDiffChanges) {
      if (change.processChange(line1, line2, shift, side)) {
        invalid.add(change);
        change.destroyHighlighter();
      }
    }
    myDiffChanges.removeAll(invalid);
  }

  @Override
  protected void onDocumentChange(@NotNull DocumentEvent e) {
    super.onDocumentChange(e);
    myFoldingModel.onDocumentChanged(e);
  }

  @CalledInAwt
  protected boolean doScrollToChange(@NotNull ScrollToPolicy scrollToPolicy) {
    SimpleThreesideDiffChange targetChange = scrollToPolicy.select(myDiffChanges);
    if (targetChange == null) return false;

    doScrollToChange(targetChange, false);
    return true;
  }

  private void doScrollToChange(@NotNull SimpleThreesideDiffChange change, boolean animated) {
    // TODO: use anchors to fix scrolling issue at the start/end of file
    EditorEx editor = getCurrentEditor();
    int line = change.getStartLine(getCurrentSide());
    DiffUtil.scrollEditor(editor, line, animated);
  }

  @NotNull
  private IgnorePolicy getIgnorePolicy() {
    IgnorePolicy policy = getTextSettings().getIgnorePolicy();
    if (policy == IgnorePolicy.IGNORE_WHITESPACES_CHUNKS) return IgnorePolicy.IGNORE_WHITESPACES;
    return policy;
  }

  //
  // Getters
  //

  @NotNull
  protected List<SimpleThreesideDiffChange> getDiffChanges() {
    return myDiffChanges;
  }

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

  //
  // Misc
  //

  @Nullable
  @CalledInAwt
  private SimpleThreesideDiffChange getSelectedChange(@NotNull ThreeSide side) {
    EditorEx editor = getEditor(side);
    int caretLine = editor.getCaretModel().getLogicalPosition().line;

    for (SimpleThreesideDiffChange change : myDiffChanges) {
      int line1 = change.getStartLine(side);
      int line2 = change.getEndLine(side);

      if (DiffUtil.isSelectedByLine(caretLine, line1, line2)) return change;
    }
    return null;
  }

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static boolean canShowRequest(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return ThreesideTextDiffViewer.canShowRequest(context, request);
  }

  //
  // Actions
  //

  private class MyPrevNextDifferenceIterable extends PrevNextDifferenceIterableBase<SimpleThreesideDiffChange> {
    @NotNull
    @Override
    protected List<SimpleThreesideDiffChange> getChanges() {
      return myDiffChanges;
    }

    @NotNull
    @Override
    protected EditorEx getEditor() {
      return getCurrentEditor();
    }

    @Override
    protected int getStartLine(@NotNull SimpleThreesideDiffChange change) {
      return change.getStartLine(getCurrentSide());
    }

    @Override
    protected int getEndLine(@NotNull SimpleThreesideDiffChange change) {
      return change.getEndLine(getCurrentSide());
    }

    @Override
    protected void scrollToChange(@NotNull SimpleThreesideDiffChange change) {
      doScrollToChange(change, true);
    }
  }

  private class MyToggleExpandByDefaultAction extends TextDiffViewerUtil.ToggleExpandByDefaultAction {
    public MyToggleExpandByDefaultAction() {
      super(getTextSettings());
    }

    @Override
    protected void expandAll(boolean expand) {
      myFoldingModel.expandAll(expand);
    }
  }

  private class MyIgnorePolicySettingAction extends TextDiffViewerUtil.IgnorePolicySettingAction {
    public MyIgnorePolicySettingAction() {
      super(getTextSettings());
    }

    @NotNull
    @Override
    protected IgnorePolicy getCurrentSetting() {
      return getIgnorePolicy();
    }

    @NotNull
    @Override
    protected List<IgnorePolicy> getAvailableSettings() {
      ArrayList<IgnorePolicy> settings = ContainerUtil.newArrayList(IgnorePolicy.values());
      settings.remove(IgnorePolicy.IGNORE_WHITESPACES_CHUNKS);
      return settings;
    }

    @Override
    protected void onSettingsChanged() {
      rediff();
    }
  }

  protected class MyEditorReadOnlyLockAction extends TextDiffViewerUtil.EditorReadOnlyLockAction {
    public MyEditorReadOnlyLockAction() {
      super(getContext(), getEditableEditors());
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
      SimpleThreesideDiffChange change = getSelectedChange(getCurrentSide());
      if (change != null) {
        return new LineRange(change.getStartLine(getCurrentSide()), change.getEndLine(getCurrentSide()));
      }
    }

    return super.getData(dataId);
  }

  private class MySyncScrollable extends BaseSyncScrollable {
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
      for (SimpleThreesideDiffChange diffChange : myDiffChanges) {
        if (!helper.process(diffChange.getStartLine(left), diffChange.getStartLine(right))) return;
        if (!helper.process(diffChange.getEndLine(left), diffChange.getEndLine(right))) return;
      }
      helper.process(getEditor(left).getDocument().getLineCount(), getEditor(right).getDocument().getLineCount());
    }
  }

  private class MyDividerPaintable implements DiffDividerDrawUtil.DividerPaintable {
    @NotNull private final Side mySide;

    public MyDividerPaintable(@NotNull Side side) {
      mySide = side;
    }

    @Override
    public void process(@NotNull Handler handler) {
      ThreeSide left = mySide.select(ThreeSide.LEFT, ThreeSide.BASE);
      ThreeSide right = mySide.select(ThreeSide.BASE, ThreeSide.RIGHT);

      for (SimpleThreesideDiffChange diffChange : myDiffChanges) {
        if (!diffChange.getType().isChange(mySide)) continue;
        if (!handler.process(diffChange.getStartLine(left), diffChange.getEndLine(left),
                             diffChange.getStartLine(right), diffChange.getEndLine(right),
                             diffChange.getDiffType().getColor(getEditor(ThreeSide.BASE)))) {
          return;
        }
      }
    }
  }

  private class MyDividerPainter implements DiffSplitter.Painter {
    @NotNull private final Side mySide;
    @NotNull private final MyDividerPaintable myPaintable;

    public MyDividerPainter(@NotNull Side side) {
      mySide = side;
      myPaintable = new MyDividerPaintable(side);
    }

    @Override
    public void paint(@NotNull Graphics g, @NotNull JComponent divider) {
      Graphics2D gg = DiffDividerDrawUtil.getDividerGraphics(g, divider, getEditor(ThreeSide.BASE).getComponent());

      gg.setColor(DiffDrawUtil.getDividerColor(getEditor(ThreeSide.BASE)));
      gg.fill(gg.getClipBounds());

      Editor editor1 = mySide.select(getEditor(ThreeSide.LEFT), getEditor(ThreeSide.BASE));
      Editor editor2 = mySide.select(getEditor(ThreeSide.BASE), getEditor(ThreeSide.RIGHT));

      //DividerPolygonUtil.paintSimplePolygons(gg, divider.getWidth(), editor1, editor2, myPaintable);
      DiffDividerDrawUtil.paintPolygons(gg, divider.getWidth(), editor1, editor2, myPaintable);

      myFoldingModel.paintOnDivider(gg, divider, mySide);

      gg.dispose();
    }
  }

  private class MyScrollbarPainter implements ButtonlessScrollBarUI.ScrollbarRepaintCallback {
    @NotNull private final MyDividerPaintable myPaintable = new MyDividerPaintable(Side.RIGHT);

    @Override
    public void call(Graphics g) {
      EditorEx editor1 = getEditor(ThreeSide.BASE);
      EditorEx editor2 = getEditor(ThreeSide.RIGHT);

      int width = editor1.getScrollPane().getVerticalScrollBar().getWidth();
      DiffDividerDrawUtil.paintPolygonsOnScrollbar((Graphics2D)g, width, editor1, editor2, myPaintable);

      myFoldingModel.paintOnScrollbar((Graphics2D)g, width);
    }
  }

  private class MyStatusPanel extends StatusPanel {
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

  private static class MyFoldingModel extends FoldingModelSupport {
    private final MyPaintable myPaintable1 = new MyPaintable(0, 1);
    private final MyPaintable myPaintable2 = new MyPaintable(1, 2);

    public MyFoldingModel(@NotNull EditorEx[] editors, @NotNull Disposable disposable) {
      super(editors, disposable);
      assert editors.length == 3;
    }

    public void install(@Nullable List<MergeLineFragment> fragments,
                        @NotNull UserDataHolder context,
                        @NotNull FoldingModelSupport.Settings settings) {
      Iterator<int[]> it = map(fragments, new Function<MergeLineFragment, int[]>() {
        @Override
        public int[] fun(MergeLineFragment fragment) {
          return new int[]{
            fragment.getStartLine(ThreeSide.LEFT),
            fragment.getEndLine(ThreeSide.LEFT),
            fragment.getStartLine(ThreeSide.BASE),
            fragment.getEndLine(ThreeSide.BASE),
            fragment.getStartLine(ThreeSide.RIGHT),
            fragment.getEndLine(ThreeSide.RIGHT)};
        }
      });
      install(it, context, settings);
    }

    public void paintOnDivider(@NotNull Graphics2D gg, @NotNull Component divider, @NotNull Side side) {
      MyPaintable paintable = side.select(myPaintable1, myPaintable2);
      paintable.paintOnDivider(gg, divider);
    }

    public void paintOnScrollbar(@NotNull Graphics2D gg, int width) {
      myPaintable2.paintOnScrollbar(gg, width);
    }
  }

  private class MyInitialScrollHelper extends MyInitialScrollPositionHelper {
    @Override
    protected boolean doScrollToChange() {
      if (myScrollToChange == null) return false;
      return SimpleThreesideDiffViewer.this.doScrollToChange(myScrollToChange);
    }

    @Override
    protected boolean doScrollToFirstChange() {
      return SimpleThreesideDiffViewer.this.doScrollToChange(ScrollToPolicy.FIRST_CHANGE);
    }
  }
}
