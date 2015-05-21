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
import com.intellij.diff.actions.BufferedLineIterator;
import com.intellij.diff.actions.NavigationContextChecker;
import com.intellij.diff.comparison.DiffTooBigException;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.util.*;
import com.intellij.diff.tools.util.base.HighlightPolicy;
import com.intellij.diff.tools.util.base.TextDiffViewerUtil;
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer;
import com.intellij.diff.util.*;
import com.intellij.diff.util.DiffUserDataKeysEx.ScrollToPolicy;
import com.intellij.diff.util.DiffUtil.DocumentData;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffNavigationContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;

import static com.intellij.diff.util.DiffUtil.getLineCount;

public class SimpleDiffViewer extends TwosideTextDiffViewer {
  public static final Logger LOG = Logger.getInstance(SimpleDiffViewer.class);

  @NotNull private final SyncScrollSupport.SyncScrollable mySyncScrollable;
  @NotNull private final PrevNextDifferenceIterable myPrevNextDifferenceIterable;
  @NotNull private final StatusPanel myStatusPanel;

  @NotNull private final List<SimpleDiffChange> myDiffChanges = new ArrayList<SimpleDiffChange>();
  @NotNull private final List<SimpleDiffChange> myInvalidDiffChanges = new ArrayList<SimpleDiffChange>();

  @NotNull private final MyFoldingModel myFoldingModel;
  @NotNull private final MyInitialScrollHelper myInitialScrollHelper = new MyInitialScrollHelper();
  @NotNull private final ModifierProvider myModifierProvider;

  public SimpleDiffViewer(@NotNull DiffContext context, @NotNull DiffRequest request) {
    super(context, (ContentDiffRequest)request);

    mySyncScrollable = new MySyncScrollable();
    myPrevNextDifferenceIterable = new MyPrevNextDifferenceIterable();
    myStatusPanel = new MyStatusPanel();
    myFoldingModel = new MyFoldingModel(getEditors(), this);

    myModifierProvider = new ModifierProvider();
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
    super.onDispose();
  }

  @NotNull
  @Override
  protected List<AnAction> createToolbarActions() {
    List<AnAction> group = new ArrayList<AnAction>();

    group.add(new MyIgnorePolicySettingAction());
    group.add(new MyHighlightPolicySettingAction());
    group.add(new MyToggleExpandByDefaultAction());
    group.add(new MyToggleAutoScrollAction());
    group.add(new MyReadOnlyLockAction());
    group.add(myEditorSettingsAction);

    return group;
  }

  @Nullable
  @Override
  protected List<AnAction> createPopupActions() {
    List<AnAction> group = new ArrayList<AnAction>();

    group.add(Separator.getInstance());
    group.add(new MyIgnorePolicySettingAction().getPopupGroup());
    group.add(Separator.getInstance());
    group.add(new MyHighlightPolicySettingAction().getPopupGroup());
    group.add(Separator.getInstance());
    group.add(new MyToggleAutoScrollAction());
    group.add(new MyToggleExpandByDefaultAction());

    return group;
  }

  @NotNull
  @Override
  protected List<AnAction> createEditorPopupActions() {
    List<AnAction> group = new ArrayList<AnAction>();

    group.add(new ReplaceSelectedChangesAction());
    group.add(new AppendSelectedChangesAction());
    group.add(new RevertSelectedChangesAction());
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

      DocumentData data = ApplicationManager.getApplication().runReadAction(new Computable<DocumentData>() {
        @Override
        public DocumentData compute() {
          return new DocumentData(document1.getImmutableCharSequence(), document2.getImmutableCharSequence(),
                                  document1.getModificationStamp(), document2.getModificationStamp());
        }
      });

      List<LineFragment> lineFragments = null;
      if (getHighlightPolicy().isShouldCompare()) {
        lineFragments = DiffUtil.compareWithCache(myRequest, data, getDiffConfig(), indicator);
      }

      boolean isEqualContents = (lineFragments == null || lineFragments.isEmpty()) &&
                                StringUtil.equals(document1.getCharsSequence(), document2.getCharsSequence());

      return apply(new CompareData(lineFragments, isEqualContents));
    }
    catch (DiffTooBigException ignore) {
      return applyNotification(DiffNotifications.DIFF_TOO_BIG);
    }
    catch (ProcessCanceledException ignore) {
      return applyNotification(DiffNotifications.OPERATION_CANCELED);
    }
    catch (Throwable e) {
      LOG.error(e);
      return applyNotification(DiffNotifications.ERROR);
    }
  }

  @NotNull
  private Runnable apply(@NotNull final CompareData data) {
    return new Runnable() {
      @Override
      public void run() {
        myFoldingModel.updateContext(myRequest, getFoldingModelSettings());
        clearDiffPresentation();

        if (data.isEqualContent()) myPanel.addNotification(DiffNotifications.EQUAL_CONTENTS);

        if (data.getFragments() != null) {
          for (LineFragment fragment : data.getFragments()) {
            myDiffChanges.add(new SimpleDiffChange(SimpleDiffViewer.this, fragment, getHighlightPolicy().isFineFragments()));
          }
        }

        myFoldingModel.install(data.getFragments(), myRequest, getFoldingModelSettings());

        myInitialScrollHelper.onRediff();

        myContentPanel.repaintDivider();
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

  @NotNull
  private DiffUtil.DiffConfig getDiffConfig() {
    return new DiffUtil.DiffConfig(getTextSettings().getIgnorePolicy(), getHighlightPolicy());
  }

  @NotNull
  private HighlightPolicy getHighlightPolicy() {
    return getTextSettings().getHighlightPolicy();
  }

  //
  // Impl
  //

  private void destroyChangedBlocks() {
    for (SimpleDiffChange change : myDiffChanges) {
      change.destroyHighlighter();
    }
    myDiffChanges.clear();

    for (SimpleDiffChange change : myInvalidDiffChanges) {
      change.destroyHighlighter();
    }
    myInvalidDiffChanges.clear();

    myFoldingModel.destroy();

    myContentPanel.repaintDivider();
    myStatusPanel.update();
  }

  @Override
  @CalledInAwt
  protected void onBeforeDocumentChange(@NotNull DocumentEvent e) {
    super.onBeforeDocumentChange(e);
    if (myDiffChanges.isEmpty()) return;

    Side side = null;
    if (e.getDocument() == getEditor(Side.LEFT).getDocument()) side = Side.LEFT;
    if (e.getDocument() == getEditor(Side.RIGHT).getDocument()) side = Side.RIGHT;
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

    List<SimpleDiffChange> invalid = new ArrayList<SimpleDiffChange>();
    for (SimpleDiffChange change : myDiffChanges) {
      if (change.processChange(line1, line2, shift, side)) {
        invalid.add(change);
      }
    }

    if (!invalid.isEmpty()) {
      myDiffChanges.removeAll(invalid);
      myInvalidDiffChanges.addAll(invalid);
    }
  }

  @Override
  protected void onDocumentChange(@NotNull DocumentEvent e) {
    super.onDocumentChange(e);
    myFoldingModel.onDocumentChanged(e);
  }

  @CalledInAwt
  protected boolean doScrollToChange(@NotNull ScrollToPolicy scrollToPolicy) {
    SimpleDiffChange targetChange = scrollToPolicy.select(myDiffChanges);
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
    ChangedLinesIterator changedLinesIterator = new ChangedLinesIterator(Side.RIGHT);
    NavigationContextChecker checker = new NavigationContextChecker(changedLinesIterator, context);
    int line = checker.contextMatchCheck();
    if (line == -1) {
      // this will work for the case, when spaces changes are ignored, and corresponding fragments are not reported as changed
      // just try to find target line  -> +-
      AllLinesIterator allLinesIterator = new AllLinesIterator(Side.RIGHT);
      NavigationContextChecker checker2 = new NavigationContextChecker(allLinesIterator, context);
      line = checker2.contextMatchCheck();
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

    List<SimpleDiffChange> affectedChanges = new ArrayList<SimpleDiffChange>();
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

  private class MyPrevNextDifferenceIterable implements PrevNextDifferenceIterable {
    @Override
    public boolean canGoNext() {
      if (myDiffChanges.isEmpty()) return false;

      EditorEx editor = getCurrentEditor();
      int line = editor.getCaretModel().getLogicalPosition().line;
      if (line == editor.getDocument().getLineCount() - 1) return false;

      SimpleDiffChange lastChange = myDiffChanges.get(myDiffChanges.size() - 1);
      if (lastChange.getStartLine(getCurrentSide()) <= line) return false;

      return true;
    }

    @Override
    public void goNext() {
      int line = getCurrentEditor().getCaretModel().getLogicalPosition().line;

      SimpleDiffChange next = null;
      for (int i = 0; i < myDiffChanges.size(); i++) {
        SimpleDiffChange change = myDiffChanges.get(i);
        if (change.getStartLine(getCurrentSide()) <= line) continue;

        next = change;
        break;
      }

      assert next != null;
      doScrollToChange(next, true);
    }

    @Override
    public boolean canGoPrev() {
      if (myDiffChanges.isEmpty()) return false;

      int line = getCurrentEditor().getCaretModel().getLogicalPosition().line;
      if (line == 0) return false;

      SimpleDiffChange firstChange = myDiffChanges.get(0);
      if (firstChange.getEndLine(getCurrentSide()) > line) return false;
      if (firstChange.getStartLine(getCurrentSide()) >= line) return false;

      return true;
    }

    @Override
    public void goPrev() {
      int line = getCurrentEditor().getCaretModel().getLogicalPosition().line;

      SimpleDiffChange prev = null;
      for (int i = 0; i < myDiffChanges.size(); i++) {
        SimpleDiffChange change = myDiffChanges.get(i);

        SimpleDiffChange next = i < myDiffChanges.size() - 1 ? myDiffChanges.get(i + 1) : null;
        if (next == null || next.getEndLine(getCurrentSide()) > line || next.getStartLine(getCurrentSide()) >= line) {
          prev = change;
          break;
        }
      }

      assert prev != null;
      doScrollToChange(prev, true);
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

  private abstract class ApplySelectedChangesActionBase extends AnAction implements DumbAware {
    private final boolean myModifyOpposite;

    public ApplySelectedChangesActionBase(@Nullable String text,
                                          @Nullable String description,
                                          @Nullable Icon icon,
                                          boolean modifyOpposite) {
      super(text, description, icon);
      myModifyOpposite = modifyOpposite;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Editor editor = e.getData(CommonDataKeys.EDITOR);
      if (editor != getEditor1() && editor != getEditor2()) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }

      Side side = Side.fromLeft(editor == getEditor(Side.LEFT));
      Editor modifiedEditor = getEditor(side.other(myModifyOpposite));

      if (!DiffUtil.isEditable(modifiedEditor)) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }

      e.getPresentation().setIcon(getIcon(side));
      e.getPresentation().setEnabled(isSomeChangeSelected(side));
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent e) {
      Editor editor = e.getRequiredData(CommonDataKeys.EDITOR);
      final Side side = Side.fromLeft(editor == getEditor(Side.LEFT));
      final List<SimpleDiffChange> selectedChanges = getSelectedChanges(side);

      Editor modifiedEditor = getEditor(side.other(myModifyOpposite));
      String title = e.getPresentation().getText() + " selected changes";
      DiffUtil.executeWriteCommand(modifiedEditor.getDocument(), e.getProject(), title, new Runnable() {
        @Override
        public void run() {
          apply(side, selectedChanges);
        }
      });
    }

    protected boolean isSomeChangeSelected(@NotNull Side side) {
      if (myDiffChanges.isEmpty()) return false;

      List<Caret> carets = getEditor(side).getCaretModel().getAllCarets();
      if (carets.size() != 1) return true;
      Caret caret = carets.get(0);
      if (caret.hasSelection()) return true;
      int line = caret.getLogicalPosition().line;

      for (SimpleDiffChange change : myDiffChanges) {
        if (change.isSelectedByLine(line, side)) return true;
      }
      return false;
    }

    @NotNull
    protected abstract Icon getIcon(@NotNull Side side);

    @CalledWithWriteLock
    protected abstract void apply(@NotNull Side side, @NotNull List<SimpleDiffChange> changes);
  }

  private class ReplaceSelectedChangesAction extends ApplySelectedChangesActionBase {
    public ReplaceSelectedChangesAction() {
      super("Replace", null, AllIcons.Diff.Arrow, true);
    }

    @NotNull
    @Override
    protected Icon getIcon(@NotNull Side side) {
      return side.isLeft() ? AllIcons.Diff.ArrowRight : AllIcons.Diff.Arrow;
    }

    @Override
    protected void apply(@NotNull Side side, @NotNull List<SimpleDiffChange> changes) {
      for (SimpleDiffChange change : changes) {
        replaceChange(change, side);
      }
    }
  }

  private class AppendSelectedChangesAction extends ApplySelectedChangesActionBase {
    public AppendSelectedChangesAction() {
      super("Insert", null, AllIcons.Diff.ArrowLeftDown, true);
    }

    @NotNull
    @Override
    protected Icon getIcon(@NotNull Side side) {
      return side.isLeft() ? AllIcons.Diff.ArrowRightDown : AllIcons.Diff.ArrowLeftDown;
    }

    @Override
    protected void apply(@NotNull Side side, @NotNull List<SimpleDiffChange> changes) {
      for (SimpleDiffChange change : changes) {
        appendChange(change, side);
      }
    }
  }

  private class RevertSelectedChangesAction extends ApplySelectedChangesActionBase {
    public RevertSelectedChangesAction() {
      super("Revert", null, AllIcons.Diff.Remove, false);
    }

    @NotNull
    @Override
    protected Icon getIcon(@NotNull Side side) {
      return AllIcons.Diff.Remove;
    }

    @Override
    protected void apply(@NotNull Side side, @NotNull List<SimpleDiffChange> changes) {
      for (SimpleDiffChange change : changes) {
        replaceChange(change, side.other());
      }
    }
  }

  @CalledWithWriteLock
  public void replaceChange(@NotNull SimpleDiffChange change, @NotNull final Side sourceSide) {
    if (!change.isValid()) return;

    final Document document1 = getEditor1().getDocument();
    final Document document2 = getEditor2().getDocument();

    DiffUtil.applyModification(sourceSide.other().select(document1, document2),
                               change.getStartLine(sourceSide.other()), change.getEndLine(sourceSide.other()),
                               sourceSide.select(document1, document2),
                               change.getStartLine(sourceSide), change.getEndLine(sourceSide));

    change.destroyHighlighter();
    myDiffChanges.remove(change);
  }

  @CalledWithWriteLock
  public void appendChange(@NotNull SimpleDiffChange change, @NotNull final Side sourceSide) {
    if (!change.isValid()) return;
    if (change.getStartLine(sourceSide) == change.getEndLine(sourceSide)) return;

    final Document document1 = getEditor1().getDocument();
    final Document document2 = getEditor2().getDocument();

    DiffUtil.applyModification(sourceSide.other().select(document1, document2),
                               change.getEndLine(sourceSide.other()), change.getEndLine(sourceSide.other()),
                               sourceSide.select(document1, document2),
                               change.getStartLine(sourceSide), change.getEndLine(sourceSide));

    change.destroyHighlighter();
    myDiffChanges.remove(change);
  }

  private class MyHighlightPolicySettingAction extends TextDiffViewerUtil.HighlightPolicySettingAction {
    public MyHighlightPolicySettingAction() {
      super(getTextSettings());
    }

    @Override
    protected void onSettingsChanged() {
      rediff();
    }
  }

  private class MyIgnorePolicySettingAction extends TextDiffViewerUtil.IgnorePolicySettingAction {
    public MyIgnorePolicySettingAction() {
      super(getTextSettings());
    }

    @Override
    protected void onSettingsChanged() {
      rediff();
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

  //
  // Scroll from annotate
  //

  private class AllLinesIterator implements Iterator<Pair<Integer, CharSequence>> {
    @NotNull private final Side mySide;
    @NotNull private final Document myDocument;
    private int myLine = 0;

    private AllLinesIterator(@NotNull Side side) {
      mySide = side;

      myDocument = getEditor(mySide).getDocument();
    }

    @Override
    public boolean hasNext() {
      return myLine < getLineCount(myDocument);
    }

    @Override
    public Pair<Integer, CharSequence> next() {
      int offset1 = myDocument.getLineStartOffset(myLine);
      int offset2 = myDocument.getLineEndOffset(myLine);

      CharSequence text = myDocument.getImmutableCharSequence().subSequence(offset1, offset2);

      Pair<Integer, CharSequence> pair = new Pair<Integer, CharSequence>(myLine, text);
      myLine++;

      return pair;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  private class ChangedLinesIterator extends BufferedLineIterator {
    @NotNull private final Side mySide;
    private int myIndex = 0;

    private ChangedLinesIterator(@NotNull Side side) {
      mySide = side;
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

      int line1 = change.getStartLine(mySide);
      int line2 = change.getEndLine(mySide);

      Document document = getEditor(mySide).getDocument();

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
      helper.process(getEditor1().getDocument().getLineCount(), getEditor2().getDocument().getLineCount());
    }
  }

  private class MyDividerPainter implements DiffSplitter.Painter, DiffDividerDrawUtil.DividerPaintable {
    @Override
    public void paint(@NotNull Graphics g, @NotNull JComponent divider) {
      Graphics2D gg = DiffDividerDrawUtil.getDividerGraphics(g, divider, getEditor1().getComponent());

      gg.setColor(DiffDrawUtil.getDividerColor(getEditor1()));
      gg.fill(gg.getClipBounds());

      //DividerPolygonUtil.paintSimplePolygons(gg, divider.getWidth(), getEditor1(), getEditor2(), this);
      DiffDividerDrawUtil.paintPolygons(gg, divider.getWidth(), getEditor1(), getEditor2(), this);

      myFoldingModel.paintOnDivider(gg, divider);

      gg.dispose();
    }

    @Override
    public void process(@NotNull Handler handler) {
      for (SimpleDiffChange diffChange : myDiffChanges) {
        if (!handler.process(diffChange.getStartLine(Side.LEFT), diffChange.getEndLine(Side.LEFT),
                             diffChange.getStartLine(Side.RIGHT), diffChange.getEndLine(Side.RIGHT),
                             diffChange.getDiffType().getColor(getEditor1()))) {
          return;
        }
      }
    }
  }

  private class MyStatusPanel extends StatusPanel {
    @Override
    protected int getChangesCount() {
      return myDiffChanges.size() + myInvalidDiffChanges.size();
    }
  }

  private static class CompareData {
    @Nullable private final List<LineFragment> myFragments;
    private final boolean myEqualContent;

    public CompareData(@Nullable List<LineFragment> fragments, boolean equalContent) {
      myFragments = fragments;
      myEqualContent = equalContent;
    }

    @Nullable
    public List<LineFragment> getFragments() {
      return myFragments;
    }

    public boolean isEqualContent() {
      return myEqualContent;
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
      super(editors.toArray(new EditorEx[2]), disposable);
    }

    public void install(@Nullable final List<LineFragment> fragments,
                        @NotNull UserDataHolder context,
                        @NotNull FoldingModelSupport.Settings settings) {
      Iterator<int[]> it = map(fragments, new Function<LineFragment, int[]>() {
        @Override
        public int[] fun(LineFragment fragment) {
          return new int[]{
            fragment.getStartLine1(),
            fragment.getEndLine1(),
            fragment.getStartLine2(),
            fragment.getEndLine2()};
        }
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
