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
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.fragments.LineFragmentImpl;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.util.*;
import com.intellij.diff.tools.util.base.HighlightPolicy;
import com.intellij.diff.tools.util.twoside.TwosideTextDiffViewer;
import com.intellij.diff.util.DiffDividerDrawUtil;
import com.intellij.diff.util.DiffDrawUtil;
import com.intellij.diff.util.DiffUserDataKeysEx.ScrollToPolicy;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.DiffUtil.DocumentData;
import com.intellij.diff.util.Side;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeEventQueue;
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
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.util.*;
import java.util.List;

import static com.intellij.diff.util.DiffUtil.getLineCount;

public class SimpleDiffViewer extends TwosideTextDiffViewer {
  public static final Logger LOG = Logger.getInstance(SimpleDiffViewer.class);

  @NotNull private final SyncScrollSupport.SyncScrollable mySyncScrollable;
  @NotNull private final PrevNextDifferenceIterable myPrevNextDifferenceIterable;
  @NotNull private final StatusPanel myStatusPanel;

  @NotNull private final List<SimpleDiffChange> myDiffChanges = new ArrayList<SimpleDiffChange>();
  @NotNull private final List<SimpleDiffChange> myInvalidDiffChanges = new ArrayList<SimpleDiffChange>();

  @Nullable private final MyFoldingModel myFoldingModel;
  @NotNull private final ModifierProvider myModifierProvider;

  public SimpleDiffViewer(@NotNull DiffContext context, @NotNull DiffRequest request) {
    super(context, (ContentDiffRequest)request);

    mySyncScrollable = new MySyncScrollable();
    myPrevNextDifferenceIterable = new MyPrevNextDifferenceIterable();
    myStatusPanel = new MyStatusPanel();
    myFoldingModel = createFoldingModel(myEditor1, myEditor2);

    myModifierProvider = new ModifierProvider();
  }

  @Override
  protected void onInit() {
    super.onInit();
    myContentPanel.setPainter(new MyDividerPainter());
    myModifierProvider.init();
  }

  @Override
  protected void onDisposeAwt() {
    myModifierProvider.destroy();
    destroyChangedBlocks();
    super.onDisposeAwt();
  }

  @NotNull
  @Override
  protected List<AnAction> createToolbarActions() {
    List<AnAction> group = new ArrayList<AnAction>();

    group.add(new IgnorePolicySettingAction());
    group.add(new HighlightPolicySettingAction());
    group.add(new MyToggleExpandByDefaultAction());
    group.add(new ToggleAutoScrollAction());
    group.add(new MyReadOnlyLockAction());
    group.add(myEditorSettingsAction);

    return group;
  }

  @Nullable
  @Override
  protected List<AnAction> createPopupActions() {
    List<AnAction> group = new ArrayList<AnAction>();

    group.add(Separator.getInstance());
    group.add(new IgnorePolicySettingAction().getPopupGroup());
    group.add(Separator.getInstance());
    group.add(new HighlightPolicySettingAction().getPopupGroup());
    group.add(Separator.getInstance());
    group.add(new ToggleAutoScrollAction());
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

  @Nullable
  private MyFoldingModel createFoldingModel(@Nullable EditorEx editor1, @Nullable EditorEx editor2) {
    if (editor1 == null || editor2 == null) return null;

    return new MyFoldingModel(editor1, editor2, this);
  }

  @Override
  protected void updateContextHints() {
    super.updateContextHints();
    if (myFoldingModel != null) myFoldingModel.updateContext(myRequest, getTextSettings().isExpandByDefault());
  }

  //
  // Diff
  //

  @Override
  protected void onSlowRediff() {
    super.onSlowRediff();
    myStatusPanel.setBusy(true);
  }

  @Override
  @NotNull
  protected Runnable performRediff(@NotNull final ProgressIndicator indicator) {
    try {
      indicator.checkCanceled();

      assert myActualContent1 != null || myActualContent2 != null;

      if (myActualContent1 == null) {
        final DocumentContent content = myActualContent2;
        final Document document = content.getDocument();

        CompareData data = ApplicationManager.getApplication().runReadAction(new Computable<CompareData>() {
          @Override
          public CompareData compute() {
            List<LineFragment> fragments = Collections.<LineFragment>singletonList(new LineFragmentImpl(0, 0, 0, getLineCount(document),
                                                                                                        0, 0, 0, document.getTextLength()));
            return new CompareData(fragments, false, 0, document.getModificationStamp());
          }
        });

        return apply(data);
      }

      if (myActualContent2 == null) {
        final DocumentContent content = myActualContent1;
        final Document document = content.getDocument();

        CompareData data = ApplicationManager.getApplication().runReadAction(new Computable<CompareData>() {
          @Override
          public CompareData compute() {
            List<LineFragment> fragments = Collections.<LineFragment>singletonList(new LineFragmentImpl(0, getLineCount(document), 0, 0,
                                                                                                        0, document.getTextLength(), 0, 0));
            return new CompareData(fragments, false, document.getModificationStamp(), 0);
          }
        });

        return apply(data);
      }

      final DocumentContent content1 = myActualContent1;
      final DocumentContent content2 = myActualContent2;
      final Document document1 = content1.getDocument();
      final Document document2 = content2.getDocument();

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

      return apply(new CompareData(lineFragments, isEqualContents, data.getStamp1(), data.getStamp2()));
    }
    catch (DiffTooBigException ignore) {
      return new Runnable() {
        @Override
        public void run() {
          clearDiffPresentation();
          myPanel.addTooBigContentNotification();
        }
      };
    }
    catch (ProcessCanceledException ignore) {
      return new Runnable() {
        @Override
        public void run() {
          clearDiffPresentation();
          myPanel.addOperationCanceledNotification();
        }
      };
    }
    catch (Throwable e) {
      LOG.error(e);
      return new Runnable() {
        @Override
        public void run() {
          clearDiffPresentation();
          myPanel.addDiffErrorNotification();
        }
      };
    }
  }

  @NotNull
  private Runnable apply(@NotNull final CompareData data) {
    return new Runnable() {
      @Override
      public void run() {
        if (myEditor1 != null && myEditor1.getDocument().getModificationStamp() != data.getStamp1()) return;
        if (myEditor2 != null && myEditor2.getDocument().getModificationStamp() != data.getStamp2()) return;

        if (myFoldingModel != null) myFoldingModel.updateContext(myRequest, getTextSettings().isExpandByDefault());
        clearDiffPresentation();

        if (data.isEqualContent()) myPanel.addContentsEqualNotification();

        if (data.getFragments() != null) {
          for (LineFragment fragment : data.getFragments()) {
            myDiffChanges.add(new SimpleDiffChange(SimpleDiffViewer.this, fragment, myEditor1, myEditor2,
                                                   getHighlightPolicy().isFineFragments()));
          }
        }

        if (myFoldingModel != null) {
          myFoldingModel.install(data.getFragments(), myRequest,
                                 getTextSettings().isExpandByDefault(), getTextSettings().getContextRange());
        }

        scrollOnRediff();

        myContentPanel.repaintDivider();
        myStatusPanel.update();
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

    if (myFoldingModel != null) myFoldingModel.destroy();

    myContentPanel.repaintDivider();
    myStatusPanel.update();
  }

  @Override
  @CalledInAwt
  protected void onBeforeDocumentChange(@NotNull DocumentEvent e) {
    super.onBeforeDocumentChange(e);
    if (myDiffChanges.isEmpty()) return;
    if (myEditor1 == null || myEditor2 == null) return;

    Side side;
    if (e.getDocument() == myEditor1.getDocument()) {
      side = Side.LEFT;
    }
    else if (e.getDocument() == myEditor2.getDocument()) {
      side = Side.RIGHT;
    }
    else {
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
    if (myFoldingModel != null) myFoldingModel.onDocumentChanged(e);
  }

  @CalledInAwt
  @Override
  protected boolean doScrollToChange(@NotNull ScrollToPolicy scrollToPolicy) {
    if (myDiffChanges.isEmpty()) return false;

    SimpleDiffChange targetChange;
    switch (scrollToPolicy) {
      case FIRST_CHANGE:
        targetChange = myDiffChanges.get(0);
        break;
      case LAST_CHANGE:
        targetChange = myDiffChanges.get(myDiffChanges.size() - 1);
        break;
      default:
        throw new IllegalArgumentException(scrollToPolicy.name());
    }

    EditorEx editor = getCurrentEditor();
    int line = targetChange.getStartLine(getCurrentSide());
    DiffUtil.scrollEditor(editor, line);

    return true;
  }

  @Override
  protected boolean doScrollToContext(@NotNull DiffNavigationContext context) {
    if (myEditor2 == null) return false;

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
    EditorEx editor = side.select(myEditor1, myEditor2);
    if (editor == null) return Collections.emptyList();

    final BitSet lines = DiffUtil.getSelectedLines(editor);
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
      EditorEx editor = getCurrentEditor();

      int line = editor.getCaretModel().getLogicalPosition().line;

      SimpleDiffChange next = null;
      for (int i = 0; i < myDiffChanges.size(); i++) {
        SimpleDiffChange change = myDiffChanges.get(i);
        if (change.getStartLine(getCurrentSide()) <= line) continue;

        next = change;
        break;
      }

      assert next != null;

      DiffUtil.scrollToLineAnimated(editor, next.getStartLine(getCurrentSide()));
    }

    @Override
    public boolean canGoPrev() {
      if (myDiffChanges.isEmpty()) return false;

      EditorEx editor = getCurrentEditor();
      int line = editor.getCaretModel().getLogicalPosition().line;
      if (line == 0) return false;

      SimpleDiffChange firstChange = myDiffChanges.get(0);
      if (firstChange.getEndLine(getCurrentSide()) > line) return false;
      if (firstChange.getStartLine(getCurrentSide()) >= line) return false;

      return true;
    }

    @Override
    public void goPrev() {
      EditorEx editor = getCurrentEditor();

      int line = editor.getCaretModel().getLogicalPosition().line;

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

      DiffUtil.scrollToLineAnimated(editor, prev.getStartLine(getCurrentSide()));
    }
  }

  private class MyReadOnlyLockAction extends ReadOnlyLockAction {
    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      super.setSelected(e, state);
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
      Side side = Side.fromLeft(editor == myEditor1);

      if (myEditor1 == null || myEditor2 == null) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }

      if (editor != myEditor1 && editor != myEditor2) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }

      Editor modifiedEditor = side.other(myModifyOpposite).select(myEditor1, myEditor2);
      if (!DiffUtil.isEditable(modifiedEditor)) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }

      e.getPresentation().setIcon(getIcon(side));
      e.getPresentation().setEnabled(isSomeChangeSelected(side));
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent e) {
      assert myEditor1 != null && myEditor2 != null;

      Editor editor = e.getRequiredData(CommonDataKeys.EDITOR);
      final Side side = Side.fromLeft(editor == myEditor1);
      final List<SimpleDiffChange> selectedChanges = getSelectedChanges(side);

      Editor modifiedEditor = side.other(myModifyOpposite).select(myEditor1, myEditor2);
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

      Editor editor = side.select(myEditor1, myEditor2);
      if (editor == null) return false;

      List<Caret> carets = editor.getCaretModel().getAllCarets();
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
        change.replaceChange(side);
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
        change.appendChange(side);
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
        change.replaceChange(side.other());
      }
    }
  }

  private class MyToggleExpandByDefaultAction extends ToggleExpandByDefaultAction {
    @Override
    protected void expandAll(boolean expand) {
      if (myFoldingModel != null) myFoldingModel.expandAll(expand);
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

      Editor editor = mySide.select(myEditor1, myEditor2);
      assert editor != null;
      myDocument = editor.getDocument();
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

      Editor editor = mySide.select(myEditor1, myEditor2);
      assert editor != null;
      Document document = editor.getDocument();

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
    else {
      return super.getData(dataId);
    }
  }

  private class MySyncScrollable extends BaseSyncScrollable {
    @Override
    public boolean isSyncScrollEnabled() {
      return getTextSettings().isEnableSyncScroll();
    }

    public int transfer(@NotNull Side side, int line) {
      if (myDiffChanges.isEmpty()) {
        return line;
      }

      return super.transfer(side, line);
    }

    @Override
    protected void processHelper(@NotNull ScrollHelper helper) {
      assert myEditor1 != null && myEditor2 != null;

      if (!helper.process(0, 0)) return;
      for (SimpleDiffChange diffChange : myDiffChanges) {
        if (!helper.process(diffChange.getStartLine(Side.LEFT), diffChange.getStartLine(Side.RIGHT))) return;
        if (!helper.process(diffChange.getEndLine(Side.LEFT), diffChange.getEndLine(Side.RIGHT))) return;
      }
      helper.process(myEditor1.getDocument().getLineCount(), myEditor2.getDocument().getLineCount());
    }
  }

  private class MyDividerPainter implements DiffSplitter.Painter, DiffDividerDrawUtil.DividerPaintable {
    @Override
    public void paint(@NotNull Graphics g, @NotNull JComponent divider) {
      if (myEditor1 == null || myEditor2 == null) return;
      Graphics2D gg = getDividerGraphics(g, divider);

      gg.setColor(DiffDrawUtil.getDividerColor(myEditor1));
      gg.fill(gg.getClipBounds());

      //DividerPolygonUtil.paintSimplePolygons(gg, divider.getWidth(), myEditor1, myEditor2, this);
      DiffDividerDrawUtil.paintPolygons(gg, divider.getWidth(), myEditor1, myEditor2, this);

      if (myFoldingModel != null) myFoldingModel.paintOnDivider(gg, divider);

      gg.dispose();
    }

    @Override
    public void process(@NotNull Handler handler) {
      for (SimpleDiffChange diffChange : myDiffChanges) {
        if (!handler.process(diffChange.getStartLine(Side.LEFT), diffChange.getEndLine(Side.LEFT),
                             diffChange.getStartLine(Side.RIGHT), diffChange.getEndLine(Side.RIGHT),
                             diffChange.getDiffType().getColor(myEditor1))) {
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
    private final long myStamp1;
    private final long myStamp2;

    public CompareData(@Nullable List<LineFragment> fragments, boolean equalContent, long stamp1, long stamp2) {
      myFragments = fragments;
      myEqualContent = equalContent;
      myStamp1 = stamp1;
      myStamp2 = stamp2;
    }

    @Nullable
    public List<LineFragment> getFragments() {
      return myFragments;
    }

    public boolean isEqualContent() {
      return myEqualContent;
    }

    public long getStamp1() {
      return myStamp1;
    }

    public long getStamp2() {
      return myStamp2;
    }
  }

  public class ModifierProvider {
    private boolean myShiftPressed;
    private boolean myCtrlPressed;
    private boolean myAltPressed;

    private Window myWindow;

    private final WindowFocusListener myWindowFocusListener = new WindowFocusListener() {
      @Override
      public void windowGainedFocus(WindowEvent e) {
        resetState();
      }

      @Override
      public void windowLostFocus(WindowEvent e) {
        resetState();
      }
    };

    public void init() {
      // we can use KeyListener on Editors, but Ctrl+Click will not work with focus in other place.
      // ex: commit dialog with focus in commit message
      IdeEventQueue.getInstance().addPostprocessor(new IdeEventQueue.EventDispatcher() {
        @Override
        public boolean dispatch(AWTEvent e) {
          if (e instanceof KeyEvent) {
            onKeyEvent((KeyEvent)e);
          }
          return false;
        }
      }, SimpleDiffViewer.this);

      myWindow = UIUtil.getWindow(myPanel);
      if (myWindow != null) {
        myWindow.addWindowFocusListener(myWindowFocusListener);
      }
    }

    public void destroy() {
      if (myWindow != null) {
        myWindow.removeWindowFocusListener(myWindowFocusListener);
      }
    }

    private void onKeyEvent(KeyEvent e) {
      final int keyCode = e.getKeyCode();
      if (keyCode == KeyEvent.VK_SHIFT) {
        myShiftPressed = e.getID() == KeyEvent.KEY_PRESSED;
        updateActions();
      }
      if (keyCode == KeyEvent.VK_CONTROL) {
        myCtrlPressed = e.getID() == KeyEvent.KEY_PRESSED;
        updateActions();
      }
      if (keyCode == KeyEvent.VK_ALT) {
        myAltPressed = e.getID() == KeyEvent.KEY_PRESSED;
        updateActions();
      }
    }

    private void resetState() {
      myShiftPressed = false;
      myAltPressed = false;
      myCtrlPressed = false;
      updateActions();
    }

    public boolean isShiftPressed() {
      return myShiftPressed;
    }

    public boolean isCtrlPressed() {
      return myCtrlPressed;
    }

    public boolean isAltPressed() {
      return myAltPressed;
    }

    public void updateActions() {
      for (SimpleDiffChange change : myDiffChanges) {
        change.updateGutterActions(false);
      }
    }
  }

  private static class MyFoldingModel extends FoldingModelSupport {
    private final MyPaintable myPaintable = new MyPaintable(0, 1);

    public MyFoldingModel(@NotNull EditorEx editor1, @NotNull EditorEx editor2, @NotNull Disposable disposable) {
      super(new EditorEx[]{editor1, editor2}, disposable);
    }

    public void install(@Nullable final List<LineFragment> fragments,
                        @NotNull UserDataHolder context,
                        boolean defaultExpanded,
                        final int range) {
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
      install(it, context, defaultExpanded, range);
    }

    public void paintOnDivider(@NotNull Graphics2D gg, @NotNull Component divider) {
      myPaintable.paintOnDivider(gg, divider);
    }
  }
}
