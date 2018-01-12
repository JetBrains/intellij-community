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
import com.intellij.diff.util.DiffUserDataKeysEx.ScrollToPolicy;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.DiffNavigationContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;

import static com.intellij.diff.util.DiffUtil.getLineCount;
import static com.intellij.util.ArrayUtil.toObjectArray;
import static com.intellij.util.ObjectUtils.assertNotNull;

public class SimpleDiffViewer extends TwosideTextDiffViewer {
  @NotNull private final SyncScrollSupport.SyncScrollable mySyncScrollable;
  @NotNull private final PrevNextDifferenceIterable myPrevNextDifferenceIterable;
  @NotNull private final StatusPanel myStatusPanel;

  @NotNull private final List<SimpleDiffChange> myDiffChanges = new ArrayList<>();
  @NotNull private final List<SimpleDiffChange> myNonResolvedDiffChanges = new ArrayList<>();
  @NotNull private final List<SimpleDiffChange> myInvalidDiffChanges = new ArrayList<>();
  private boolean myIsContentsEqual;

  @NotNull private final MyFoldingModel myFoldingModel;
  @NotNull private final MyInitialScrollHelper myInitialScrollHelper = new MyInitialScrollHelper();
  @NotNull private final ModifierProvider myModifierProvider;

  @NotNull protected final TwosideTextDiffProvider myTextDiffProvider;

  public SimpleDiffViewer(@NotNull DiffContext context, @NotNull DiffRequest request) {
    super(context, (ContentDiffRequest)request);

    mySyncScrollable = new MySyncScrollable();
    myPrevNextDifferenceIterable = new MyPrevNextDifferenceIterable();
    myStatusPanel = new MyStatusPanel();
    myFoldingModel = new MyFoldingModel(getEditors(), this);

    myModifierProvider = new ModifierProvider();

    myTextDiffProvider = DiffUtil.createTextDiffProvider(getProject(), getRequest(), getTextSettings(), this::rediff, this);

    for (Side side : Side.values()) {
      DiffUtil.installLineConvertor(getEditor(side), getContent(side), myFoldingModel, side.getIndex());
    }

    DiffUtil.registerAction(new ReplaceSelectedChangesAction(Side.LEFT, true), myPanel);
    DiffUtil.registerAction(new AppendSelectedChangesAction(Side.LEFT, true), myPanel);
    DiffUtil.registerAction(new ReplaceSelectedChangesAction(Side.RIGHT, true), myPanel);
    DiffUtil.registerAction(new AppendSelectedChangesAction(Side.RIGHT, true), myPanel);
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
    destroyChangedBlocks();
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

    group.add(new ReplaceSelectedChangesAction(Side.LEFT, false));
    group.add(new AppendSelectedChangesAction(Side.LEFT, false));
    group.add(new ReplaceSelectedChangesAction(Side.RIGHT, false));
    group.add(new AppendSelectedChangesAction(Side.RIGHT, false));

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

      final Document document1 = getContent1().getDocument();
      final Document document2 = getContent2().getDocument();

      CharSequence[] texts = ReadAction.compute(() -> {
        return new CharSequence[]{document1.getImmutableCharSequence(), document2.getImmutableCharSequence()};
      });

      List<LineFragment> lineFragments = myTextDiffProvider.compare(texts[0], texts[1], indicator);

      boolean isContentsEqual = (lineFragments == null || lineFragments.isEmpty()) &&
                                StringUtil.equals(texts[0], texts[1]);

      return apply(new CompareData(lineFragments, null, isContentsEqual));
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
  protected Runnable apply(@NotNull final CompareData data) {
    return () -> {
      myFoldingModel.updateContext(myRequest, getFoldingModelSettings());

      clearDiffPresentation();

      myIsContentsEqual = data.isContentsEqual();
      if (data.isContentsEqual()) {
        boolean equalCharsets = TextDiffViewerUtil.areEqualCharsets(getContents());
        boolean equalSeparators = TextDiffViewerUtil.areEqualLineSeparators(getContents());
        myPanel.addNotification(DiffNotifications.createEqualContents(equalCharsets, equalSeparators));
      }

      List<LineFragment> fragments = data.getFragments();
      if (fragments != null) {
        for (int i = 0; i < fragments.size(); i++) {
          LineFragment fragment = fragments.get(i);
          LineFragment previousFragment = i != 0 ? fragments.get(i - 1) : null;
          boolean isResolved = data.isResolved(i);

          SimpleDiffChange change = new SimpleDiffChange(this, fragment, previousFragment, isResolved);

          myDiffChanges.add(change);
          if (!change.isResolved()) myNonResolvedDiffChanges.add(change);
        }
      }

      myFoldingModel.install(myNonResolvedDiffChanges, myRequest, getFoldingModelSettings());

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
    myStatusPanel.setBusy(false);
    myPanel.resetNotifications();
    destroyChangedBlocks();
  }

  //
  // Impl
  //

  private void destroyChangedBlocks() {
    myIsContentsEqual = false;

    for (SimpleDiffChange change : myDiffChanges) {
      change.destroyHighlighter();
    }
    myDiffChanges.clear();
    myNonResolvedDiffChanges.clear();

    for (SimpleDiffChange change : myInvalidDiffChanges) {
      change.destroyHighlighter();
    }
    myInvalidDiffChanges.clear();

    myContentPanel.repaintDivider();
    myStatusPanel.update();
  }

  @Override
  @CalledInAwt
  protected void onBeforeDocumentChange(@NotNull DocumentEvent e) {
    super.onBeforeDocumentChange(e);
    if (myDiffChanges.isEmpty()) return;

    List<Document> documents = ContainerUtil.map(getEditors(), Editor::getDocument);
    Side side = Side.fromValue(documents, e.getDocument());
    if (side == null) {
      LOG.warn("Unknown document changed");
      return;
    }

    LineRange lineRange = DiffUtil.getAffectedLineRange(e);
    int shift = DiffUtil.countLinesShift(e);

    List<SimpleDiffChange> invalid = new ArrayList<>();
    for (SimpleDiffChange change : myDiffChanges) {
      if (change.processChange(lineRange.start, lineRange.end, shift, side)) {
        invalid.add(change);
      }
    }

    if (!invalid.isEmpty()) {
      myDiffChanges.removeAll(invalid);
      myInvalidDiffChanges.addAll(invalid);
    }
  }

  @CalledInAwt
  protected boolean doScrollToChange(@NotNull ScrollToPolicy scrollToPolicy) {
    SimpleDiffChange targetChange = scrollToPolicy.select(myNonResolvedDiffChanges);
    if (targetChange == null) targetChange = scrollToPolicy.select(myDiffChanges);
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
  protected List<SimpleDiffChange> getDiffChanges() {
    return myDiffChanges;
  }

  @NotNull
  @Override
  protected SyncScrollSupport.SyncScrollable getSyncScrollable() {
    return mySyncScrollable;
  }

  @NotNull
  @Override
  protected JComponent getStatusPanel() {
    return myStatusPanel;
  }

  @NotNull
  public ModifierProvider getModifierProvider() {
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

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static boolean canShowRequest(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return TwosideTextDiffViewer.canShowRequest(context, request);
  }

  @NotNull
  @CalledInAwt
  private List<SimpleDiffChange> getSelectedChanges(@NotNull Side side) {
    final BitSet lines = DiffUtil.getSelectedLines(getEditor(side));

    List<SimpleDiffChange> affectedChanges = new ArrayList<>();
    for (int i = myDiffChanges.size() - 1; i >= 0; i--) {
      SimpleDiffChange change = myDiffChanges.get(i);
      int line1 = change.getStartLine(side);
      int line2 = change.getEndLine(side);

      if (DiffUtil.isSelectedByLine(lines, line1, line2)) {
        affectedChanges.add(change);
      }
    }
    return affectedChanges;
  }

  @Nullable
  @CalledInAwt
  private SimpleDiffChange getSelectedChange(@NotNull Side side) {
    int caretLine = getEditor(side).getCaretModel().getLogicalPosition().line;

    for (SimpleDiffChange change : myDiffChanges) {
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
      return myNonResolvedDiffChanges;
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
    public MyReadOnlyLockAction() {
      super(getContext(), getEditableEditors());
    }

    @Override
    protected void doApply(boolean readOnly) {
      super.doApply(readOnly);
      for (SimpleDiffChange change : myDiffChanges) {
        change.updateGutterActions(true);
      }
    }
  }

  //
  // Modification operations
  //

  protected abstract class SelectedChangesActionBase extends DumbAwareAction {
    private final boolean myShortcut;

    public SelectedChangesActionBase(boolean shortcut) {
      myShortcut = shortcut;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      if (myShortcut) {
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
      final Side side = assertNotNull(Side.fromValue(getEditors(), editor));
      final List<SimpleDiffChange> selectedChanges = getSelectedChanges(side);
      if (selectedChanges.isEmpty()) return;

      doPerform(e, side, selectedChanges);
    }

    protected boolean isSomeChangeSelected(@NotNull Side side) {
      if (myDiffChanges.isEmpty()) return false;

      EditorEx editor = getEditor(side);
      List<Caret> carets = editor.getCaretModel().getAllCarets();
      if (carets.size() != 1) return true;
      Caret caret = carets.get(0);
      if (caret.hasSelection()) return true;
      int line = editor.getDocument().getLineNumber(editor.getExpectedCaretOffset());

      for (SimpleDiffChange change : myDiffChanges) {
        if (change.isSelectedByLine(line, side)) return true;
      }
      return false;
    }

    protected abstract boolean isVisible(@NotNull Side side);

    @NotNull
    protected abstract String getText(@NotNull Side side);

    @Nullable
    protected abstract Icon getIcon(@NotNull Side side);

    @CalledWithWriteLock
    protected abstract void doPerform(@NotNull AnActionEvent e, @NotNull Side side, @NotNull List<SimpleDiffChange> changes);
  }

  private abstract class ApplySelectedChangesActionBase extends SelectedChangesActionBase {
    @NotNull protected final Side myModifiedSide;

    public ApplySelectedChangesActionBase(@NotNull Side modifiedSide, boolean shortcut) {
      super(shortcut);
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

      String title = e.getPresentation().getText() + " selected changes";
      DiffUtil.executeWriteCommand(getEditor(myModifiedSide).getDocument(), e.getProject(), title, () -> {
        apply(changes);
      });
    }

    protected boolean isBothEditable() {
      return isEditable(Side.LEFT) && isEditable(Side.RIGHT);
    }

    @CalledWithWriteLock
    protected abstract void apply(@NotNull List<SimpleDiffChange> changes);
  }

  private class ReplaceSelectedChangesAction extends ApplySelectedChangesActionBase {
    public ReplaceSelectedChangesAction(@NotNull Side focusedSide, boolean shortcut) {
      super(focusedSide.other(), shortcut);
      setShortcutSet(ActionManager.getInstance().getAction(focusedSide.select("Diff.ApplyLeftSide", "Diff.ApplyRightSide")).getShortcutSet());
    }

    @NotNull
    @Override
    protected String getText(@NotNull Side side) {
      return "Accept";
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
    public AppendSelectedChangesAction(@NotNull Side focusedSide, boolean shortcut) {
      super(focusedSide.other(), shortcut);
      setShortcutSet(ActionManager.getInstance().getAction(focusedSide.select("Diff.AppendLeftSide", "Diff.AppendRightSide")).getShortcutSet());
    }

    @NotNull
    @Override
    protected String getText(@NotNull Side side) {
      return isBothEditable() ? myModifiedSide.select("Append to the Left", "Append to the Right") : "Append";
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

    change.destroyHighlighter();
    myDiffChanges.remove(change);
  }

  @CalledWithWriteLock
  public void appendChange(@NotNull SimpleDiffChange change, @NotNull final Side sourceSide) {
    if (!change.isValid()) return;
    if (change.getStartLine(sourceSide) == change.getEndLine(sourceSide)) return;
    Side outputSide = sourceSide.other();

    DiffUtil.applyModification(getEditor(outputSide).getDocument(), change.getEndLine(outputSide), change.getEndLine(outputSide),
                               getEditor(sourceSide).getDocument(), change.getStartLine(sourceSide), change.getEndLine(sourceSide));

    change.destroyHighlighter();
    myDiffChanges.remove(change);
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
      return myIndex < myDiffChanges.size();
    }

    @Override
    public void loadNextBlock() {
      SimpleDiffChange change = myDiffChanges.get(myIndex);
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
  public Object getData(@NonNls String dataId) {
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

    @Override
    public int transfer(@NotNull Side baseSide, int line) {
      if (myDiffChanges.isEmpty()) {
        return line;
      }

      return super.transfer(baseSide, line);
    }

    @Override
    protected void processHelper(@NotNull ScrollHelper helper) {
      if (!helper.process(0, 0)) return;
      for (SimpleDiffChange diffChange : myDiffChanges) {
        if (!helper.process(diffChange.getStartLine(Side.LEFT), diffChange.getStartLine(Side.RIGHT))) return;
        if (!helper.process(diffChange.getEndLine(Side.LEFT), diffChange.getEndLine(Side.RIGHT))) return;
      }
      helper.process(getLineCount(getEditor1().getDocument()), getLineCount(getEditor2().getDocument()));
    }
  }

  private class MyDividerPainter implements DiffSplitter.Painter, DiffDividerDrawUtil.DividerPaintable {
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
      for (SimpleDiffChange diffChange : myDiffChanges) {
        if (!handler.process(diffChange.getStartLine(Side.LEFT), diffChange.getEndLine(Side.LEFT),
                             diffChange.getStartLine(Side.RIGHT), diffChange.getEndLine(Side.RIGHT),
                             diffChange.getDiffType().getColor(getEditor1()), diffChange.isResolved())) {
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
      int changesCount = myDiffChanges.size() + myInvalidDiffChanges.size();
      if (changesCount == 0 && !myIsContentsEqual) {
        return DiffBundle.message("diff.all.differences.ignored.text");
      }
      return DiffBundle.message("diff.count.differences.status.text", changesCount);
    }
  }

  protected static class CompareData {
    @Nullable private final List<LineFragment> myFragments;
    @Nullable private final BitSet myAreResolved;
    private final boolean myIsContentsEqual;

    public CompareData(@Nullable List<LineFragment> fragments, @Nullable BitSet areResolved, boolean isContentsEqual) {
      myFragments = fragments;
      myAreResolved = areResolved;
      myIsContentsEqual = isContentsEqual;
    }

    @Nullable
    public List<LineFragment> getFragments() {
      return myFragments;
    }

    public boolean isContentsEqual() {
      return myIsContentsEqual;
    }

    public boolean isResolved(int i) {
      return myAreResolved != null && myAreResolved.get(i);
    }
  }

  public class ModifierProvider extends KeyboardModifierListener {
    public void init() {
      init(myPanel, SimpleDiffViewer.this);
    }

    @Override
    public void onModifiersChanged() {
      for (SimpleDiffChange change : myDiffChanges) {
        change.updateGutterActions(false);
      }
    }
  }

  private static class MyFoldingModel extends FoldingModelSupport {
    private final MyPaintable myPaintable = new MyPaintable(0, 1);

    public MyFoldingModel(@NotNull List<? extends EditorEx> editors, @NotNull Disposable disposable) {
      super(toObjectArray(editors, EditorEx.class), disposable);
    }

    public void install(@NotNull List<SimpleDiffChange> changes,
                        @NotNull UserDataHolder context,
                        @NotNull Settings settings) {
      Iterator<int[]> it = map(changes, change -> new int[]{
        change.getStartLine(Side.LEFT),
        change.getEndLine(Side.LEFT),
        change.getStartLine(Side.RIGHT),
        change.getEndLine(Side.RIGHT),
      });

      install(it, context, settings);
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
