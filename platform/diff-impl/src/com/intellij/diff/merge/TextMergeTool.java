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
package com.intellij.diff.merge;

import com.intellij.diff.DiffContext;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.comparison.ByLine;
import com.intellij.diff.comparison.ComparisonMergeUtil;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.comparison.DiffTooBigException;
import com.intellij.diff.comparison.iterables.FairDiffIterable;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.fragments.MergeLineFragment;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.tools.simple.ThreesideTextDiffViewerEx;
import com.intellij.diff.tools.util.DiffNotifications;
import com.intellij.diff.tools.util.KeyboardModifierListener;
import com.intellij.diff.util.*;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.BooleanGetter;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.CalledWithWriteLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Set;

public class TextMergeTool implements MergeTool {
  public static final TextMergeTool INSTANCE = new TextMergeTool();

  public static final Logger LOG = Logger.getInstance(TextMergeTool.class);

  @NotNull
  @Override
  public MergeViewer createComponent(@NotNull MergeContext context, @NotNull MergeRequest request) {
    return new TextMergeViewer(context, ((TextMergeRequest)request));
  }

  @Override
  public boolean canShow(@NotNull MergeContext context, @NotNull MergeRequest request) {
    return request instanceof TextMergeRequest;
  }

  public static class TextMergeViewer implements MergeViewer {
    @NotNull private final MergeContext myMergeContext;
    @NotNull private final TextMergeRequest myMergeRequest;

    @NotNull private final DiffContext myDiffContext;
    @NotNull private final ContentDiffRequest myDiffRequest;

    @NotNull private final MyThreesideViewer myViewer;

    private boolean myConflictResolved;
    private int myBulkChangeUpdateDepth;

    public TextMergeViewer(@NotNull MergeContext context, @NotNull TextMergeRequest request) {
      myMergeContext = context;
      myMergeRequest = request;

      myDiffContext = new MergeUtil.ProxyDiffContext(myMergeContext);
      myDiffRequest = new SimpleDiffRequest(myMergeRequest.getTitle(),
                                            getDiffContents(myMergeRequest),
                                            getDiffContentTitles(myMergeRequest));
      myDiffRequest.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true);

      myViewer = new MyThreesideViewer(myDiffContext, myDiffRequest);
    }

    @NotNull
    private static List<DiffContent> getDiffContents(@NotNull TextMergeRequest mergeRequest) {
      List<DocumentContent> contents = mergeRequest.getContents();

      final DocumentContent left = ThreeSide.LEFT.select(contents);
      final DocumentContent right = ThreeSide.RIGHT.select(contents);
      final DocumentContent output = mergeRequest.getOutputContent();

      return ContainerUtil.<DiffContent>list(left, output, right);
    }

    @NotNull
    private static List<String> getDiffContentTitles(@NotNull TextMergeRequest mergeRequest) {
      List<String> titles = MergeUtil.notNullizeContentTitles(mergeRequest.getContentTitles());
      titles.set(ThreeSide.BASE.getIndex(), "Result");
      return titles;
    }

    //
    // Impl
    //

    @NotNull
    @Override
    public JComponent getComponent() {
      return myViewer.getComponent();
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
      return myViewer.getPreferredFocusedComponent();
    }

    @Override
    public ToolbarComponents init() {
      ToolbarComponents components = new ToolbarComponents();

      FrameDiffTool.ToolbarComponents init = myViewer.init();
      components.statusPanel = init.statusPanel;
      components.toolbarActions = init.toolbarActions;

      components.closeHandler = new BooleanGetter() {
        @Override
        public boolean get() {
          return isConflictResolved();
        }
      };


      return components;
    }

    @NotNull
    @Override
    public BottomActions getBottomActions() {
      return MergeUtil.createBottomAction(myViewer.createActionProcessor());
    }

    @Override
    public void dispose() {
      Disposer.dispose(myViewer);
      LOG.assertTrue(myBulkChangeUpdateDepth == 0);
      if (!isConflictResolved()) myMergeRequest.applyResult(MergeResult.CANCEL);
    }

    //
    // Getters
    //

    @NotNull
    public MyThreesideViewer getViewer() {
      return myViewer;
    }

    public boolean isConflictResolved() {
      return myConflictResolved;
    }

    public void markConflictResolved() {
      myConflictResolved = true;
    }

    //
    // Viewer
    //

    public class MyThreesideViewer extends ThreesideTextDiffViewerEx {
      @NotNull private final ModifierProvider myModifierProvider;

      // all changes - both applied and unapplied ones
      @NotNull private final List<TextMergeChange> myAllMergeChanges = new ArrayList<TextMergeChange>();
      private boolean myInitialRediffDone;

      private final Set<TextMergeChange> myChangesToUpdate = new HashSet<TextMergeChange>();

      public MyThreesideViewer(@NotNull DiffContext context, @NotNull ContentDiffRequest request) {
        super(context, request);

        myModifierProvider = new ModifierProvider();

        DiffUtil.registerAction(new ApplySelectedChangesAction(Side.LEFT, true), myPanel);
        DiffUtil.registerAction(new AppendSelectedChangesAction(Side.LEFT, true), myPanel);
        DiffUtil.registerAction(new ApplySelectedChangesAction(Side.RIGHT, true), myPanel);
        DiffUtil.registerAction(new AppendSelectedChangesAction(Side.RIGHT, true), myPanel);
        DiffUtil.registerAction(new RevertSelectedChangesAction(true), myPanel);
      }

      @Override
      protected void onInit() {
        super.onInit();
        myModifierProvider.init();
      }

      @NotNull
      @Override
      protected List<AnAction> createToolbarActions() {
        List<AnAction> group = new ArrayList<AnAction>();

        group.add(new MyToggleAutoScrollAction());
        group.add(myEditorSettingsAction);

        group.add(Separator.getInstance());
        group.add(new ShowLeftBasePartialDiffAction());
        group.add(new ShowBaseRightPartialDiffAction());
        group.add(new ShowLeftRightPartialDiffAction());

        group.add(Separator.getInstance());
        group.add(new ApplyNonConflictsAction());
        group.add(new ApplySideNonConflictsAction(Side.LEFT));
        group.add(new ApplySideNonConflictsAction(Side.RIGHT));

        return group;
      }

      @NotNull
      @Override
      protected List<AnAction> createEditorPopupActions() {
        List<AnAction> group = new ArrayList<AnAction>();

        group.add(new ApplySelectedChangesAction(Side.LEFT, false));
        group.add(new AppendSelectedChangesAction(Side.LEFT, false));
        group.add(new ApplySelectedChangesAction(Side.RIGHT, false));
        group.add(new AppendSelectedChangesAction(Side.RIGHT, false));
        group.add(new RevertSelectedChangesAction(false));
        group.add(Separator.getInstance());

        group.addAll(super.createEditorPopupActions());

        return group;
      }

      @Nullable
      @Override
      protected List<AnAction> createPopupActions() {
        List<AnAction> group = new ArrayList<AnAction>();

        group.add(Separator.getInstance());
        group.add(new MyToggleAutoScrollAction());

        return group;
      }

      @NotNull
      private MergeUtil.AcceptActionProcessor createActionProcessor() {
        return new MergeUtil.AcceptActionProcessor() {
          @Override
          public boolean isVisible(@NotNull MergeResult result) {
            return true;
          }

          @Override
          public void perform(@NotNull MergeResult result) {
            if (result == MergeResult.RESOLVED) {
              if ((getChangesCount() != 0 || getConflictsCount() != 0) &&
                  Messages.showYesNoDialog(myPanel.getRootPane(),
                                           DiffBundle.message("merge.dialog.apply.partially.resolved.changes.confirmation.message", getChangesCount(), getConflictsCount()),
                                           DiffBundle.message("apply.partially.resolved.merge.dialog.title"),
                                           Messages.getQuestionIcon()) != Messages.YES) {
                return;
              }
            }
            if (result == MergeResult.CANCEL) {
              if (Messages.showYesNoDialog(myPanel.getRootPane(),
                                           DiffBundle.message("merge.dialog.exit.without.applying.changes.confirmation.message"),
                                           DiffBundle.message("cancel.visual.merge.dialog.title"), Messages.getQuestionIcon()) != Messages.YES) {
                return;
              }
            }
            markConflictResolved();
            destroyChangedBlocks();
            myMergeRequest.applyResult(result);
            myMergeContext.closeDialog();
          }
        };
      }

      //
      // Diff
      //

      private void setInitialOutputContent() {
        final Document baseDocument = ThreeSide.BASE.select(myMergeRequest.getContents()).getDocument();
        final Document outputDocument = myMergeRequest.getOutputContent().getDocument();

        DiffUtil.executeWriteCommand(outputDocument, getProject(), "Init merge content",
                                     UndoConfirmationPolicy.REQUEST_CONFIRMATION, new Runnable() {
            @Override
            public void run() {
              outputDocument.setText(baseDocument.getCharsSequence());
            }
          });
      }

      @Override
      @CalledInAwt
      public void rediff(boolean trySync) {
        if (myInitialRediffDone) return;
        myInitialRediffDone = true;
        assert myAllMergeChanges.isEmpty();
        doRediff();
      }

      @CalledInAwt
      private void doRediff() {
        myStatusPanel.setBusy(true);

        setInitialOutputContent();

        // we have to collect contents here, because someone can modify document while we're starting rediff
        List<DiffContent> contents = myRequest.getContents();
        final List<CharSequence> sequences = ContainerUtil.map(contents, new Function<DiffContent, CharSequence>() {
          @Override
          public CharSequence fun(DiffContent diffContent) {
            return ((DocumentContent)diffContent).getDocument().getImmutableCharSequence();
          }
        });
        final long outputModificationStamp = myMergeRequest.getOutputContent().getDocument().getModificationStamp();

        // we need invokeLater() here because viewer is partially-initialized (ex: there are no toolbar or status panel)
        // user can see this state while we're showing progress indicator, so we want let init() to finish.
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            ProgressManager.getInstance().run(new Task.Modal(getProject(), "Computing differences...", true) {
              private Runnable myCallback;

              @Override
              public void run(@NotNull ProgressIndicator indicator) {
                myCallback = performRediff(sequences, outputModificationStamp, indicator);
              }

              @Override
              public void onCancel() {
                markConflictResolved();
                myMergeRequest.applyResult(MergeResult.CANCEL);
                myMergeContext.closeDialog();
              }

              @Override
              public void onSuccess() {
                myCallback.run();
              }
            });
          }
        });
      }

      @NotNull
      @Override
      protected Runnable performRediff(@NotNull ProgressIndicator indicator) {
        throw new UnsupportedOperationException();
      }

      @NotNull
      protected Runnable performRediff(@NotNull List<CharSequence> sequences,
                                       long outputModificationStamp,
                                       @NotNull ProgressIndicator indicator) {
        try {
          indicator.checkCanceled();

          FairDiffIterable fragments1 = ByLine.compareTwoStepFair(sequences.get(1), sequences.get(0), ComparisonPolicy.DEFAULT, indicator);
          FairDiffIterable fragments2 = ByLine.compareTwoStepFair(sequences.get(1), sequences.get(2), ComparisonPolicy.DEFAULT, indicator);
          List<MergeLineFragment> mergeFragments = ComparisonMergeUtil.buildFair(fragments1, fragments2, indicator);
          return apply(mergeFragments, outputModificationStamp);
        }
        catch (DiffTooBigException e) {
          return applyNotification(DiffNotifications.DIFF_TOO_BIG);
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Throwable e) {
          LOG.error(e);
          return new Runnable() {
            @Override
            public void run() {
              clearDiffPresentation();
              myPanel.setErrorContent();
            }
          };
        }
      }

      @NotNull
      private Runnable apply(@NotNull final List<MergeLineFragment> fragments, final long outputModificationStamp) {
        return new Runnable() {
          @Override
          public void run() {
            if (myMergeRequest.getOutputContent().getDocument().getModificationStamp() != outputModificationStamp) {
              setInitialOutputContent(); // in case if anyone changed output content since init() call. (unlikely, but possible)
            }

            clearDiffPresentation();

            resetChangeCounters();
            for (MergeLineFragment fragment : fragments) {
              TextMergeChange change = new TextMergeChange(fragment, TextMergeViewer.this);
              myAllMergeChanges.add(change);
              onChangeAdded(change);
            }

            myInitialScrollHelper.onRediff();

            myContentPanel.repaintDividers();
            myStatusPanel.update();

            // Editor was viewer because of FORCE_READ_ONLY flag. This is made to reduce unwanted modifications before rediff is finished.
            // It could happen between this init() EDT chunk and invokeLater().
            getEditor(ThreeSide.BASE).setViewer(false);
          }
        };
      }

      protected void destroyChangedBlocks() {
        for (TextMergeChange change : myAllMergeChanges) {
          change.destroyHighlighter();
        }
        myAllMergeChanges.clear();
      }

      //
      // Impl
      //

      @Override
      @CalledInAwt
      protected void onBeforeDocumentChange(@NotNull DocumentEvent e) {
        super.onBeforeDocumentChange(e);
        enterBulkChangeUpdateBlock();
        if (myAllMergeChanges.isEmpty()) return;

        ThreeSide side = null;
        if (e.getDocument() == getEditor(ThreeSide.LEFT).getDocument()) side = ThreeSide.LEFT;
        if (e.getDocument() == getEditor(ThreeSide.RIGHT).getDocument()) side = ThreeSide.RIGHT;
        if (e.getDocument() == getEditor(ThreeSide.BASE).getDocument()) side = ThreeSide.BASE;
        if (side == null) {
          LOG.warn("Unknown document changed");
          return;
        }

        if (side != ThreeSide.BASE) {
          LOG.error("Non-base side was changed"); // unsupported operation
          return;
        }

        int line1 = e.getDocument().getLineNumber(e.getOffset());
        int line2 = e.getDocument().getLineNumber(e.getOffset() + e.getOldLength()) + 1;
        int shift = DiffUtil.countLinesShift(e);

        for (TextMergeChange change : myAllMergeChanges) {
          if (change.processBaseChange(line1, line2, shift)) {
            reinstallHighlighter(change); // document state is not updated yet - can't reinstall range here
          }
        }
      }

      @Override
      protected void onDocumentChange(@NotNull DocumentEvent e) {
        super.onDocumentChange(e);
        exitBulkChangeUpdateBlock();
      }

      public void repaintDividers() {
        myContentPanel.repaintDividers();
      }

      @CalledInAwt
      public void reinstallHighlighter(@NotNull TextMergeChange change) {
        if (myBulkChangeUpdateDepth > 0) {
          myChangesToUpdate.add(change);
        }
        else {
          change.doReinstallHighlighter();
        }
      }

      @CalledInAwt
      public void enterBulkChangeUpdateBlock() {
        myBulkChangeUpdateDepth++;
      }

      @CalledInAwt
      public void exitBulkChangeUpdateBlock() {
        myBulkChangeUpdateDepth--;
        LOG.assertTrue(myBulkChangeUpdateDepth >= 0);

        if (myBulkChangeUpdateDepth == 0) {
          for (TextMergeChange change : myChangesToUpdate) {
            change.doReinstallHighlighter();
          }
          myChangesToUpdate.clear();
        }
      }

      public void onChangeResolved(@NotNull TextMergeChange change) {
        onChangeRemoved(change);
        if (getChangesCount() == 0 && getConflictsCount() == 0) {
          LOG.assertTrue(getFirstUnresolvedChange(true, null) == null);
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              if (Messages.showOkCancelDialog(myPanel,
                                              DiffBundle.message("merge.all.changes.have.processed.save.and.finish.confirmation.text"),
                                              DiffBundle.message("all.changes.processed.dialog.title"),
                                              DiffBundle.message("merge.save.and.finish.button"),
                                              DiffBundle.message("merge.continue.button"),
                                              Messages.getQuestionIcon()) == Messages.OK) {
                markConflictResolved();
                destroyChangedBlocks();
                myMergeRequest.applyResult(MergeResult.RESOLVED);
                myMergeContext.closeDialog();
              }
            }
          });
        }
      }

      //
      // Getters
      //

      @NotNull
      public List<TextMergeChange> getAllChanges() {
        return myAllMergeChanges;
      }

      @NotNull
      public List<TextMergeChange> getChanges() {
        return ContainerUtil.filter(myAllMergeChanges, new Condition<TextMergeChange>() {
          @Override
          public boolean value(TextMergeChange mergeChange) {
            return !mergeChange.isResolved();
          }
        });
      }

      @NotNull
      @Override
      protected DiffDividerDrawUtil.DividerPaintable getDividerPaintable(@NotNull Side side) {
        return new MyDividerPaintable(side);
      }

      @NotNull
      public ModifierProvider getModifierProvider() {
        return myModifierProvider;
      }

      @Nullable
      private TextMergeChange getFirstUnresolvedChange(boolean acceptConflicts, @Nullable Side side) {
        for (TextMergeChange change : getAllChanges()) {
          if (change.isResolved()) continue;
          if (!acceptConflicts && change.isConflict()) continue;
          if (side != null && !change.isChange(side)) continue;
          return change;
        }
        return null;
      }

      //
      // Modification operations
      //

      @CalledWithWriteLock
      public void replaceChange(@NotNull TextMergeChange change, @NotNull Side side) {
        if (change.isResolved(side)) return;
        if (!change.isChange(side)) {
          change.markResolved();
          return;
        }

        ThreeSide sourceSide = side.select(ThreeSide.LEFT, ThreeSide.RIGHT);
        ThreeSide outputSide = ThreeSide.BASE;

        int outputStartLine = change.getStartLine(outputSide);
        int outputEndLine = change.getEndLine(outputSide);
        int sourceStartLine = change.getStartLine(sourceSide);
        int sourceEndLine = change.getEndLine(sourceSide);

        enterBulkChangeUpdateBlock();
        try {
          DiffUtil.applyModification(getContent(outputSide).getDocument(), outputStartLine, outputEndLine,
                                     getContent(sourceSide).getDocument(), sourceStartLine, sourceEndLine);

          if (outputStartLine == outputEndLine) { // onBeforeDocumentChange() should process other cases correctly
            int newOutputEndLine = outputStartLine + (sourceEndLine - sourceStartLine);
            moveChangesAfterInsertion(change, outputStartLine, newOutputEndLine);
          }

          change.markResolved();
        }
        finally {
          exitBulkChangeUpdateBlock();
        }
      }

      @CalledWithWriteLock
      public void appendChange(@NotNull TextMergeChange change, @NotNull Side side) {
        if (change.isResolved(side)) return;
        if (!change.isChange(side)) return;

        ThreeSide sourceSide = side.select(ThreeSide.LEFT, ThreeSide.RIGHT);
        ThreeSide oppositeSide = side.select(ThreeSide.RIGHT, ThreeSide.LEFT);
        ThreeSide outputSide = ThreeSide.BASE;

        int outputStartLine = change.getStartLine(outputSide);
        int outputEndLine = change.getEndLine(outputSide);
        int sourceStartLine = change.getStartLine(sourceSide);
        int sourceEndLine = change.getEndLine(sourceSide);

        if (sourceStartLine == sourceEndLine) return; // can't append deletion

        enterBulkChangeUpdateBlock();
        try {
          DiffUtil.applyModification(getContent(outputSide).getDocument(), outputEndLine, outputEndLine,
                                     getContent(sourceSide).getDocument(), sourceStartLine, sourceEndLine);

          int newOutputEndLine = outputEndLine + (sourceEndLine - sourceStartLine);
          moveChangesAfterInsertion(change, outputStartLine, newOutputEndLine);

          if (!change.isConflict() ||
              change.getStartLine(oppositeSide) == change.getEndLine(oppositeSide)) { // conflict modification-deletion
            change.markResolved();
          }
          else {
            change.markResolved(side);
          }
        }
        finally {
          exitBulkChangeUpdateBlock();
        }
      }

      /*
       * We want to include inserted block into change, so we are updating endLine(BASE).
       *
       * It could break order of changes if there are other changes that starts/ends at this line.
       * So we should check all other changes and shift them if necessary.
       */
      private void moveChangesAfterInsertion(@NotNull TextMergeChange change,
                                             int newOutputStartLine,
                                             int newOutputEndLine) {
        if (change.getStartLine(ThreeSide.BASE) != newOutputStartLine ||
            change.getEndLine(ThreeSide.BASE) != newOutputEndLine) {
          change.setStartLine(ThreeSide.BASE, newOutputStartLine);
          change.setEndLine(ThreeSide.BASE, newOutputEndLine);
          reinstallHighlighter(change);
        }

        boolean beforeChange = true;
        for (TextMergeChange otherChange : getAllChanges()) {
          int startLine = otherChange.getStartLine(ThreeSide.BASE);
          int endLine = otherChange.getEndLine(ThreeSide.BASE);
          if (endLine < newOutputStartLine) continue;
          if (startLine > newOutputEndLine) break;
          if (otherChange == change) {
            beforeChange = false;
            continue;
          }

          int newStartLine = beforeChange ? Math.min(startLine, newOutputStartLine) : Math.max(startLine, newOutputEndLine);
          int newEndLine = beforeChange ? Math.min(endLine, newOutputStartLine) : Math.max(endLine, newOutputEndLine);
          if (startLine != newStartLine || endLine != newEndLine) {
            otherChange.setStartLine(ThreeSide.BASE, newStartLine);
            otherChange.setEndLine(ThreeSide.BASE, newEndLine);
            reinstallHighlighter(otherChange);
          }
        }
      }

      //
      // Actions
      //

      private abstract class ApplySelectedChangesActionBase extends AnAction implements DumbAware {
        private final boolean myShortcut;

        public ApplySelectedChangesActionBase(boolean shortcut) {
          myShortcut = shortcut;
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
          if (myShortcut) {
            // consume shortcut even if there are nothing to do - avoid calling some other action
            e.getPresentation().setEnabledAndVisible(true);
            return;
          }

          Presentation presentation = e.getPresentation();
          Editor editor = e.getData(CommonDataKeys.EDITOR);

          ThreeSide side = getEditorSide(editor);
          if (side == null) {
            presentation.setEnabledAndVisible(false);
            return;
          }

          if (!isVisible(side)) {
            presentation.setEnabledAndVisible(false);
            return;
          }

          presentation.setVisible(true);
          presentation.setEnabled(isSomeChangeSelected(side));
        }

        @Override
        public void actionPerformed(@NotNull final AnActionEvent e) {
          Editor editor = e.getData(CommonDataKeys.EDITOR);
          final ThreeSide side = getEditorSide(editor);
          if (editor == null || side == null) return;

          final List<TextMergeChange> selectedChanges = getSelectedChanges(side);
          if (selectedChanges.isEmpty()) return;

          DocumentEx document = getEditor(ThreeSide.BASE).getDocument();
          String title = e.getPresentation().getText() + " in merge";

          getEditor(ThreeSide.BASE).getDocument().setInBulkUpdate(true);
          enterBulkChangeUpdateBlock();
          try {
            DiffUtil.executeWriteCommand(document, e.getProject(), title, new Runnable() {
              @Override
              public void run() {
                apply(side, selectedChanges);
              }
            });
          }
          finally {
            exitBulkChangeUpdateBlock();
            getEditor(ThreeSide.BASE).getDocument().setInBulkUpdate(false);
          }
        }

        private boolean isSomeChangeSelected(@NotNull ThreeSide side) {
          EditorEx editor = getEditor(side);
          List<Caret> carets = editor.getCaretModel().getAllCarets();
          if (carets.size() != 1) return true;
          Caret caret = carets.get(0);
          if (caret.hasSelection()) return true;

          int line = editor.getDocument().getLineNumber(editor.getExpectedCaretOffset());

          List<TextMergeChange> changes = getAllChanges();
          for (TextMergeChange change : changes) {
            if (!isEnabled(change)) continue;
            int line1 = change.getStartLine(side);
            int line2 = change.getEndLine(side);

            if (DiffUtil.isSelectedByLine(line, line1, line2)) return true;
          }
          return false;
        }

        @NotNull
        @CalledInAwt
        private List<TextMergeChange> getSelectedChanges(@NotNull ThreeSide side) {
          final BitSet lines = DiffUtil.getSelectedLines(getEditor(side));
          List<TextMergeChange> changes = getChanges();

          List<TextMergeChange> affectedChanges = new ArrayList<TextMergeChange>();
          for (int i = changes.size() - 1; i >= 0; i--) {
            TextMergeChange change = changes.get(i);
            if (!isEnabled(change)) continue;
            int line1 = change.getStartLine(side);
            int line2 = change.getEndLine(side);

            if (DiffUtil.isSelectedByLine(lines, line1, line2)) {
              affectedChanges.add(change);
            }
          }
          return affectedChanges;
        }

        protected abstract boolean isVisible(@NotNull ThreeSide side);

        protected abstract boolean isEnabled(@NotNull TextMergeChange change);

        @CalledWithWriteLock
        protected abstract void apply(@NotNull ThreeSide side, @NotNull List<TextMergeChange> changes);
      }

      private class RevertSelectedChangesAction extends ApplySelectedChangesActionBase {
        public RevertSelectedChangesAction(boolean shortcut) {
          super(shortcut);
          EmptyAction.setupAction(this, "Diff.IgnoreChange", null);
        }

        @Override
        protected boolean isVisible(@NotNull ThreeSide side) {
          return true;
        }

        @Override
        protected boolean isEnabled(@NotNull TextMergeChange change) {
          return !change.isResolved();
        }

        @Override
        protected void apply(@NotNull ThreeSide side, @NotNull List<TextMergeChange> changes) {
          for (TextMergeChange change : changes) {
            change.markResolved();
          }
        }
      }

      private class ApplySelectedChangesAction extends ApplySelectedChangesActionBase {
        @NotNull private final Side mySide;

        public ApplySelectedChangesAction(@NotNull Side side, boolean shortcut) {
          super(shortcut);
          mySide = side;
          EmptyAction.setupAction(this, mySide.select("Diff.ApplyLeftSide", "Diff.ApplyRightSide"), null);
        }

        @Override
        protected boolean isVisible(@NotNull ThreeSide side) {
          if (side == ThreeSide.BASE) return true;
          return side == mySide.select(ThreeSide.LEFT, ThreeSide.RIGHT);
        }

        @Override
        protected boolean isEnabled(@NotNull TextMergeChange change) {
          return !change.isResolved(mySide);
        }

        @Override
        protected void apply(@NotNull ThreeSide side, @NotNull List<TextMergeChange> changes) {
          for (TextMergeChange change : changes) {
            replaceChange(change, mySide);
          }
        }
      }

      private class AppendSelectedChangesAction extends ApplySelectedChangesActionBase {
        @NotNull private final Side mySide;

        public AppendSelectedChangesAction(@NotNull Side side, boolean shortcut) {
          super(shortcut);
          mySide = side;
          EmptyAction.setupAction(this, mySide.select("Diff.AppendLeftSide", "Diff.AppendRightSide"), null);
        }

        @Override
        protected boolean isVisible(@NotNull ThreeSide side) {
          if (side == ThreeSide.BASE) return true;
          return side == mySide.select(ThreeSide.LEFT, ThreeSide.RIGHT);
        }

        @Override
        protected boolean isEnabled(@NotNull TextMergeChange change) {
          return !change.isResolved(mySide) && change.isChange(mySide);
        }

        @Override
        protected void apply(@NotNull ThreeSide side, @NotNull List<TextMergeChange> changes) {
          for (TextMergeChange change : changes) {
            appendChange(change, mySide);
          }
        }
      }

      public abstract class ApplyNonConflictsActionBase extends DumbAwareAction {
        public ApplyNonConflictsActionBase(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
          super(text, description, icon);
        }

        public void actionPerformed(AnActionEvent e) {
          DocumentEx document = getEditor(ThreeSide.BASE).getDocument();

          getEditor(ThreeSide.BASE).getDocument().setInBulkUpdate(true);
          enterBulkChangeUpdateBlock();
          try {
            DiffUtil.executeWriteCommand(document, getProject(), "Apply Non Conflicted Changes", new Runnable() {
              @Override
              public void run() {
                doPerform();
              }
            });
          }
          finally {
            exitBulkChangeUpdateBlock();
            getEditor(ThreeSide.BASE).getDocument().setInBulkUpdate(false);
          }

          TextMergeChange firstConflict = getFirstUnresolvedChange(true, null);
          if (firstConflict != null) doScrollToChange(firstConflict, true);
        }

        @CalledWithWriteLock
        protected abstract void doPerform();
      }

      public class ApplyNonConflictsAction extends ApplyNonConflictsActionBase {
        public ApplyNonConflictsAction() {
          super(DiffBundle.message("merge.dialog.apply.all.non.conflicting.changes.action.name"), null, AllIcons.Diff.ApplyNotConflicts);
        }

        @Override
        protected void doPerform() {
          List<TextMergeChange> allChanges = ContainerUtil.newArrayList(getAllChanges());
          for (TextMergeChange change : allChanges) {
            if (change.isConflict()) continue;
            if (change.isResolved()) continue;
            Side masterSide = change.isChange(Side.LEFT) ? Side.LEFT : Side.RIGHT;
            replaceChange(change, masterSide);
          }
        }

        public void update(AnActionEvent e) {
          e.getPresentation().setEnabled(getFirstUnresolvedChange(false, null) != null);
        }
      }

      public class ApplySideNonConflictsAction extends ApplyNonConflictsActionBase {
        @NotNull private final Side mySide;

        public ApplySideNonConflictsAction(@NotNull Side side) {
          super(side.select(DiffBundle.message("merge.dialog.apply.left.non.conflicting.changes.action.name"),
                            DiffBundle.message("merge.dialog.apply.right.non.conflicting.changes.action.name")),
                null,
                side.select(AllIcons.Diff.ApplyNotConflictsLeft, AllIcons.Diff.ApplyNotConflictsRight));
          mySide = side;
        }

        @Override
        protected void doPerform() {
          List<TextMergeChange> allChanges = ContainerUtil.newArrayList(getAllChanges());
          for (TextMergeChange change : allChanges) {
            if (change.isConflict()) continue;
            if (change.isResolved(mySide)) continue;
            if (!change.isChange(mySide)) continue;
            replaceChange(change, mySide);
          }
        }

        public void update(AnActionEvent e) {
          e.getPresentation().setEnabled(getFirstUnresolvedChange(false, mySide) != null);
        }
      }

      //
      // Helpers
      //

      private class MyDividerPaintable implements DiffDividerDrawUtil.DividerPaintable {
        @NotNull private final Side mySide;

        public MyDividerPaintable(@NotNull Side side) {
          mySide = side;
        }

        @Override
        public void process(@NotNull Handler handler) {
          ThreeSide left = mySide.select(ThreeSide.LEFT, ThreeSide.BASE);
          ThreeSide right = mySide.select(ThreeSide.BASE, ThreeSide.RIGHT);
          for (TextMergeChange mergeChange : myAllMergeChanges) {
            if (!mergeChange.isChange(mySide)) continue;
            Color color = mergeChange.getDiffType().getColor(getEditor(ThreeSide.BASE));
            boolean isResolved = mergeChange.isResolved(mySide);
            if (!handler.process(mergeChange.getStartLine(left), mergeChange.getEndLine(left),
                                 mergeChange.getStartLine(right), mergeChange.getEndLine(right),
                                 color, isResolved)) {
              return;
            }
          }
        }
      }

      public class ModifierProvider extends KeyboardModifierListener {
        public void init() {
          init(myPanel, TextMergeViewer.this);
        }

        @Override
        public void onModifiersChanged() {
          for (TextMergeChange change : myAllMergeChanges) {
            change.updateGutterActions(false);
          }
        }
      }

    }
  }
}
