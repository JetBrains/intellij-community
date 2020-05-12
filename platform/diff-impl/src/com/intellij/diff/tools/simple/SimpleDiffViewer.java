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
import com.intellij.diff.actions.AllLinesIterator;
import com.intellij.diff.actions.BufferedLineIterator;
import com.intellij.diff.comparison.DiffTooBigException;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.util.*;
import com.intellij.diff.tools.util.base.TextDiffViewerUtil;
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer;
import com.intellij.diff.tools.util.text.TwosideTextDiffProvider;
import com.intellij.diff.util.*;
import com.intellij.diff.util.Range;
import com.intellij.diff.util.DiffUserDataKeysEx.ScrollToPolicy;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.DiffNavigationContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DirtyUI;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;

import static com.intellij.diff.util.DiffUtil.getLineCount;

public class SimpleDiffViewer extends TwosideTextDiffViewer {
  @NotNull private final SyncScrollSupport.SyncScrollable mySyncScrollable;
  @NotNull private final PrevNextDifferenceIterable myPrevNextDifferenceIterable;
  @NotNull private final StatusPanel myStatusPanel;

  @NotNull private final SimpleDiffModel myModel = new SimpleDiffModel(this);

  @NotNull private final MyFoldingModel myFoldingModel;
  @NotNull private final MyInitialScrollHelper myInitialScrollHelper = new MyInitialScrollHelper();
  @NotNull private final ModifierProvider myModifierProvider;

  @NotNull protected final TwosideTextDiffProvider myTextDiffProvider;

  public SimpleDiffViewer(@NotNull DiffContext context, @NotNull DiffRequest request) {
    super(context, (ContentDiffRequest)request);

    mySyncScrollable = new MySyncScrollable();
    myPrevNextDifferenceIterable = new MyPrevNextDifferenceIterable();
    myStatusPanel = new MyStatusPanel();
    myFoldingModel = new MyFoldingModel(getProject(), getEditors(), this);

    myModifierProvider = new ModifierProvider();

    myTextDiffProvider = DiffUtil.createTextDiffProvider(getProject(), getRequest(), getTextSettings(), this::rediff, this);

    for (Side side : Side.values()) {
      DiffUtil.installLineConvertor(getEditor(side), getContent(side), myFoldingModel, side.getIndex());
    }
  }

  @Override
  @CalledInAwt
  protected void onInit() {
    super.onInit();
    myContentPanel.setPainter(new MyDividerPainter());
    myModifierProvider.init();
  }

  @Override
  @CalledInAwt
  protected void onDispose() {
    myModel.clear();
    myFoldingModel.destroy();
    super.onDispose();
  }

  @NotNull
  @Override
  protected List<AnAction> createToolbarActions() {
    List<AnAction> group = new ArrayList<>(myTextDiffProvider.getToolbarActions());
    group.add(new MyToggleExpandByDefaultAction());
    group.add(new MyToggleAutoScrollAction());
    group.add(new MyReadOnlyLockAction());
    group.add(myEditorSettingsAction);

    group.add(Separator.getInstance());
    group.addAll(super.createToolbarActions());

    return group;
  }

  @NotNull
  @Override
  protected List<AnAction> createPopupActions() {
    List<AnAction> group = new ArrayList<>(myTextDiffProvider.getPopupActions());
    group.add(new MyToggleAutoScrollAction());
    group.add(new MyToggleExpandByDefaultAction());

    group.add(Separator.getInstance());
    group.addAll(super.createPopupActions());

    return group;
  }

  @NotNull
  @Override
  protected List<AnAction> createEditorPopupActions() {
    List<AnAction> group = new ArrayList<>();

    group.add(new ReplaceSelectedChangesAction(Side.LEFT));
    group.add(new AppendSelectedChangesAction(Side.LEFT));
    group.add(new ReplaceSelectedChangesAction(Side.RIGHT));
    group.add(new AppendSelectedChangesAction(Side.RIGHT));

    group.add(Separator.getInstance());
    group.addAll(super.createEditorPopupActions());

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

  @NotNull
  protected SimpleDiffChangeUi createUi(@NotNull SimpleDiffChange change) {
    return new SimpleDiffChangeUi(this, change);
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
      return computeDifferences(indicator);
    }
    catch (DiffTooBigException e) {
      return applyNotification(DiffNotifications.createDiffTooBig());
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.error(e);
      return applyNotification(DiffNotifications.createError());
    }
  }

  @NotNull
  protected Runnable computeDifferences(@NotNull ProgressIndicator indicator) {
    indicator.checkCanceled();

    final Document document1 = getContent1().getDocument();
    final Document document2 = getContent2().getDocument();

    CharSequence[] texts =
      ReadAction.compute(() -> new CharSequence[]{document1.getImmutableCharSequence(), document2.getImmutableCharSequence()});

    List<LineFragment> lineFragments = myTextDiffProvider.compare(texts[0], texts[1], indicator);

    boolean isContentsEqual = (lineFragments == null || lineFragments.isEmpty()) &&
                              StringUtil.equals(texts[0], texts[1]);

    if (lineFragments == null) {
      return apply(null, isContentsEqual);
    }
    else {
      List<SimpleDiffChange> changes = new ArrayList<>();
      for (LineFragment fragment : lineFragments) {
        changes.add(new SimpleDiffChange(changes.size(), fragment));
      }
      return apply(changes, isContentsEqual);
    }
  }

  @NotNull
  protected Runnable apply(@Nullable List<SimpleDiffChange> changes,
                           boolean isContentsEqual) {
    List<SimpleDiffChange> nonSkipped = changes != null ? ContainerUtil.filter(changes, it -> !it.isSkipped()) : null;
    FoldingModelSupport.Data foldingState = myFoldingModel.createState(nonSkipped, getFoldingModelSettings());

    return () -> {
      myFoldingModel.updateContext(myRequest, getFoldingModelSettings());

      clearDiffPresentation();

      if (isContentsEqual) {
        boolean equalCharsets = TextDiffViewerUtil.areEqualCharsets(getContents());
        boolean equalSeparators = TextDiffViewerUtil.areEqualLineSeparators(getContents());
        myPanel.addNotification(DiffNotifications.createEqualContents(equalCharsets, equalSeparators));
      }

      myModel.setChanges(ContainerUtil.notNullize(changes), isContentsEqual);

      myFoldingModel.install(foldingState, myRequest, getFoldingModelSettings());

      myInitialScrollHelper.onRediff();

      myContentPanel.repaintDivider();
      myStatusPanel.update();
    };
  }

  @NotNull
  protected Runnable applyNotification(@Nullable final JComponent notification) {
    return () -> {
      clearDiffPresentation();
      myFoldingModel.destroy();
      if (notification != null) myPanel.addNotification(notification);
    };
  }

  private void clearDiffPresentation() {
    myModel.clear();

    myPanel.resetNotifications();
    myStatusPanel.setBusy(false);

    myContentPanel.repaintDivider();
    myStatusPanel.update();
  }

  //
  // Impl
  //

  @Override
  @CalledInAwt
  protected void onBeforeDocumentChange(@NotNull DocumentEvent e) {
    super.onBeforeDocumentChange(e);

    List<Document> documents = ContainerUtil.map(getEditors(), Editor::getDocument);
    Side side = Side.fromValue(documents, e.getDocument());
    if (side == null) {
      LOG.warn("Unknown document changed");
      return;
    }

    myModel.handleBeforeDocumentChange(side, e);
  }

  @CalledInAwt
  protected boolean doScrollToChange(@NotNull ScrollToPolicy scrollToPolicy) {
    SimpleDiffChange targetChange = scrollToPolicy.select(getNonSkippedDiffChanges());
    if (targetChange == null) targetChange = scrollToPolicy.select(getDiffChanges());
    if (targetChange == null) return false;

    doScrollToChange(targetChange, false);
    return true;
  }

  private void doScrollToChange(@NotNull SimpleDiffChange change, final boolean animated) {
    final int line1 = change.getStartLine(Side.LEFT);
    final int line2 = change.getStartLine(Side.RIGHT);
    final int endLine1 = change.getEndLine(Side.LEFT);
    final int endLine2 = change.getEndLine(Side.RIGHT);

    DiffUtil.moveCaret(getEditor1(), line1);
    DiffUtil.moveCaret(getEditor2(), line2);

    getSyncScrollSupport().makeVisible(getCurrentSide(), line1, endLine1, line2, endLine2, animated);
  }

  protected boolean doScrollToContext(@NotNull DiffNavigationContext context) {
    ChangedLinesIterator changedLinesIterator = new ChangedLinesIterator();
    int line = context.contextMatchCheck(changedLinesIterator);
    if (line == -1) {
      // this will work for the case, when spaces changes are ignored, and corresponding fragments are not reported as changed
      // just try to find target line  -> +-
      AllLinesIterator allLinesIterator = new AllLinesIterator(getEditor(Side.RIGHT).getDocument());
      line = context.contextMatchCheck(allLinesIterator);
    }
    if (line == -1) return false;

    scrollToLine(Side.RIGHT, line);
    return true;
  }

  //
  // Getters
  //

  @NotNull
  public List<SimpleDiffChange> getDiffChanges() {
    return myModel.getChanges();
  }

  @NotNull
  private List<SimpleDiffChange> getNonSkippedDiffChanges() {
    return ContainerUtil.filter(myModel.getChanges(), it -> !it.isSkipped());
  }

  @NotNull
  @Override
  protected SyncScrollSupport.SyncScrollable getSyncScrollable() {
    return mySyncScrollable;
  }

  @NotNull
  @Override
  protected StatusPanel getStatusPanel() {
    return myStatusPanel;
  }

  @NotNull
  public KeyboardModifierListener getModifierProvider() {
    return myModifierProvider;
  }

  @NotNull
  @Override
  public SyncScrollSupport.TwosideSyncScrollSupport getSyncScrollSupport() {
    //noinspection ConstantConditions
    return super.getSyncScrollSupport();
  }

  protected boolean isEditable(@NotNull Side side) {
    return DiffUtil.isEditable(getEditor(side));
  }

  //
  // Misc
  //

  boolean isDiffForLocalChanges() {
    boolean isLastWithLocal = DiffUserDataKeysEx.LAST_REVISION_WITH_LOCAL.get(myContext, false);
    return isLastWithLocal && !isEditable(Side.LEFT) && isEditable(Side.RIGHT);
  }

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static boolean canShowRequest(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return TwosideTextDiffViewer.canShowRequest(context, request);
  }

  protected boolean isSomeChangeSelected(@NotNull Side side) {
    if (getDiffChanges().isEmpty()) return false;

    EditorEx editor = getEditor(side);
    return DiffUtil.isSomeRangeSelected(editor, lines ->
      ContainerUtil.exists(getDiffChanges(), change -> isChangeSelected(change, lines, side)));
  }

  @NotNull
  @CalledInAwt
  protected List<SimpleDiffChange> getSelectedChanges(@NotNull Side side) {
    EditorEx editor = getEditor(side);
    BitSet lines = DiffUtil.getSelectedLines(editor);
    return ContainerUtil.filter(getDiffChanges(), change -> isChangeSelected(change, lines, side));
  }

  private static boolean isChangeSelected(SimpleDiffChange change, @NotNull BitSet lines, @NotNull Side side) {
    int line1 = change.getStartLine(side);
    int line2 = change.getEndLine(side);
    return DiffUtil.isSelectedByLine(lines, line1, line2);
  }

  @Nullable
  @CalledInAwt
  protected SimpleDiffChange getSelectedChange(@NotNull Side side) {
    int caretLine = getEditor(side).getCaretModel().getLogicalPosition().line;

    for (SimpleDiffChange change : getDiffChanges()) {
      int line1 = change.getStartLine(side);
      int line2 = change.getEndLine(side);

      if (DiffUtil.isSelectedByLine(caretLine, line1, line2)) return change;
    }
    return null;
  }

  //
  // Actions
  //

  private class MyPrevNextDifferenceIterable extends PrevNextDifferenceIterableBase<SimpleDiffChange> {
    @NotNull
    @Override
    protected List<SimpleDiffChange> getChanges() {
      return getNonSkippedDiffChanges();
    }

    @NotNull
    @Override
    protected EditorEx getEditor() {
      return getCurrentEditor();
    }

    @Override
    protected int getStartLine(@NotNull SimpleDiffChange change) {
      return change.getStartLine(getCurrentSide());
    }

    @Override
    protected int getEndLine(@NotNull SimpleDiffChange change) {
      return change.getEndLine(getCurrentSide());
    }

    @Override
    protected void scrollToChange(@NotNull SimpleDiffChange change) {
      doScrollToChange(change, true);
    }
  }

  private class MyReadOnlyLockAction extends TextDiffViewerUtil.EditorReadOnlyLockAction {
    MyReadOnlyLockAction() {
      super(getContext(), getEditableEditors());
    }

    @Override
    protected void doApply(boolean readOnly) {
      super.doApply(readOnly);
      myModel.updateGutterActions(true);
    }
  }

  //
  // Modification operations
  //

  protected abstract class SelectedChangesActionBase extends DumbAwareAction {
    @Override
    public void update(@NotNull AnActionEvent e) {
      if (DiffUtil.isFromShortcut(e)) {
        // consume shortcut even if there are nothing to do - avoid calling some other action
        e.getPresentation().setEnabledAndVisible(true);
        return;
      }

      Editor editor = e.getData(CommonDataKeys.EDITOR);
      Side side = Side.fromValue(getEditors(), editor);
      if (side == null || !isVisible(side)) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }

      e.getPresentation().setText(getText(side));
      e.getPresentation().setIcon(getIcon(side));

      e.getPresentation().setVisible(true);
      e.getPresentation().setEnabled(isSomeChangeSelected(side));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Editor editor = e.getData(CommonDataKeys.EDITOR);
      final Side side = Side.fromValue(getEditors(), editor);
      if (side == null) return;

      final List<SimpleDiffChange> selectedChanges = getSelectedChanges(side);
      if (selectedChanges.isEmpty()) return;

      doPerform(e, side, ContainerUtil.reverse(selectedChanges));
    }

    protected abstract boolean isVisible(@NotNull Side side);

    @Nls
    @NotNull
    protected abstract String getText(@NotNull Side side);

    @Nullable
    protected abstract Icon getIcon(@NotNull Side side);

    @CalledWithWriteLock
    protected abstract void doPerform(@NotNull AnActionEvent e, @NotNull Side side, @NotNull List<SimpleDiffChange> changes);
  }

  private abstract class ApplySelectedChangesActionBase extends SelectedChangesActionBase {
    @NotNull protected final Side myModifiedSide;

    ApplySelectedChangesActionBase(@NotNull Side modifiedSide) {
      myModifiedSide = modifiedSide;
    }

    @Override
    protected boolean isVisible(@NotNull Side side) {
      if (!isEditable(myModifiedSide)) return false;
      return !isBothEditable() || side == myModifiedSide.other();
    }

    @Override
    protected void doPerform(@NotNull AnActionEvent e, @NotNull Side side, @NotNull List<SimpleDiffChange> changes) {
      if (!isEditable(myModifiedSide)) return;

      String title = DiffBundle.message("message.use.selected.changes.command", e.getPresentation().getText());
      DiffUtil.executeWriteCommand(getEditor(myModifiedSide).getDocument(), e.getProject(), title, () -> apply(changes));
    }

    protected boolean isBothEditable() {
      return isEditable(Side.LEFT) && isEditable(Side.RIGHT);
    }

    @CalledWithWriteLock
    protected abstract void apply(@NotNull List<SimpleDiffChange> changes);
  }

  private class ReplaceSelectedChangesAction extends ApplySelectedChangesActionBase {
    ReplaceSelectedChangesAction(@NotNull Side focusedSide) {
      super(focusedSide.other());
      setShortcutSet(ActionManager.getInstance().getAction(focusedSide.select("Diff.ApplyLeftSide", "Diff.ApplyRightSide")).getShortcutSet());
    }

    @NotNull
    @Override
    protected String getText(@NotNull Side side) {
      if (myModifiedSide == Side.RIGHT && isDiffForLocalChanges()) return DiffBundle.message("action.presentation.diff.revert.text");
      return DiffBundle.message("action.presentation.diff.accept.text");
    }

    @Nullable
    @Override
    protected Icon getIcon(@NotNull Side side) {
      return DiffUtil.getArrowIcon(myModifiedSide.other());
    }

    @Override
    protected void apply(@NotNull List<SimpleDiffChange> changes) {
      for (SimpleDiffChange change : changes) {
        replaceChange(change, myModifiedSide.other());
      }
    }
  }

  private class AppendSelectedChangesAction extends ApplySelectedChangesActionBase {
    AppendSelectedChangesAction(@NotNull Side focusedSide) {
      super(focusedSide.other());
      setShortcutSet(ActionManager.getInstance().getAction(focusedSide.select("Diff.AppendLeftSide", "Diff.AppendRightSide")).getShortcutSet());
    }

    @NotNull
    @Override
    protected String getText(@NotNull Side side) {
      return isBothEditable()
             ? DiffBundle.message("action.presentation.diff.append.to.the.side.text", myModifiedSide.getIndex())
             : DiffBundle.message("action.presentation.diff.append.text");
    }

    @Nullable
    @Override
    protected Icon getIcon(@NotNull Side side) {
      return DiffUtil.getArrowDownIcon(myModifiedSide.other());
    }

    @Override
    protected void apply(@NotNull List<SimpleDiffChange> changes) {
      for (SimpleDiffChange change : changes) {
        appendChange(change, myModifiedSide.other());
      }
    }
  }

  @CalledWithWriteLock
  public void replaceChange(@NotNull SimpleDiffChange change, @NotNull final Side sourceSide) {
    if (!change.isValid()) return;
    Side outputSide = sourceSide.other();

    DiffUtil.applyModification(getEditor(outputSide).getDocument(), change.getStartLine(outputSide), change.getEndLine(outputSide),
                               getEditor(sourceSide).getDocument(), change.getStartLine(sourceSide), change.getEndLine(sourceSide));

    myModel.destroyChange(change);
  }

  @CalledWithWriteLock
  public void appendChange(@NotNull SimpleDiffChange change, @NotNull final Side sourceSide) {
    if (!change.isValid()) return;
    if (change.getStartLine(sourceSide) == change.getEndLine(sourceSide)) return;
    Side outputSide = sourceSide.other();

    DiffUtil.applyModification(getEditor(outputSide).getDocument(), change.getEndLine(outputSide), change.getEndLine(outputSide),
                               getEditor(sourceSide).getDocument(), change.getStartLine(sourceSide), change.getEndLine(sourceSide));

    myModel.destroyChange(change);
  }

  private class MyToggleExpandByDefaultAction extends TextDiffViewerUtil.ToggleExpandByDefaultAction {
    MyToggleExpandByDefaultAction() {
      super(getTextSettings());
    }

    @Override
    protected void expandAll(boolean expand) {
      myFoldingModel.expandAll(expand);
    }
  }

  //
  // Scroll from annotate
  //

  private class ChangedLinesIterator extends BufferedLineIterator {
    private int myIndex = 0;

    private ChangedLinesIterator() {
      init();
    }

    @Override
    public boolean hasNextBlock() {
      return myIndex < getDiffChanges().size();
    }

    @Override
    public void loadNextBlock() {
      SimpleDiffChange change = getDiffChanges().get(myIndex);
      myIndex++;

      int line1 = change.getStartLine(Side.RIGHT);
      int line2 = change.getEndLine(Side.RIGHT);

      Document document = getEditor(Side.RIGHT).getDocument();

      for (int i = line1; i < line2; i++) {
        int offset1 = document.getLineStartOffset(i);
        int offset2 = document.getLineEndOffset(i);

        CharSequence text = document.getImmutableCharSequence().subSequence(offset1, offset2);
        addLine(i, text);
      }
    }
  }

  //
  // Helpers
  //

  @Nullable
  @Override
  public Object getData(@NotNull @NonNls String dataId) {
    if (DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE.is(dataId)) {
      return myPrevNextDifferenceIterable;
    }
    else if (DiffDataKeys.CURRENT_CHANGE_RANGE.is(dataId)) {
      SimpleDiffChange change = getSelectedChange(getCurrentSide());
      if (change != null) {
        return new LineRange(change.getStartLine(getCurrentSide()), change.getEndLine(getCurrentSide()));
      }
    }
    return super.getData(dataId);
  }

  private class MySyncScrollable extends BaseSyncScrollable {
    @Override
    public boolean isSyncScrollEnabled() {
      return getTextSettings().isEnableSyncScroll();
    }

    @NotNull
    @Override
    public Range getRange(@NotNull Side baseSide, int line) {
      if (getDiffChanges().isEmpty()) return idRange(line);
      return super.getRange(baseSide, line);
    }

    @Override
    protected void processHelper(@NotNull ScrollHelper helper) {
      if (!helper.process(0, 0)) return;
      for (SimpleDiffChange diffChange : getDiffChanges()) {
        if (!helper.process(diffChange.getStartLine(Side.LEFT), diffChange.getStartLine(Side.RIGHT))) return;
        if (!helper.process(diffChange.getEndLine(Side.LEFT), diffChange.getEndLine(Side.RIGHT))) return;
      }
      helper.process(getLineCount(getEditor1().getDocument()), getLineCount(getEditor2().getDocument()));
    }
  }

  private class MyDividerPainter implements DiffSplitter.Painter, DiffDividerDrawUtil.DividerPaintable {
    @DirtyUI
    @Override
    public void paint(@NotNull Graphics g, @NotNull JComponent divider) {
      Graphics2D gg = DiffDividerDrawUtil.getDividerGraphics(g, divider, getEditor1().getComponent());

      gg.setColor(DiffDrawUtil.getDividerColor(getEditor1()));
      gg.fill(gg.getClipBounds());

      DiffDividerDrawUtil.paintPolygons(gg, divider.getWidth(), getEditor1(), getEditor2(), this);

      myFoldingModel.paintOnDivider(gg, divider);

      gg.dispose();
    }

    @Override
    public void process(@NotNull Handler handler) {
      for (SimpleDiffChange diffChange : getDiffChanges()) {
        if (!handler.processExcludable(diffChange.getStartLine(Side.LEFT), diffChange.getEndLine(Side.LEFT),
                                       diffChange.getStartLine(Side.RIGHT), diffChange.getEndLine(Side.RIGHT),
                                       getEditor1(), diffChange.getDiffType(), diffChange.isExcluded())) {
          return;
        }
      }
    }
  }

  private class MyStatusPanel extends StatusPanel {
    @Nullable
    @Override
    protected String getMessage() {
      if (myTextDiffProvider.isHighlightingDisabled()) {
        return DiffBundle.message("diff.highlighting.disabled.text");
      }
      List<SimpleDiffChange> allChanges = myModel.getAllChanges();
      return DiffUtil.getStatusText(allChanges.size(),
                                    ContainerUtil.count(allChanges, it -> it.isExcluded()),
                                    myModel.isContentsEqual());
    }
  }

  public class ModifierProvider extends KeyboardModifierListener {
    public void init() {
      init(myPanel, SimpleDiffViewer.this);
    }

    @Override
    public void onModifiersChanged() {
      myModel.updateGutterActions(false);
    }
  }

  private static class MyFoldingModel extends FoldingModelSupport {
    private final MyPaintable myPaintable = new MyPaintable(0, 1);

    MyFoldingModel(@Nullable Project project, @NotNull List<? extends EditorEx> editors, @NotNull Disposable disposable) {
      super(project, editors.toArray(new EditorEx[0]), disposable);
    }

    @Nullable
    public Data createState(@Nullable List<SimpleDiffChange> changes, @NotNull Settings settings) {
      Iterator<int[]> it = map(changes, change -> new int[]{
        change.getStartLine(Side.LEFT),
        change.getEndLine(Side.LEFT),
        change.getStartLine(Side.RIGHT),
        change.getEndLine(Side.RIGHT),
      });

      return computeFoldedRanges(it, settings);
    }

    public void paintOnDivider(@NotNull Graphics2D gg, @NotNull Component divider) {
      myPaintable.paintOnDivider(gg, divider);
    }
  }

  private class MyInitialScrollHelper extends MyInitialScrollPositionHelper {
    @Override
    protected boolean doScrollToChange() {
      if (myScrollToChange == null) return false;
      return SimpleDiffViewer.this.doScrollToChange(myScrollToChange);
    }

    @Override
    protected boolean doScrollToFirstChange() {
      return SimpleDiffViewer.this.doScrollToChange(ScrollToPolicy.FIRST_CHANGE);
    }

    @Override
    protected boolean doScrollToContext() {
      if (myNavigationContext == null) return false;
      return SimpleDiffViewer.this.doScrollToContext(myNavigationContext);
    }
  }
}
