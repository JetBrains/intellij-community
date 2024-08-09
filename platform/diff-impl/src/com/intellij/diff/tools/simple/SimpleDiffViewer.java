// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.simple;

import com.intellij.codeWithMe.ClientId;
import com.intellij.diff.DiffContext;
import com.intellij.diff.actions.AllLinesIterator;
import com.intellij.diff.actions.BufferedLineIterator;
import com.intellij.diff.comparison.DiffTooBigException;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.util.*;
import com.intellij.diff.tools.util.base.TextDiffViewerUtil;
import com.intellij.diff.tools.util.side.TwosideContentPanel;
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer;
import com.intellij.diff.tools.util.text.TwosideTextDiffProvider;
import com.intellij.diff.util.*;
import com.intellij.diff.util.DiffUserDataKeysEx.ScrollToPolicy;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.DiffNavigationContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DirtyUI;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  @NotNull protected final StatusPanel myStatusPanel;

  @NotNull protected SimpleDiffModel myModel = new SimpleDiffModel(this);
  @NotNull private final AlignedDiffModel myAlignedDiffModel;

  @NotNull private final MyFoldingModel myFoldingModel;
  @NotNull private final MyInitialScrollHelper myInitialScrollHelper = new MyInitialScrollHelper();
  @NotNull private final ModifierProvider myModifierProvider;

  @NotNull protected final TwosideTextDiffProvider myTextDiffProvider;

  protected boolean aligningViewModeSupported;


  public SimpleDiffViewer(@NotNull DiffContext context, @NotNull DiffRequest request) {
    super(context, (ContentDiffRequest)request);

    this.aligningViewModeSupported = true;
    mySyncScrollable = new MySyncScrollable();
    myAlignedDiffModel = new SimpleAlignedDiffModel(this);

    myPrevNextDifferenceIterable = new MyPrevNextDifferenceIterable();
    myStatusPanel = new MyStatusPanel();
    myFoldingModel = new MyFoldingModel(getProject(), getEditors(), myContentPanel, this);

    myModifierProvider = new ModifierProvider();

    myTextDiffProvider = DiffUtil.createTextDiffProvider(getProject(), getRequest(), getTextSettings(), this::rediff, this);

    for (Side side : Side.values()) {
      DiffUtil.installLineConvertor(getEditor(side), getContent(side), myFoldingModel, side.getIndex());
    }
  }

  @Override
  @RequiresEdt
  protected void onInit() {
    super.onInit();
    myContentPanel.setPainter(new MyDividerPainter());
    myModifierProvider.init();
  }

  @Override
  protected void onDispose() {
    Disposer.dispose(myAlignedDiffModel);
    super.onDispose();
  }

  @ApiStatus.Internal
  protected void setModel(@NotNull SimpleDiffModel model) {
    this.myModel = model;
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

  @NotNull
  public FoldingModelSupport getFoldingModel() {
    return myFoldingModel;
  }

  @ApiStatus.Internal
  public boolean needAlignChanges() {
    if (!aligningViewModeSupported) return false;
    return myAlignedDiffModel.needAlignChanges();
  }

  @NotNull
  public TwosideTextDiffProvider getTextDiffProvider() {
    return myTextDiffProvider;
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
  protected Runnable apply(@Nullable List<? extends SimpleDiffChange> changes,
                           boolean isContentsEqual) {
    List<SimpleDiffChange> nonSkipped = changes != null ? ContainerUtil.filter(changes, it -> !it.isSkipped()) : null;
    FoldingModelSupport.Data foldingState = myFoldingModel.createState(nonSkipped, getFoldingModelSettings());

    return () -> {
      myFoldingModel.updateContext(myRequest, getFoldingModelSettings());

      clearDiffPresentation();

      if (isContentsEqual &&
          !DiffUtil.isUserDataFlagSet(DiffUserDataKeysEx.DISABLE_CONTENTS_EQUALS_NOTIFICATION, myContext, myRequest)) {
        myPanel.addNotification(TextDiffViewerUtil.createEqualContentsNotification(getContents()));
      }

      myModel.setChanges(ContainerUtil.notNullize(changes), isContentsEqual);
      myAlignedDiffModel.realignChanges();

      //maybe readaction
      WriteIntentReadAction.run((Runnable)() -> myFoldingModel.install(foldingState, myRequest, getFoldingModelSettings()));

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

  protected void clearDiffPresentation() {
    myModel.clear();
    myAlignedDiffModel.clear();

    myPanel.resetNotifications();
    myStatusPanel.setBusy(false);

    myContentPanel.repaintDivider();
    myStatusPanel.update();
  }

  //
  // Impl
  //

  @Override
  @RequiresEdt
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

  @RequiresEdt
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

    if (ClientId.isCurrentlyUnderLocalId()) {
      getSyncScrollSupport().makeVisible(getCurrentSide(), line1, endLine1, line2, endLine2, animated);
    }
    else {
      getCurrentEditor().getScrollingModel().scrollToCaret(ScrollType.CENTER);
    }
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

  public boolean isAligningViewModeSupported() {
    return aligningViewModeSupported;
  }

  //
  // Misc
  //

  boolean isDiffForLocalChanges() {
    boolean isLastWithLocal = DiffUtil.isUserDataFlagSet(DiffUserDataKeysEx.LAST_REVISION_WITH_LOCAL, myContext);
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
  @RequiresEdt
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
  @RequiresEdt
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
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

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

    @RequiresWriteLock
    protected abstract void doPerform(@NotNull AnActionEvent e, @NotNull Side side, @NotNull List<? extends SimpleDiffChange> changes);
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
    protected void doPerform(@NotNull AnActionEvent e, @NotNull Side side, @NotNull List<? extends SimpleDiffChange> changes) {
      if (!isEditable(myModifiedSide)) return;

      String title = DiffBundle.message("message.use.selected.changes.command", e.getPresentation().getText());
      DiffUtil.executeWriteCommand(getEditor(myModifiedSide).getDocument(), e.getProject(), title, () -> apply(changes));
    }

    protected boolean isBothEditable() {
      return isEditable(Side.LEFT) && isEditable(Side.RIGHT);
    }

    @RequiresWriteLock
    protected abstract void apply(@NotNull List<? extends SimpleDiffChange> changes);
  }

  private class ReplaceSelectedChangesAction extends ApplySelectedChangesActionBase {
    ReplaceSelectedChangesAction(@NotNull Side focusedSide) {
      super(focusedSide.other());
      setShortcutSet(ActionManager.getInstance().getAction(focusedSide.select("Diff.ApplyLeftSide", "Diff.ApplyRightSide"))
                       .getShortcutSet());
    }

    @NotNull
    @Override
    protected String getText(@NotNull Side side) {
      return SimpleDiffChangeUi.getApplyActionText(SimpleDiffViewer.this, myModifiedSide.other());
    }

    @Nullable
    @Override
    protected Icon getIcon(@NotNull Side side) {
      return DiffUtil.getArrowIcon(myModifiedSide.other());
    }

    @Override
    protected void apply(@NotNull List<? extends SimpleDiffChange> changes) {
      for (SimpleDiffChange change : changes) {
        replaceChange(change, myModifiedSide.other());
      }
    }
  }

  private class AppendSelectedChangesAction extends ApplySelectedChangesActionBase {
    AppendSelectedChangesAction(@NotNull Side focusedSide) {
      super(focusedSide.other());
      setShortcutSet(ActionManager.getInstance().getAction(focusedSide.select("Diff.AppendLeftSide", "Diff.AppendRightSide"))
                       .getShortcutSet());
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
    protected void apply(@NotNull List<? extends SimpleDiffChange> changes) {
      for (SimpleDiffChange change : changes) {
        appendChange(change, myModifiedSide.other());
      }
    }
  }

  @RequiresWriteLock
  public void replaceChange(@NotNull SimpleDiffChange change, @NotNull final Side sourceSide) {
    if (!change.isValid()) return;
    Side outputSide = sourceSide.other();

    boolean isLocalChangeRevert = sourceSide == Side.LEFT && isDiffForLocalChanges();
    TextDiffViewerUtil.applyModification(getEditor(outputSide).getDocument(),
                                         change.getStartLine(outputSide), change.getEndLine(outputSide),
                                         getEditor(sourceSide).getDocument(),
                                         change.getStartLine(sourceSide), change.getEndLine(sourceSide),
                                         isLocalChangeRevert);

    myModel.destroyChange(change);
  }

  @RequiresWriteLock
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
      super(getTextSettings(), myFoldingModel);
    }
  }

  //
  // Scroll from annotate
  //

  private final class ChangedLinesIterator extends BufferedLineIterator {
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

  @Override
  public @Nullable PrevNextDifferenceIterable getDifferenceIterable() {
    return myPrevNextDifferenceIterable;
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    super.uiDataSnapshot(sink);
    SimpleDiffChange change = getSelectedChange(getCurrentSide());
    if (change != null) {
      sink.set(DiffDataKeys.CURRENT_CHANGE_RANGE, new LineRange(
        change.getStartLine(getCurrentSide()), change.getEndLine(getCurrentSide())));
    }
    sink.set(DiffDataKeys.EDITOR_CHANGED_RANGE_PROVIDER, new MyChangedRangeProvider());
  }

  private class MySyncScrollable extends BaseSyncScrollable {
    @Override
    public boolean isSyncScrollEnabled() {
      return getTextSettings().isEnableSyncScroll() || getTextSettings().isEnableAligningChangesMode();
    }

    @Override
    public boolean forceSyncVerticalScroll() {
      return needAlignChanges();
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

  private class MyDividerPainter implements DiffSplitter.Painter {
    @DirtyUI
    @Override
    public void paint(@NotNull Graphics g, @NotNull JComponent divider) {
      Graphics2D gg = DiffDividerDrawUtil.getDividerGraphics(g, divider, getEditor1().getComponent());

      gg.setColor(DiffDrawUtil.getDividerColor(getEditor1()));
      gg.fill(gg.getClipBounds());

      myModel.paintPolygons(gg, divider);
      myFoldingModel.paintOnDivider(gg, divider);

      gg.dispose();
    }
  }

  private class MyStatusPanel extends StatusPanel {
    @Nullable
    @Override
    protected String getMessage() {
      return getStatusTextMessage();
    }
  }

  @Nullable
  protected @Nls String getStatusTextMessage() {
    if (myTextDiffProvider.isHighlightingDisabled()) {
      return DiffBundle.message("diff.highlighting.disabled.text");
    }
    List<SimpleDiffChange> allChanges = myModel.getAllChanges();
    return DiffUtil.getStatusText(allChanges.size(),
                                  ContainerUtil.count(allChanges, it -> it.isExcluded()),
                                  myModel.isContentsEqual());
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
    private final TwosideContentPanel myContentPanel;

    MyFoldingModel(@Nullable Project project,
                   @NotNull List<? extends EditorEx> editors,
                   @NotNull TwosideContentPanel contentPanel,
                   @NotNull Disposable disposable) {
      super(project, editors.toArray(new EditorEx[0]), disposable);
      myContentPanel = contentPanel;
    }

    @Override
    protected void repaintSeparators() {
      myContentPanel.repaint();
    }

    @Nullable
    public Data createState(@Nullable List<? extends SimpleDiffChange> changes, @NotNull Settings settings) {
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

  private class MyChangedRangeProvider implements DiffChangedRangeProvider {
    @Override
    public @Nullable List<TextRange> getChangedRanges(@NotNull Editor editor) {
      Side side = Side.fromValue(getEditors(), editor);
      if (side == null) return null;

      return ContainerUtil.map(getNonSkippedDiffChanges(), change -> {
        return DiffUtil.getLinesRange(editor.getDocument(), change.getStartLine(side), change.getEndLine(side));
      });
    }
  }
}
