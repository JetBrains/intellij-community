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
import com.intellij.diff.actions.ProxyUndoRedoAction;
import com.intellij.diff.comparison.ByLine;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.comparison.DiffTooBigException;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.fragments.MergeLineFragment;
import com.intellij.diff.fragments.MergeWordFragment;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.tools.simple.ThreesideTextDiffViewerEx;
import com.intellij.diff.tools.util.DiffNotifications;
import com.intellij.diff.tools.util.KeyboardModifierListener;
import com.intellij.diff.tools.util.base.HighlightPolicy;
import com.intellij.diff.tools.util.base.TextDiffViewerUtil;
import com.intellij.diff.util.*;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.*;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.BooleanGetter;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashSet;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.List;

public class TextMergeViewer implements MergeTool.MergeViewer {
  @NotNull private final MergeContext myMergeContext;
  @NotNull private final TextMergeRequest myMergeRequest;

  @NotNull private final MyThreesideViewer myViewer;

  public TextMergeViewer(@NotNull MergeContext context, @NotNull TextMergeRequest request) {
    myMergeContext = context;
    myMergeRequest = request;

    DiffContext diffContext = new MergeUtil.ProxyDiffContext(myMergeContext);
    ContentDiffRequest diffRequest = new SimpleDiffRequest(myMergeRequest.getTitle(),
                                                           getDiffContents(myMergeRequest),
                                                           getDiffContentTitles(myMergeRequest));
    diffRequest.putUserData(DiffUserDataKeys.FORCE_READ_ONLY_CONTENTS, new boolean[]{true, false, true});

    myViewer = new MyThreesideViewer(diffContext, diffRequest);
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

  @NotNull
  @Override
  public MergeTool.ToolbarComponents init() {
    MergeTool.ToolbarComponents components = new MergeTool.ToolbarComponents();

    FrameDiffTool.ToolbarComponents init = myViewer.init();
    components.statusPanel = init.statusPanel;
    components.toolbarActions = init.toolbarActions;

    components.closeHandler = new BooleanGetter() {
      @Override
      public boolean get() {
        return MergeUtil.showExitWithoutApplyingChangesDialog(TextMergeViewer.this, myMergeRequest, myMergeContext);
      }
    };

    return components;
  }

  @Nullable
  @Override
  public Action getResolveAction(@NotNull MergeResult result) {
    return myViewer.getResolveAction(result);
  }

  @Override
  public void dispose() {
    Disposer.dispose(myViewer);
  }

  //
  // Getters
  //

  @NotNull
  public MyThreesideViewer getViewer() {
    return myViewer;
  }

  //
  // Viewer
  //

  public class MyThreesideViewer extends ThreesideTextDiffViewerEx {
    @NotNull private final ModifierProvider myModifierProvider;
    @Nullable private final UndoManager myUndoManager;
    @NotNull private final MyInnerDiffWorker myInnerDiffWorker;

    // all changes - both applied and unapplied ones
    @NotNull private final List<TextMergeChange> myAllMergeChanges = new ArrayList<TextMergeChange>();

    private boolean myInitialRediffStarted;
    private boolean myInitialRediffFinished;
    private boolean myContentModified;

    @Nullable private MergeCommandAction myCurrentMergeCommand;
    private int myBulkChangeUpdateDepth;

    private final Set<TextMergeChange> myChangesToUpdate = new HashSet<TextMergeChange>();

    public MyThreesideViewer(@NotNull DiffContext context, @NotNull ContentDiffRequest request) {
      super(context, request);

      myModifierProvider = new ModifierProvider();
      myUndoManager = getProject() != null ? UndoManager.getInstance(getProject()) : UndoManager.getGlobalInstance();
      myInnerDiffWorker = new MyInnerDiffWorker();

      DiffUtil.registerAction(new ApplySelectedChangesAction(Side.LEFT, true), myPanel);
      DiffUtil.registerAction(new ApplySelectedChangesAction(Side.RIGHT, true), myPanel);
      DiffUtil.registerAction(new IgnoreSelectedChangesSideAction(Side.LEFT, true), myPanel);
      DiffUtil.registerAction(new IgnoreSelectedChangesSideAction(Side.RIGHT, true), myPanel);

      ProxyUndoRedoAction.register(getProject(), getEditor(), myContentPanel);
    }

    @Override
    protected void onInit() {
      super.onInit();
      myModifierProvider.init();
    }

    @Override
    protected void onDispose() {
      LOG.assertTrue(myBulkChangeUpdateDepth == 0);
      super.onDispose();
    }

    @NotNull
    @Override
    protected List<AnAction> createToolbarActions() {
      List<AnAction> group = new ArrayList<AnAction>();

      group.add(new MyHighlightPolicySettingAction());
      group.add(new MyToggleAutoScrollAction());
      group.add(myEditorSettingsAction);

      group.add(Separator.getInstance());
      group.add(new TextShowPartialDiffAction(PartialDiffMode.LEFT_BASE));
      group.add(new TextShowPartialDiffAction(PartialDiffMode.BASE_RIGHT));
      group.add(new TextShowPartialDiffAction(PartialDiffMode.LEFT_RIGHT));

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
      group.add(new ApplySelectedChangesAction(Side.RIGHT, false));
      group.add(new ResolveSelectedChangesAction(Side.LEFT));
      group.add(new ResolveSelectedChangesAction(Side.RIGHT));
      group.add(new IgnoreSelectedChangesSideAction(Side.LEFT, false));
      group.add(new IgnoreSelectedChangesSideAction(Side.RIGHT, false));
      group.add(new IgnoreSelectedChangesAction());

      group.add(Separator.getInstance());
      group.addAll(TextDiffViewerUtil.createEditorPopupActions());

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

    @Nullable
    public Action getResolveAction(@NotNull final MergeResult result) {
      String caption = MergeUtil.getResolveActionTitle(result, myMergeRequest, myMergeContext);
      return new AbstractAction(caption) {
        @Override
        public void actionPerformed(ActionEvent e) {
          if ((result == MergeResult.LEFT || result == MergeResult.RIGHT) && myContentModified &&
              Messages.showYesNoDialog(myPanel.getRootPane(),
                                       DiffBundle.message("merge.dialog.resolve.side.with.discard.message", result == MergeResult.LEFT ? 0 : 1),
                                       DiffBundle.message("merge.dialog.resolve.side.with.discard.title"), Messages.getQuestionIcon()) != Messages.YES) {
            return;
          }
          if (result == MergeResult.RESOLVED) {
            if ((getChangesCount() != 0 || getConflictsCount() != 0) &&
                Messages.showYesNoDialog(myPanel.getRootPane(),
                                         DiffBundle.message("merge.dialog.apply.partially.resolved.changes.confirmation.message", getChangesCount(), getConflictsCount()),
                                         DiffBundle.message("apply.partially.resolved.merge.dialog.title"),
                                         Messages.getQuestionIcon()) != Messages.YES) {
              return;
            }
          }
          if (result == MergeResult.CANCEL &&
              !MergeUtil.showExitWithoutApplyingChangesDialog(TextMergeViewer.this, myMergeRequest, myMergeContext)) {
            return;
          }
          destroyChangedBlocks();
          myMergeContext.finishMerge(result);
        }
      };
    }

    //
    // Diff
    //

    private void setInitialOutputContent() {
      final Document baseDocument = ThreeSide.BASE.select(myMergeRequest.getContents()).getDocument();
      final Document outputDocument = myMergeRequest.getOutputContent().getDocument();

      DiffUtil.executeWriteCommand(outputDocument, getProject(), "Init merge content", new Runnable() {
        @Override
        public void run() {
          outputDocument.setText(baseDocument.getCharsSequence());
          if (myUndoManager != null) {
            DocumentReference ref = DocumentReferenceManager.getInstance().create(outputDocument);
            myUndoManager.nonundoableActionPerformed(ref, false);
          }
        }
      });
    }

    @Override
    @CalledInAwt
    public void rediff(boolean trySync) {
      if (myInitialRediffStarted) return;
      myInitialRediffStarted = true;
      assert myAllMergeChanges.isEmpty();
      doRediff();
    }

    @NotNull
    @Override
    protected Runnable performRediff(@NotNull ProgressIndicator indicator) {
      throw new UnsupportedOperationException();
    }

    @CalledInAwt
    private void doRediff() {
      myStatusPanel.setBusy(true);

      // This is made to reduce unwanted modifications before rediff is finished.
      // It could happen between this init() EDT chunk and invokeLater().
      getEditor().setViewer(true);

      // we have to collect contents here, because someone can modify document while we're starting rediff
      List<DocumentContent> contents = myMergeRequest.getContents();
      final List<CharSequence> sequences = ContainerUtil.map(contents, new Function<DocumentContent, CharSequence>() {
        @Override
        public CharSequence fun(DocumentContent content) {
          return content.getDocument().getImmutableCharSequence();
        }
      });

      // we need invokeLater() here because viewer is partially-initialized (ex: there are no toolbar or status panel)
      // user can see this state while we're showing progress indicator, so we want let init() to finish.
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          ProgressManager.getInstance().run(new Task.Modal(getProject(), "Computing differences...", true) {
            private Runnable myCallback;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
              myCallback = doPerformRediff(sequences, indicator);
            }

            @Override
            public void onCancel() {
              myMergeContext.finishMerge(MergeResult.CANCEL);
            }

            @Override
            public void onSuccess() {
              if (isDisposed()) return;
              myCallback.run();
            }
          });
        }
      });
    }

    @NotNull
    protected Runnable doPerformRediff(@NotNull List<CharSequence> sequences,
                                       @NotNull ProgressIndicator indicator) {
      try {
        indicator.checkCanceled();

        List<MergeLineFragment> lineFragments = ByLine.compareTwoStep(sequences.get(0), sequences.get(1), sequences.get(2),
                                                                      ComparisonPolicy.DEFAULT, indicator);

        return apply(lineFragments);
      }
      catch (DiffTooBigException e) {
        return applyNotification(DiffNotifications.createDiffTooBig());
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
    private Runnable apply(@NotNull final List<MergeLineFragment> fragments) {
      return new Runnable() {
        @Override
        public void run() {
          setInitialOutputContent();

          clearDiffPresentation();
          resetChangeCounters();

          for (int index = 0; index < fragments.size(); index++) {
            MergeLineFragment fragment = fragments.get(index);
            TextMergeChange change = new TextMergeChange(fragment, index, TextMergeViewer.this);
            myAllMergeChanges.add(change);
            onChangeAdded(change);
          }

          myInitialScrollHelper.onRediff();

          myContentPanel.repaintDividers();
          myStatusPanel.update();

          getEditor().setViewer(false);

          myInnerDiffWorker.onSettingsChanged();
          myInitialRediffFinished = true;
        }
      };
    }

    @Override
    protected void destroyChangedBlocks() {
      super.destroyChangedBlocks();
      myInnerDiffWorker.stop();

      for (TextMergeChange change : myAllMergeChanges) {
        change.destroy();
      }
      myAllMergeChanges.clear();
    }

    //
    // By-word diff
    //

    private class MyInnerDiffWorker {
      @NotNull private final Set<TextMergeChange> myScheduled = ContainerUtil.newHashSet();

      @NotNull private final Alarm myAlarm = new Alarm(MyThreesideViewer.this);
      @Nullable private ProgressIndicator myProgress;

      private boolean myEnabled = false;

      @CalledInAwt
      public void scheduleRediff(@NotNull TextMergeChange change) {
        scheduleRediff(Collections.singletonList(change));
      }

      @CalledInAwt
      public void scheduleRediff(@NotNull Collection<TextMergeChange> changes) {
        if (!myEnabled) return;

        putChanges(changes);
        schedule();
      }

      @CalledInAwt
      public void onSettingsChanged() {
        boolean enabled = getHighlightPolicy() == HighlightPolicy.BY_WORD;
        if (myEnabled == enabled) return;
        myEnabled = enabled;

        if (myProgress != null) myProgress.cancel();
        myProgress = null;

        if (myEnabled) {
          putChanges(myAllMergeChanges);
          launchRediff();
        }
        else {
          myStatusPanel.setBusy(false);
          myScheduled.clear();
          for (TextMergeChange change : myAllMergeChanges) {
            change.setInnerFragments(null);
          }
        }
      }

      public void stop() {
        if (myProgress != null) myProgress.cancel();
        myProgress = null;
        myScheduled.clear();
        myAlarm.cancelAllRequests();
      }

      private void putChanges(@NotNull Collection<TextMergeChange> changes) {
        for (TextMergeChange change : changes) {
          if (change.isResolved()) continue;
          myScheduled.add(change);
        }
      }

      @CalledInAwt
      private void schedule() {
        if (myProgress != null) return;
        if (myScheduled.isEmpty()) return;

        myAlarm.cancelAllRequests();
        myAlarm.addRequest(new Runnable() {
          @Override
          public void run() {
            launchRediff();
          }
        }, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS);
      }

      @CalledInAwt
      private void launchRediff() {
        myStatusPanel.setBusy(true);
        myProgress = new EmptyProgressIndicator();

        final List<TextMergeChange> scheduled = ContainerUtil.newArrayList(myScheduled);
        myScheduled.clear();

        final Document[] documents = new Document[]{
          getEditor(ThreeSide.LEFT).getDocument(),
          getEditor(ThreeSide.BASE).getDocument(),
          getEditor(ThreeSide.RIGHT).getDocument()};

        final List<InnerChunkData> data = ContainerUtil.map(scheduled, new Function<TextMergeChange, InnerChunkData>() {
          @Override
          public InnerChunkData fun(TextMergeChange change) {
            return new InnerChunkData(change, documents);
          }
        });

        final ProgressIndicator indicator = myProgress;
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          @Override
          public void run() {
            performRediff(scheduled, data, indicator);
          }
        });
      }

      @CalledInBackground
      private void performRediff(@NotNull final List<TextMergeChange> scheduled,
                                 @NotNull final List<InnerChunkData> data,
                                 @NotNull final ProgressIndicator indicator) {
        final List<List<MergeWordFragment>> result = new ArrayList<List<MergeWordFragment>>(data.size());
        for (InnerChunkData chunkData : data) {
          result.add(DiffUtil.compareThreesideInner(chunkData.text, ComparisonPolicy.DEFAULT, indicator));
        }

        ApplicationManager.getApplication().invokeAndWait(new Runnable() {
          @Override
          public void run() {
            if (!myEnabled || indicator.isCanceled()) return;
            myProgress = null;

            for (int i = 0; i < scheduled.size(); i++) {
              TextMergeChange change = scheduled.get(i);
              if (myScheduled.contains(change)) continue;
              change.setInnerFragments(result.get(i));
            }

            myStatusPanel.setBusy(false);
            if (!myScheduled.isEmpty()) {
              launchRediff();
            }
          }
        }, ModalityState.any());
      }
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

      if (myInitialRediffFinished) myContentModified = true;

      int line1 = e.getDocument().getLineNumber(e.getOffset());
      int line2 = e.getDocument().getLineNumber(e.getOffset() + e.getOldLength()) + 1;
      int shift = DiffUtil.countLinesShift(e);

      final List<TextMergeChange.State> corruptedStates = ContainerUtil.newSmartList();
      for (int index = 0; index < myAllMergeChanges.size(); index++) {
        TextMergeChange change = myAllMergeChanges.get(index);
        TextMergeChange.State oldState = change.processBaseChange(line1, line2, shift);
        if (oldState != null) {
          if (myCurrentMergeCommand == null) {
            corruptedStates.add(oldState);
          }
          reinstallHighlighter(change); // document state is not updated yet - can't reinstall range here
        }
      }

      if (!corruptedStates.isEmpty() && myUndoManager != null) {
        // document undo is registered inside onDocumentChange, so our undo() will be called after its undo().
        // thus thus we can avoid checks for isUndoInProgress() (to avoid modification of the same TextMergeChange by this listener)
        myUndoManager.undoableActionPerformed(new MyUndoableAction(TextMergeViewer.this, corruptedStates, true));
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
        change.markInnerFragmentsDamaged();
        change.doReinstallHighlighter();
        myInnerDiffWorker.scheduleRediff(change);
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
          change.markInnerFragmentsDamaged();
          change.doReinstallHighlighter();
        }
        myInnerDiffWorker.scheduleRediff(myChangesToUpdate);
        myChangesToUpdate.clear();
      }
    }

    private void onChangeResolved(@NotNull TextMergeChange change) {
      if (change.isResolved()) {
        onChangeRemoved(change);
      }
      else {
        onChangeAdded(change);
      }
      if (getChangesCount() == 0 && getConflictsCount() == 0) {
        LOG.assertTrue(getFirstUnresolvedChange(true, null) == null);
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            String message = "All changes have been processed.<br><a href=\"\">Save changes and finish merging</a>";
            HyperlinkListener listener = new HyperlinkAdapter() {
              @Override
              protected void hyperlinkActivated(HyperlinkEvent e) {
                destroyChangedBlocks();
                myMergeContext.finishMerge(MergeResult.RESOLVED);
              }
            };

            JComponent component = getEditor().getComponent();
            Point point = new Point(component.getWidth() / 2, JBUI.scale(5));
            Color bgColor = MessageType.INFO.getPopupBackground();

            BalloonBuilder balloonBuilder = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(message, null, bgColor, listener)
                                                                        .setAnimationCycle(200);
            Balloon balloon = balloonBuilder.createBalloon();
            balloon.show(new RelativePoint(component, point), Balloon.Position.below);
            Disposer.register(MyThreesideViewer.this, balloon);
          }
        });
      }
    }

    @NotNull
    private HighlightPolicy getHighlightPolicy() {
      HighlightPolicy policy = getTextSettings().getHighlightPolicy();
      if (policy == HighlightPolicy.BY_WORD_SPLIT) return HighlightPolicy.BY_WORD;
      if (policy == HighlightPolicy.DO_NOT_HIGHLIGHT) return HighlightPolicy.BY_LINE;
      return policy;
    }

    //
    // Getters
    //

    @NotNull
    @Override
    public List<TextMergeChange> getAllChanges() {
      return myAllMergeChanges;
    }

    @NotNull
    @Override
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

    @NotNull
    public EditorEx getEditor() {
      return getEditor(ThreeSide.BASE);
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

    private void restoreChangeState(@NotNull TextMergeChange.State state) {
      TextMergeChange change = myAllMergeChanges.get(state.myIndex);

      boolean wasResolved = change.isResolved();
      change.restoreState(state);
      reinstallHighlighter(change);
      if (wasResolved != change.isResolved()) onChangeResolved(change);
    }

    private abstract class MergeCommandAction extends DiffUtil.DiffCommandAction {
      @Nullable private final List<TextMergeChange> myAffectedChanges;

      public MergeCommandAction(@Nullable Project project,
                                @Nullable String commandName,
                                boolean underBulkUpdate,
                                @Nullable List<TextMergeChange> changes) {
        this(project, commandName, null, UndoConfirmationPolicy.DEFAULT, underBulkUpdate, changes);
      }

      public MergeCommandAction(@Nullable Project project,
                                @Nullable String commandName,
                                @Nullable String commandGroupId,
                                @NotNull UndoConfirmationPolicy confirmationPolicy,
                                boolean underBulkUpdate,
                                @Nullable List<TextMergeChange> changes) {
        super(project, getEditor().getDocument(), commandName, commandGroupId, confirmationPolicy, underBulkUpdate);
        myAffectedChanges = collectAffectedChanges(changes);
      }

      @Override
      @CalledWithWriteLock
      protected final void execute() {
        LOG.assertTrue(myCurrentMergeCommand == null);
        myContentModified = true;

        // We should restore states after changes in document (by DocumentUndoProvider) to avoid corruption by our onBeforeDocumentChange()
        // Undo actions are performed in backward order, while redo actions are performed in forward order.
        // Thus we should register two UndoableActions.

        myCurrentMergeCommand = this;
        registerUndoRedo(true);
        enterBulkChangeUpdateBlock();
        try {
          doExecute();
        }
        finally {
          exitBulkChangeUpdateBlock();
          registerUndoRedo(false);
          myCurrentMergeCommand = null;
        }
      }

      private void registerUndoRedo(final boolean undo) {
        if (myUndoManager == null) return;

        List<TextMergeChange> affectedChanges = getAffectedChanges();
        final List<TextMergeChange.State> states = new ArrayList<TextMergeChange.State>(affectedChanges.size());
        for (TextMergeChange change : affectedChanges) {
          states.add(change.storeState());
        }

        myUndoManager.undoableActionPerformed(new MyUndoableAction(TextMergeViewer.this, states, undo));
      }

      @NotNull
      private List<TextMergeChange> getAffectedChanges() {
        return myAffectedChanges != null ? myAffectedChanges : myAllMergeChanges;
      }

      @CalledWithWriteLock
      protected abstract void doExecute();
    }

    /*
     * affected changes should be sorted
     */
    public void executeMergeCommand(@Nullable String commandName,
                                    boolean underBulkUpdate,
                                    @Nullable List<TextMergeChange> affected,
                                    @NotNull final Runnable task) {
      new MergeCommandAction(getProject(), commandName, underBulkUpdate, affected) {
        @Override
        protected void doExecute() {
          task.run();
        }
      }.run();
    }

    public void executeMergeCommand(@Nullable String commandName,
                                    @Nullable List<TextMergeChange> affected,
                                    @NotNull Runnable task) {
      executeMergeCommand(commandName, false, affected, task);
    }

    @CalledInAwt
    public void markChangeResolved(@NotNull TextMergeChange change) {
      if (change.isResolved()) return;
      change.setResolved(Side.LEFT, true);
      change.setResolved(Side.RIGHT, true);

      onChangeResolved(change);
      reinstallHighlighter(change);
    }

    @CalledInAwt
    public void markChangeResolved(@NotNull TextMergeChange change, @NotNull Side side) {
      if (change.isResolved(side)) return;
      change.setResolved(side, true);

      if (change.isResolved()) onChangeResolved(change);
      reinstallHighlighter(change);
    }

    public void ignoreChange(@NotNull TextMergeChange change, @NotNull Side side, boolean resolveChange) {
      if (!change.isConflict() || resolveChange) {
        markChangeResolved(change);
      }
      else {
        markChangeResolved(change, side);
      }
    }

    @CalledWithWriteLock
    public void replaceChange(@NotNull TextMergeChange change, @NotNull Side side, boolean resolveChange) {
      LOG.assertTrue(myCurrentMergeCommand != null);
      if (change.isResolved(side)) return;
      if (!change.isChange(side)) {
        markChangeResolved(change);
        return;
      }

      ThreeSide sourceSide = side.select(ThreeSide.LEFT, ThreeSide.RIGHT);
      ThreeSide oppositeSide = side.select(ThreeSide.RIGHT, ThreeSide.LEFT);
      ThreeSide outputSide = ThreeSide.BASE;

      int outputStartLine = change.getStartLine(outputSide);
      int outputEndLine = change.getEndLine(outputSide);
      int sourceStartLine = change.getStartLine(sourceSide);
      int sourceEndLine = change.getEndLine(sourceSide);

      enterBulkChangeUpdateBlock();
      try {
        if (change.isConflict()) {
          boolean append = change.isOnesideAppliedConflict();
          int actualOutputStartLine = append ? outputEndLine : outputStartLine;

          DiffUtil.applyModification(getContent(outputSide).getDocument(), actualOutputStartLine, outputEndLine,
                                     getContent(sourceSide).getDocument(), sourceStartLine, sourceEndLine);

          if (outputStartLine == outputEndLine || append) { // onBeforeDocumentChange() should process other cases correctly
            int newOutputEndLine = actualOutputStartLine + (sourceEndLine - sourceStartLine);
            moveChangesAfterInsertion(change, outputStartLine, newOutputEndLine);
          }

          if (resolveChange || change.getStartLine(oppositeSide) == change.getEndLine(oppositeSide)) {
            markChangeResolved(change);
          } else {
            change.markOnesideAppliedConflict();
            markChangeResolved(change, side);
          }
        }
        else {
          DiffUtil.applyModification(getContent(outputSide).getDocument(), outputStartLine, outputEndLine,
                                     getContent(sourceSide).getDocument(), sourceStartLine, sourceEndLine);

          if (outputStartLine == outputEndLine) { // onBeforeDocumentChange() should process other cases correctly
            int newOutputEndLine = outputStartLine + (sourceEndLine - sourceStartLine);
            moveChangesAfterInsertion(change, outputStartLine, newOutputEndLine);
          }

          markChangeResolved(change);
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
      LOG.assertTrue(myCurrentMergeCommand != null);
      if (change.getStartLine() != newOutputStartLine ||
          change.getEndLine() != newOutputEndLine) {
        change.setStartLine(newOutputStartLine);
        change.setEndLine(newOutputEndLine);
        reinstallHighlighter(change);
      }

      boolean beforeChange = true;
      for (TextMergeChange otherChange : getAllChanges()) {
        int startLine = otherChange.getStartLine();
        int endLine = otherChange.getEndLine();
        if (endLine < newOutputStartLine) continue;
        if (startLine > newOutputEndLine) break;
        if (otherChange == change) {
          beforeChange = false;
          continue;
        }

        int newStartLine = beforeChange ? Math.min(startLine, newOutputStartLine) : Math.max(startLine, newOutputEndLine);
        int newEndLine = beforeChange ? Math.min(endLine, newOutputStartLine) : Math.max(endLine, newOutputEndLine);
        if (startLine != newStartLine || endLine != newEndLine) {
          otherChange.setStartLine(newStartLine);
          otherChange.setEndLine(newEndLine);
          reinstallHighlighter(otherChange);
        }
      }
    }

    /*
     * Nearby changes could be affected as well (ex: by moveChangesAfterInsertion)
     *
     * null means all changes could be affected
     */
    @Nullable
    private List<TextMergeChange> collectAffectedChanges(@Nullable List<TextMergeChange> directChanges) {
      if (directChanges == null || directChanges.isEmpty()) return null;

      List<TextMergeChange> result = new ArrayList<TextMergeChange>(directChanges.size());

      int directIndex = 0;
      int otherIndex = 0;
      while (directIndex < directChanges.size() && otherIndex < myAllMergeChanges.size()) {
        TextMergeChange directChange = directChanges.get(directIndex);
        TextMergeChange otherChange = myAllMergeChanges.get(otherIndex);

        if (directChange == otherChange) {
          result.add(directChange);
          otherIndex++;
          continue;
        }

        int directStart = directChange.getStartLine();
        int directEnd = directChange.getEndLine();
        int otherStart = otherChange.getStartLine();
        int otherEnd = otherChange.getEndLine();
        if (otherEnd < directStart) {
          otherIndex++;
          continue;
        }
        if (otherStart > directEnd) {
          directIndex++;
          continue;
        }

        result.add(otherChange);
        otherIndex++;
      }

      LOG.assertTrue(directChanges.size() <= result.size());
      return result;
    }

    //
    // Actions
    //

    private class MyHighlightPolicySettingAction extends TextDiffViewerUtil.HighlightPolicySettingAction {
      public MyHighlightPolicySettingAction() {
        super(getTextSettings());
      }

      @NotNull
      @Override
      protected HighlightPolicy getCurrentSetting() {
        return getHighlightPolicy();
      }

      @NotNull
      @Override
      protected List<HighlightPolicy> getAvailableSettings() {
        return ContainerUtil.list(HighlightPolicy.BY_LINE, HighlightPolicy.BY_WORD);
      }

      @Override
      protected void onSettingsChanged() {
        myInnerDiffWorker.onSettingsChanged();
      }
    }

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

        presentation.setText(getText(side));

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

        String title = e.getPresentation().getText() + " in merge";

        executeMergeCommand(title, selectedChanges.size() > 1, selectedChanges, new Runnable() {
          @Override
          public void run() {
            apply(side, selectedChanges);
          }
        });
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
        for (TextMergeChange change : changes) {
          if (!isEnabled(change)) continue;
          int line1 = change.getStartLine(side);
          int line2 = change.getEndLine(side);

          if (DiffUtil.isSelectedByLine(lines, line1, line2)) {
            affectedChanges.add(change);
          }
        }
        return affectedChanges;
      }

      protected abstract String getText(@NotNull ThreeSide side);

      protected abstract boolean isVisible(@NotNull ThreeSide side);

      protected abstract boolean isEnabled(@NotNull TextMergeChange change);

      @CalledWithWriteLock
      protected abstract void apply(@NotNull ThreeSide side, @NotNull List<TextMergeChange> changes);
    }

    private class IgnoreSelectedChangesSideAction extends ApplySelectedChangesActionBase {
      @NotNull private final Side mySide;

      public IgnoreSelectedChangesSideAction(@NotNull Side side, boolean shortcut) {
        super(shortcut);
        mySide = side;
        EmptyAction.setupAction(this, mySide.select("Diff.IgnoreLeftSide", "Diff.IgnoreRightSide"), null);
      }

      @Override
      protected String getText(@NotNull ThreeSide side) {
        return "Ignore";
      }

      @Override
      protected boolean isVisible(@NotNull ThreeSide side) {
        return side == mySide.select(ThreeSide.LEFT, ThreeSide.RIGHT);
      }

      @Override
      protected boolean isEnabled(@NotNull TextMergeChange change) {
        return !change.isResolved(mySide);
      }

      @Override
      protected void apply(@NotNull ThreeSide side, @NotNull List<TextMergeChange> changes) {
        for (TextMergeChange change : changes) {
          ignoreChange(change, mySide, false);
        }
      }
    }

    private class IgnoreSelectedChangesAction extends ApplySelectedChangesActionBase {
      public IgnoreSelectedChangesAction() {
        super(false);
        getTemplatePresentation().setIcon(AllIcons.Diff.Remove);
      }

      @Override
      protected String getText(@NotNull ThreeSide side) {
        return "Ignore";
      }

      @Override
      protected boolean isVisible(@NotNull ThreeSide side) {
        return side == ThreeSide.BASE;
      }

      @Override
      protected boolean isEnabled(@NotNull TextMergeChange change) {
        return !change.isResolved();
      }

      @Override
      protected void apply(@NotNull ThreeSide side, @NotNull List<TextMergeChange> changes) {
        for (TextMergeChange change : changes) {
          markChangeResolved(change);
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
      protected String getText(@NotNull ThreeSide side) {
        return side != ThreeSide.BASE ? "Accept" : getTemplatePresentation().getText();
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
        for (int i = changes.size() - 1; i >= 0; i--) {
          replaceChange(changes.get(i), mySide, false);
        }
      }
    }

    private class ResolveSelectedChangesAction extends ApplySelectedChangesActionBase {
      @NotNull private final Side mySide;

      public ResolveSelectedChangesAction(@NotNull Side side) {
        super(false);
        mySide = side;
      }

      @Override
      protected String getText(@NotNull ThreeSide side) {
        return mySide.select("Resolve using Left", "Resolve using Right");
      }

      @Override
      protected boolean isVisible(@NotNull ThreeSide side) {
        return side == ThreeSide.BASE;
      }

      @Override
      protected boolean isEnabled(@NotNull TextMergeChange change) {
        return !change.isResolved(mySide);
      }

      @Override
      protected void apply(@NotNull ThreeSide side, @NotNull List<TextMergeChange> changes) {
        for (int i = changes.size() - 1; i >= 0; i--) {
          replaceChange(changes.get(i), mySide, true);
        }
      }
    }

    public abstract class ApplyNonConflictsActionBase extends DumbAwareAction {
      public ApplyNonConflictsActionBase(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
        super(text, description, icon);
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        executeMergeCommand("Apply Non Conflicted Changes", true, null, new Runnable() {
          @Override
          public void run() {
            doPerform();
          }
        });

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
          replaceChange(change, masterSide, false);
        }
      }

      @Override
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
          replaceChange(change, mySide, false);
        }
      }

      @Override
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
          Color color = mergeChange.getDiffType().getColor(getEditor());
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

  private static class InnerChunkData {
    @NotNull public final CharSequence[] text = new CharSequence[3];

    public InnerChunkData(@NotNull TextMergeChange change, @NotNull Document[] documents) {
      for (ThreeSide side : ThreeSide.values()) {
        if (change.isChange(side) && !change.isResolved(side)) {
          text[side.getIndex()] = getChunkContent(change, documents, side);
        }
      }
    }

    @Nullable
    @CalledWithReadLock
    private static CharSequence getChunkContent(@NotNull TextMergeChange change, @NotNull Document[] documents, @NotNull ThreeSide side) {
      int startLine = change.getStartLine(side);
      int endLine = change.getEndLine(side);
      return startLine != endLine ? DiffUtil.getLinesContent(side.select(documents), startLine, endLine) : null;
    }
  }

  private static class MyUndoableAction extends BasicUndoableAction {
    private final WeakReference<TextMergeViewer> myViewerRef;
    @NotNull private final List<TextMergeChange.State> myStates;
    private final boolean myUndo;

    public MyUndoableAction(@NotNull TextMergeViewer viewer, @NotNull List<TextMergeChange.State> states, boolean undo) {
      super(viewer.getViewer().getEditor().getDocument());
      myViewerRef = new WeakReference<TextMergeViewer>(viewer);

      myStates = states;
      myUndo = undo;
    }

    @Override
    public final void undo() throws UnexpectedUndoException {
      TextMergeViewer mergeViewer = getViewer();
      if (mergeViewer != null && myUndo) restoreStates(mergeViewer);
    }

    @Override
    public final void redo() throws UnexpectedUndoException {
      TextMergeViewer mergeViewer = getViewer();
      if (mergeViewer != null && !myUndo) restoreStates(mergeViewer);
    }

    @Nullable
    private TextMergeViewer getViewer() {
      TextMergeViewer viewer = myViewerRef.get();
      return viewer != null && !viewer.getViewer().isDisposed() ? viewer : null;
    }

    private void restoreStates(@NotNull TextMergeViewer mergeViewer) {
      MyThreesideViewer viewer = mergeViewer.getViewer();
      if (viewer.myAllMergeChanges.isEmpty()) return; // is possible between destroyChangedBlocks() and dispose() calls

      viewer.enterBulkChangeUpdateBlock();
      for (TextMergeChange.State state : myStates) {
        viewer.restoreChangeState(state);
      }
      viewer.exitBulkChangeUpdateBlock();
    }
  }
}
