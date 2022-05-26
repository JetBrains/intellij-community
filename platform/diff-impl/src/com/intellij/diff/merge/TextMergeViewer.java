// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.merge;

import com.intellij.diff.DiffContext;
import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManager;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.actions.ProxyUndoRedoAction;
import com.intellij.diff.comparison.ComparisonManager;
import com.intellij.diff.comparison.ComparisonMergeUtil;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.comparison.DiffTooBigException;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.fragments.MergeLineFragment;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.ProxySimpleDiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.tools.holders.EditorHolderFactory;
import com.intellij.diff.tools.holders.TextEditorHolder;
import com.intellij.diff.tools.simple.ThreesideTextDiffViewerEx;
import com.intellij.diff.tools.util.DiffNotifications;
import com.intellij.diff.tools.util.FoldingModelSupport;
import com.intellij.diff.tools.util.KeyboardModifierListener;
import com.intellij.diff.tools.util.base.HighlightPolicy;
import com.intellij.diff.tools.util.base.IgnorePolicy;
import com.intellij.diff.tools.util.base.TextDiffViewerUtil;
import com.intellij.diff.tools.util.text.LineOffsets;
import com.intellij.diff.tools.util.text.LineOffsetsUtil;
import com.intellij.diff.tools.util.text.MergeInnerDifferences;
import com.intellij.diff.tools.util.text.TextDiffProviderBase;
import com.intellij.diff.util.*;
import com.intellij.diff.util.MergeConflictType.Type;
import com.intellij.icons.AllIcons;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.diff.DefaultFlagsProvider;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.LineStatusMarkerDrawUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.MarkupEditorFilter;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.progress.util.ProgressIndicatorWithDelayedPresentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ex.LineStatusMarkerPopupRenderer;
import com.intellij.openapi.vcs.ex.LineStatusTrackerBase;
import com.intellij.openapi.vcs.ex.Range;
import com.intellij.openapi.vcs.ex.SimpleLineStatusTracker;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.*;

import static com.intellij.diff.util.DiffUtil.getLineCount;
import static com.intellij.util.containers.ContainerUtil.ar;

public class TextMergeViewer implements MergeTool.MergeViewer {
  @NotNull private final MergeContext myMergeContext;
  @NotNull private final TextMergeRequest myMergeRequest;

  @NotNull private final MyThreesideViewer myViewer;

  private final Action myCancelResolveAction;
  private final Action myLeftResolveAction;
  private final Action myRightResolveAction;
  private final Action myAcceptResolveAction;

  public TextMergeViewer(@NotNull MergeContext context, @NotNull TextMergeRequest request) {
    myMergeContext = context;
    myMergeRequest = request;

    DiffContext diffContext = new MergeUtil.ProxyDiffContext(myMergeContext);
    ContentDiffRequest diffRequest = new ProxySimpleDiffRequest(myMergeRequest.getTitle(),
                                                                getDiffContents(myMergeRequest),
                                                                getDiffContentTitles(myMergeRequest),
                                                                myMergeRequest);
    diffRequest.putUserData(DiffUserDataKeys.FORCE_READ_ONLY_CONTENTS, new boolean[]{true, false, true});

    myViewer = new MyThreesideViewer(diffContext, diffRequest);

    myCancelResolveAction = myViewer.getResolveAction(MergeResult.CANCEL);
    myLeftResolveAction = myViewer.getResolveAction(MergeResult.LEFT);
    myRightResolveAction = myViewer.getResolveAction(MergeResult.RIGHT);
    myAcceptResolveAction = myViewer.getResolveAction(MergeResult.RESOLVED);
  }

  @NotNull
  private static List<DiffContent> getDiffContents(@NotNull TextMergeRequest mergeRequest) {
    List<DocumentContent> contents = mergeRequest.getContents();

    final DocumentContent left = ThreeSide.LEFT.select(contents);
    final DocumentContent right = ThreeSide.RIGHT.select(contents);
    final DocumentContent output = mergeRequest.getOutputContent();

    return Arrays.asList(left, output, right);
  }

  @NotNull
  private static List<String> getDiffContentTitles(@NotNull TextMergeRequest mergeRequest) {
    List<String> titles = MergeUtil.notNullizeContentTitles(mergeRequest.getContentTitles());
    titles.set(ThreeSide.BASE.getIndex(), DiffBundle.message("merge.version.title.merged.result"));
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

    components.closeHandler =
      () -> MergeUtil.showExitWithoutApplyingChangesDialog(this, myMergeRequest, myMergeContext, myViewer.myContentModified);

    return components;
  }

  @Nullable
  @Override
  public Action getResolveAction(@NotNull MergeResult result) {
    switch (result) {
      case CANCEL:
        return myCancelResolveAction;
      case LEFT:
        return myLeftResolveAction;
      case RIGHT:
        return myRightResolveAction;
      case RESOLVED:
        return myAcceptResolveAction;
      default:
        return null;
    }
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
    @NotNull private final MergeModelBase myModel;

    @NotNull private final ModifierProvider myModifierProvider;
    @NotNull private final MyInnerDiffWorker myInnerDiffWorker;
    @NotNull private final SimpleLineStatusTracker myLineStatusTracker;

    @NotNull private final TextDiffProviderBase myTextDiffProvider;

    // all changes - both applied and unapplied ones
    @NotNull private final List<TextMergeChange> myAllMergeChanges = new ArrayList<>();
    @NotNull private IgnorePolicy myCurrentIgnorePolicy;

    private boolean myInitialRediffStarted;
    private boolean myInitialRediffFinished;
    private boolean myContentModified;

    public MyThreesideViewer(@NotNull DiffContext context, @NotNull ContentDiffRequest request) {
      super(context, request);

      myModel = new MyMergeModel(getProject(), getEditor().getDocument());

      myModifierProvider = new ModifierProvider();
      myInnerDiffWorker = new MyInnerDiffWorker();

      myLineStatusTracker = new SimpleLineStatusTracker(getProject(), getEditor().getDocument(), MyLineStatusMarkerRenderer::new);

      myTextDiffProvider = new TextDiffProviderBase(
        getTextSettings(),
        () -> {
          restartMergeResolveIfNeeded();
          myInnerDiffWorker.onSettingsChanged();
        },
        this,
        ar(IgnorePolicy.DEFAULT, IgnorePolicy.TRIM_WHITESPACES, IgnorePolicy.IGNORE_WHITESPACES),
        ar(HighlightPolicy.BY_LINE, HighlightPolicy.BY_WORD));
      myCurrentIgnorePolicy = myTextDiffProvider.getIgnorePolicy();

      DiffUtil.registerAction(new NavigateToChangeMarkerAction(false), myPanel);
      DiffUtil.registerAction(new NavigateToChangeMarkerAction(true), myPanel);

      ProxyUndoRedoAction.register(getProject(), getEditor(), myContentPanel);
    }

    @Override
    protected void onInit() {
      super.onInit();
      myModifierProvider.init();
    }

    @Override
    protected void onDispose() {
      Disposer.dispose(myModel);
      myLineStatusTracker.release();
      myInnerDiffWorker.disable();
      super.onDispose();
    }

    @Override
    protected @NotNull List<TextEditorHolder> createEditorHolders(@NotNull EditorHolderFactory<TextEditorHolder> factory) {
      List<TextEditorHolder> holders = super.createEditorHolders(factory);
      ThreeSide.BASE.select(holders).getEditor().putUserData(DiffUserDataKeys.MERGE_EDITOR_FLAG, true);
      return holders;
    }

    @NotNull
    @Override
    protected List<AnAction> createToolbarActions() {
      List<AnAction> group = new ArrayList<>();

      DefaultActionGroup diffGroup = DefaultActionGroup.createPopupGroup(() -> ActionsBundle.message("group.compare.contents.text"));
      diffGroup.getTemplatePresentation().setIcon(AllIcons.Actions.Diff);
      diffGroup.add(Separator.create(ActionsBundle.message("group.compare.contents.text")));
      diffGroup.add(new TextShowPartialDiffAction(PartialDiffMode.LEFT_MIDDLE, true));
      diffGroup.add(new TextShowPartialDiffAction(PartialDiffMode.RIGHT_MIDDLE, true));
      diffGroup.add(new TextShowPartialDiffAction(PartialDiffMode.LEFT_RIGHT, true));
      diffGroup.add(new ShowDiffWithBaseAction(ThreeSide.LEFT));
      diffGroup.add(new ShowDiffWithBaseAction(ThreeSide.BASE));
      diffGroup.add(new ShowDiffWithBaseAction(ThreeSide.RIGHT));
      group.add(diffGroup);

      group.add(new Separator(DiffBundle.messagePointer("action.Anonymous.text.apply.non.conflicting.changes")));
      group.add(new ApplyNonConflictsAction(ThreeSide.LEFT, DiffBundle.message("action.merge.apply.non.conflicts.left.text")));
      group.add(new ApplyNonConflictsAction(ThreeSide.BASE, DiffBundle.message("action.merge.apply.non.conflicts.all.text")));
      group.add(new ApplyNonConflictsAction(ThreeSide.RIGHT, DiffBundle.message("action.merge.apply.non.conflicts.right.text")));
      group.add(new MagicResolvedConflictsAction());

      group.add(Separator.getInstance());
      group.addAll(myTextDiffProvider.getToolbarActions());
      group.add(new MyToggleExpandByDefaultAction());
      group.add(new MyToggleAutoScrollAction());
      group.add(myEditorSettingsAction);

      return group;
    }

    @NotNull
    @Override
    protected List<AnAction> createEditorPopupActions() {
      List<AnAction> group = new ArrayList<>();

      group.add(new ApplySelectedChangesAction(Side.LEFT));
      group.add(new ApplySelectedChangesAction(Side.RIGHT));
      group.add(new ResolveSelectedChangesAction(Side.LEFT));
      group.add(new ResolveSelectedChangesAction(Side.RIGHT));
      group.add(new IgnoreSelectedChangesSideAction(Side.LEFT));
      group.add(new IgnoreSelectedChangesSideAction(Side.RIGHT));
      group.add(new ResolveSelectedConflictsAction());
      group.add(new IgnoreSelectedChangesAction());

      group.add(Separator.getInstance());
      group.addAll(TextDiffViewerUtil.createEditorPopupActions());

      return group;
    }

    @Nullable
    @Override
    protected List<AnAction> createPopupActions() {
      List<AnAction> group = new ArrayList<>(myTextDiffProvider.getPopupActions());
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
          if ((result == MergeResult.LEFT || result == MergeResult.RIGHT) &&
              !MergeUtil.showConfirmDiscardChangesDialog(myPanel.getRootPane(),
                                                         result == MergeResult.LEFT
                                                         ? DiffBundle.message("button.merge.resolve.accept.left")
                                                         : DiffBundle.message("button.merge.resolve.accept.right"),
                                                         myContentModified)) {
            return;
          }
          if (result == MergeResult.RESOLVED &&
              (getChangesCount() > 0 || getConflictsCount() > 0) &&
              !MessageDialogBuilder.yesNo(DiffBundle.message("apply.partially.resolved.merge.dialog.title"), DiffBundle
                .message("merge.dialog.apply.partially.resolved.changes.confirmation.message", getChangesCount(), getConflictsCount()))
                .yesText(DiffBundle.message("apply.changes.and.mark.resolved"))
                .noText(DiffBundle.message("continue.merge"))
                .ask(myPanel.getRootPane())) {
            return;
          }
          if (result == MergeResult.CANCEL &&
              !MergeUtil.showExitWithoutApplyingChangesDialog(TextMergeViewer.this, myMergeRequest, myMergeContext, myContentModified)) {
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

    private void restartMergeResolveIfNeeded() {
      if (isDisposed()) return;
      if (myTextDiffProvider.getIgnorePolicy().equals(myCurrentIgnorePolicy)) return;

      if (!myInitialRediffFinished) {
        ApplicationManager.getApplication().invokeLater(() -> restartMergeResolveIfNeeded());
        return;
      }

      if (myContentModified) {
        if (Messages.showYesNoDialog(myProject,
                                     DiffBundle.message("changing.highlighting.requires.the.file.merge.restart"),
                                     DiffBundle.message("update.highlighting.settings"),
                                     DiffBundle.message("discard.changes.and.restart.merge"),
                                     DiffBundle.message("continue.merge"),
                                     Messages.getQuestionIcon()) != Messages.YES) {
          getTextSettings().setIgnorePolicy(myCurrentIgnorePolicy);
          return;
        }
      }

      myInitialRediffFinished = false;
      doRediff();
    }

    private boolean setInitialOutputContent() {
      final Document baseDocument = ThreeSide.BASE.select(myMergeRequest.getContents()).getDocument();
      final Document outputDocument = myMergeRequest.getOutputContent().getDocument();

      return DiffUtil.executeWriteCommand(outputDocument, getProject(), DiffBundle.message("message.init.merge.content.command"), () -> {
        outputDocument.setText(baseDocument.getCharsSequence());

        DiffUtil.putNonundoableOperation(getProject(), outputDocument);

        if (getTextSettings().isEnableLstGutterMarkersInMerge()) {
          myLineStatusTracker.setBaseRevision(baseDocument.getCharsSequence());
          getEditor().getGutterComponentEx().setForceShowRightFreePaintersArea(true);
        }
      });
    }

    @Override
    @RequiresEdt
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

    @RequiresEdt
    private void doRediff() {
      myStatusPanel.setBusy(true);
      myInnerDiffWorker.disable();

      // This is made to reduce unwanted modifications before rediff is finished.
      // It could happen between this init() EDT chunk and invokeLater().
      getEditor().setViewer(true);
      myLoadingPanel.startLoading();
      myAcceptResolveAction.setEnabled(false);

      BackgroundTaskUtil.executeAndTryWait(indicator -> BackgroundTaskUtil.runUnderDisposeAwareIndicator(this, () -> {
        try {
          return doPerformRediff(indicator);
        }
        catch (ProcessCanceledException e) {
          return () -> myMergeContext.finishMerge(MergeResult.CANCEL);
        }
        catch (Throwable e) {
          LOG.error(e);
          return () -> myMergeContext.finishMerge(MergeResult.CANCEL);
        }
      }), null, ProgressIndicatorWithDelayedPresentation.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS, ApplicationManager.getApplication().isUnitTestMode());
    }

    @NotNull
    protected Runnable doPerformRediff(@NotNull ProgressIndicator indicator) {
      try {
        indicator.checkCanceled();
        IgnorePolicy ignorePolicy = myTextDiffProvider.getIgnorePolicy();

        List<DocumentContent> contents = myMergeRequest.getContents();
        List<CharSequence> sequences = ReadAction.compute(() -> {
          indicator.checkCanceled();
          return ContainerUtil.map(contents, content -> content.getDocument().getImmutableCharSequence());
        });
        List<LineOffsets> lineOffsets = ContainerUtil.map(sequences, LineOffsetsUtil::create);

        ComparisonManager manager = ComparisonManager.getInstance();
        List<MergeLineFragment> lineFragments = manager.mergeLines(sequences.get(0), sequences.get(1), sequences.get(2),
                                                                   ignorePolicy.getComparisonPolicy(), indicator);

        List<MergeConflictType> conflictTypes = ContainerUtil.map(lineFragments, fragment ->
          MergeRangeUtil.getLineMergeType(fragment, sequences, lineOffsets, ignorePolicy.getComparisonPolicy()));

        FoldingModelSupport.Data foldingState = myFoldingModel.createState(lineFragments, lineOffsets, getFoldingModelSettings());

        return () -> apply(lineFragments, conflictTypes, foldingState, ignorePolicy);
      }
      catch (DiffTooBigException e) {
        return applyNotification(DiffNotifications.createDiffTooBig());
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Throwable e) {
        LOG.error(e);
        return () -> {
          clearDiffPresentation();
          myPanel.setErrorContent();
        };
      }
    }

    @RequiresEdt
    private void apply(@NotNull List<? extends MergeLineFragment> fragments,
                       @NotNull List<? extends MergeConflictType> conflictTypes,
                       @Nullable FoldingModelSupport.Data foldingState,
                       @NotNull IgnorePolicy ignorePolicy) {
      if (isDisposed()) return;
      myFoldingModel.updateContext(myRequest, getFoldingModelSettings());
      clearDiffPresentation();
      resetChangeCounters();

      boolean success = setInitialOutputContent();
      if (!success) {
        fragments = Collections.emptyList();
        conflictTypes = Collections.emptyList();
        myPanel
          .addNotification(DiffNotifications.createNotification(DiffBundle.message("error.cant.resolve.conflicts.in.a.read.only.file")));
      }

      myModel.setChanges(ContainerUtil.map(fragments, f -> new LineRange(f.getStartLine(ThreeSide.BASE),
                                                                         f.getEndLine(ThreeSide.BASE))));

      for (int index = 0; index < fragments.size(); index++) {
        MergeLineFragment fragment = fragments.get(index);
        MergeConflictType conflictType = conflictTypes.get(index);

        TextMergeChange change = new TextMergeChange(index, fragment, conflictType, TextMergeViewer.this);
        myAllMergeChanges.add(change);
        onChangeAdded(change);
      }

      myFoldingModel.install(foldingState, myRequest, getFoldingModelSettings());

      myInitialScrollHelper.onRediff();

      myContentPanel.repaintDividers();
      myStatusPanel.update();

      getEditor().setViewer(false);
      myLoadingPanel.stopLoading();
      myAcceptResolveAction.setEnabled(true);

      myInnerDiffWorker.onEverythingChanged();
      myInitialRediffFinished = true;
      myContentModified = false;
      myCurrentIgnorePolicy = ignorePolicy;

      if (myViewer.getTextSettings().isAutoApplyNonConflictedChanges()) {
        if (hasNonConflictedChanges(ThreeSide.BASE)) {
          applyNonConflictedChanges(ThreeSide.BASE);
        }
      }
    }

    @Override
    @RequiresEdt
    protected void destroyChangedBlocks() {
      super.destroyChangedBlocks();
      myInnerDiffWorker.stop();

      for (TextMergeChange change : myAllMergeChanges) {
        change.destroy();
      }
      myAllMergeChanges.clear();

      myModel.setChanges(Collections.emptyList());
    }

    //
    // By-word diff
    //

    private class MyInnerDiffWorker {
      @NotNull private final Set<TextMergeChange> myScheduled = new HashSet<>();

      @NotNull private final Alarm myAlarm = new Alarm(MyThreesideViewer.this);
      @Nullable private ProgressIndicator myProgress;

      private boolean myEnabled = false;

      @RequiresEdt
      public void scheduleRediff(@NotNull TextMergeChange change) {
        scheduleRediff(Collections.singletonList(change));
      }

      @RequiresEdt
      public void scheduleRediff(@NotNull Collection<? extends TextMergeChange> changes) {
        if (!myEnabled) return;

        putChanges(changes);
        schedule();
      }

      @RequiresEdt
      public void onSettingsChanged() {
        boolean enabled = myTextDiffProvider.getHighlightPolicy() == HighlightPolicy.BY_WORD;
        if (myEnabled == enabled) return;
        myEnabled = enabled;

        rebuildEverything();
      }

      @RequiresEdt
      public void onEverythingChanged() {
        myEnabled = myTextDiffProvider.getHighlightPolicy() == HighlightPolicy.BY_WORD;

        rebuildEverything();
      }

      @RequiresEdt
      public void disable() {
        myEnabled = false;
        stop();
      }

      private void rebuildEverything() {
        if (myProgress != null) myProgress.cancel();
        myProgress = null;

        if (myEnabled) {
          putChanges(myAllMergeChanges);
          launchRediff(true);
        }
        else {
          myStatusPanel.setBusy(false);
          myScheduled.clear();
          for (TextMergeChange change : myAllMergeChanges) {
            change.setInnerFragments(null);
          }
        }
      }

      @RequiresEdt
      public void stop() {
        if (myProgress != null) myProgress.cancel();
        myProgress = null;
        myScheduled.clear();
        myAlarm.cancelAllRequests();
      }

      @RequiresEdt
      private void putChanges(@NotNull Collection<? extends TextMergeChange> changes) {
        for (TextMergeChange change : changes) {
          if (change.isResolved()) continue;
          myScheduled.add(change);
        }
      }

      @RequiresEdt
      private void schedule() {
        if (myProgress != null) return;
        if (myScheduled.isEmpty()) return;

        myAlarm.cancelAllRequests();
        myAlarm.addRequest(() -> launchRediff(false), ProgressIndicatorWithDelayedPresentation.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS);
      }

      @RequiresEdt
      private void launchRediff(boolean trySync) {
        myStatusPanel.setBusy(true);

        final List<TextMergeChange> scheduled = new ArrayList<>(myScheduled);
        myScheduled.clear();

        List<Document> documents = ThreeSide.map((side) -> getEditor(side).getDocument());
        final List<InnerChunkData> data = ContainerUtil.map(scheduled, change -> new InnerChunkData(change, documents));

        int waitMillis = trySync ? ProgressIndicatorWithDelayedPresentation.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS : 0;
        ProgressIndicator progress = BackgroundTaskUtil.executeAndTryWait(indicator -> performRediff(scheduled, data, indicator), null, waitMillis, false);

        if (progress.isRunning()) {
          myProgress = progress;
        }
      }

      @NotNull
      @RequiresBackgroundThread
      private Runnable performRediff(@NotNull final List<? extends TextMergeChange> scheduled,
                                     @NotNull final List<? extends InnerChunkData> data,
                                     @NotNull final ProgressIndicator indicator) {
        ComparisonPolicy comparisonPolicy = myTextDiffProvider.getIgnorePolicy().getComparisonPolicy();
        final List<MergeInnerDifferences> result = new ArrayList<>(data.size());
        for (InnerChunkData chunkData : data) {
          result.add(DiffUtil.compareThreesideInner(chunkData.text, comparisonPolicy, indicator));
        }

        return () -> {
          if (!myEnabled || indicator.isCanceled()) return;
          myProgress = null;

          for (int i = 0; i < scheduled.size(); i++) {
            TextMergeChange change = scheduled.get(i);
            if (myScheduled.contains(change)) continue;
            change.setInnerFragments(result.get(i));
          }

          myStatusPanel.setBusy(false);
          if (!myScheduled.isEmpty()) {
            launchRediff(false);
          }
        };
      }
    }

    //
    // Impl
    //

    @Override
    @RequiresEdt
    protected void onBeforeDocumentChange(@NotNull DocumentEvent e) {
      super.onBeforeDocumentChange(e);
      if (myInitialRediffFinished) myContentModified = true;
    }

    public void repaintDividers() {
      myContentPanel.repaintDividers();
    }

    private void onChangeResolved(@NotNull TextMergeChange change) {
      if (change.isResolved()) {
        onChangeRemoved(change);
      }
      else {
        onChangeAdded(change);
      }
      if (getChangesCount() == 0 && getConflictsCount() == 0) {
        LOG.assertTrue(ContainerUtil.and(getAllChanges(), TextMergeChange::isResolved));
        ApplicationManager.getApplication().invokeLater(() -> {
          if (isDisposed()) return;

          JComponent component = getEditor().getComponent();
          RelativePoint point = new RelativePoint(component, new Point(component.getWidth() / 2, JBUIScale.scale(5)));

          String message = DiffBundle.message("merge.all.changes.processed.message.text");
          DiffUtil.showSuccessPopup(message, point, this, () -> {
            if (isDisposed() || myLoadingPanel.isLoading()) return;
            destroyChangedBlocks();
            myMergeContext.finishMerge(MergeResult.RESOLVED);
          });
        });
      }
    }

    //
    // Getters
    //

    @NotNull
    public MergeModelBase getModel() {
      return myModel;
    }

    @NotNull
    @Override
    public List<TextMergeChange> getAllChanges() {
      return myAllMergeChanges;
    }

    @NotNull
    @Override
    public List<TextMergeChange> getChanges() {
      return ContainerUtil.filter(myAllMergeChanges, mergeChange -> !mergeChange.isResolved());
    }

    @NotNull
    @Override
    protected DiffDividerDrawUtil.DividerPaintable getDividerPaintable(@NotNull Side side) {
      return new MyDividerPaintable(side);
    }

    @NotNull
    public KeyboardModifierListener getModifierProvider() {
      return myModifierProvider;
    }

    @NotNull
    public EditorEx getEditor() {
      return getEditor(ThreeSide.BASE);
    }

    //
    // Modification operations
    //

    /*
     * affected changes should be sorted
     */
    public boolean executeMergeCommand(@Nullable @Nls String commandName,
                                       boolean underBulkUpdate,
                                       @Nullable List<? extends TextMergeChange> affected,
                                       @NotNull Runnable task) {
      myContentModified = true;

      IntList affectedIndexes = null;
      if (affected != null) {
        affectedIndexes = new IntArrayList(affected.size());
        for (TextMergeChange change : affected) {
          affectedIndexes.add(change.getIndex());
        }
      }

      return myModel.executeMergeCommand(commandName, null, UndoConfirmationPolicy.DEFAULT, underBulkUpdate, affectedIndexes, task);
    }

    public boolean executeMergeCommand(@Nullable @Nls String commandName,
                                       @Nullable List<? extends TextMergeChange> affected,
                                       @NotNull Runnable task) {
      return executeMergeCommand(commandName, false, affected, task);
    }

    @RequiresEdt
    public void markChangeResolved(@NotNull TextMergeChange change) {
      if (change.isResolved()) return;
      change.setResolved(Side.LEFT, true);
      change.setResolved(Side.RIGHT, true);

      onChangeResolved(change);
      myModel.invalidateHighlighters(change.getIndex());
    }

    @RequiresEdt
    public void markChangeResolved(@NotNull TextMergeChange change, @NotNull Side side) {
      if (change.isResolved(side)) return;
      change.setResolved(side, true);

      if (change.isResolved()) onChangeResolved(change);
      myModel.invalidateHighlighters(change.getIndex());
    }

    public void ignoreChange(@NotNull TextMergeChange change, @NotNull Side side, boolean resolveChange) {
      if (!change.isConflict() || resolveChange) {
        markChangeResolved(change);
      }
      else {
        markChangeResolved(change, side);
      }
    }

    @RequiresWriteLock
    public void replaceChange(@NotNull TextMergeChange change, @NotNull Side side, boolean resolveChange) {
      if (change.isResolved(side)) return;
      if (!change.isChange(side)) {
        markChangeResolved(change);
        return;
      }

      ThreeSide sourceSide = side.select(ThreeSide.LEFT, ThreeSide.RIGHT);
      ThreeSide oppositeSide = side.select(ThreeSide.RIGHT, ThreeSide.LEFT);

      Document sourceDocument = getContent(sourceSide).getDocument();
      int sourceStartLine = change.getStartLine(sourceSide);
      int sourceEndLine = change.getEndLine(sourceSide);
      List<String> newContent = DiffUtil.getLines(sourceDocument, sourceStartLine, sourceEndLine);

      if (change.isConflict()) {
        boolean append = change.isOnesideAppliedConflict();
        if (append) {
          myModel.appendChange(change.getIndex(), newContent);
        }
        else {
          myModel.replaceChange(change.getIndex(), newContent);
        }

        if (resolveChange || change.getStartLine(oppositeSide) == change.getEndLine(oppositeSide)) {
          markChangeResolved(change);
        }
        else {
          change.markOnesideAppliedConflict();
          markChangeResolved(change, side);
        }
      }
      else {
        myModel.replaceChange(change.getIndex(), newContent);

        markChangeResolved(change);
      }
    }

    private class MyMergeModel extends MergeModelBase<TextMergeChange.State> {
      MyMergeModel(@Nullable Project project, @NotNull Document document) {
        super(project, document);
      }

      @Override
      protected void reinstallHighlighters(int index) {
        TextMergeChange change = myAllMergeChanges.get(index);
        change.reinstallHighlighters();
        myInnerDiffWorker.scheduleRediff(change);
      }

      @NotNull
      @Override
      protected TextMergeChange.State storeChangeState(int index) {
        TextMergeChange change = myAllMergeChanges.get(index);
        return change.storeState();
      }

      @Override
      protected void restoreChangeState(@NotNull TextMergeChange.State state) {
        super.restoreChangeState(state);
        TextMergeChange change = myAllMergeChanges.get(state.myIndex);

        boolean wasResolved = change.isResolved();
        change.restoreState(state);
        if (wasResolved != change.isResolved()) onChangeResolved(change);
      }

      @Nullable
      @Override
      protected TextMergeChange.State processDocumentChange(int index, int oldLine1, int oldLine2, int shift) {
        TextMergeChange.State state = super.processDocumentChange(index, oldLine1, oldLine2, shift);

        TextMergeChange mergeChange = myAllMergeChanges.get(index);
        if (mergeChange.getStartLine() == mergeChange.getEndLine() &&
            mergeChange.getConflictType().getType() == Type.DELETED && !mergeChange.isResolved()) {
          myViewer.markChangeResolved(mergeChange);
        }

        return state;
      }
    }

    //
    // Actions
    //

    private boolean hasNonConflictedChanges(@NotNull ThreeSide side) {
      return ContainerUtil.exists(getAllChanges(), change -> !change.isConflict() && canResolveChangeAutomatically(change, side));
    }

    private void applyNonConflictedChanges(@NotNull ThreeSide side) {
      executeMergeCommand(DiffBundle.message("merge.dialog.apply.non.conflicted.changes.command"), true, null, () -> {
        List<TextMergeChange> allChanges = new ArrayList<>(getAllChanges());
        for (TextMergeChange change : allChanges) {
          if (!change.isConflict()) {
            resolveChangeAutomatically(change, side);
          }
        }
      });

      TextMergeChange firstUnresolved = ContainerUtil.find(getAllChanges(), c -> !c.isResolved());
      if (firstUnresolved != null) doScrollToChange(firstUnresolved, true);
    }

    private boolean hasResolvableConflictedChanges() {
      return ContainerUtil.exists(getAllChanges(), change -> canResolveChangeAutomatically(change, ThreeSide.BASE));
    }

    private void applyResolvableConflictedChanges() {
      executeMergeCommand(DiffBundle.message("message.resolve.simple.conflicts.command"), true, null, () -> {
        List<TextMergeChange> allChanges = new ArrayList<>(getAllChanges());
        for (TextMergeChange change : allChanges) {
          resolveChangeAutomatically(change, ThreeSide.BASE);
        }
      });

      TextMergeChange firstUnresolved = ContainerUtil.find(getAllChanges(), c -> !c.isResolved());
      if (firstUnresolved != null) doScrollToChange(firstUnresolved, true);
    }

    public boolean canResolveChangeAutomatically(@NotNull TextMergeChange change, @NotNull ThreeSide side) {
      if (change.isConflict()) {
        return side == ThreeSide.BASE &&
               change.getConflictType().canBeResolved() &&
               !change.isResolved(Side.LEFT) && !change.isResolved(Side.RIGHT) &&
               !isChangeRangeModified(change);
      }
      else {
        return !change.isResolved() &&
               change.isChange(side) &&
               !isChangeRangeModified(change);
      }
    }

    private boolean isChangeRangeModified(@NotNull TextMergeChange change) {
      MergeLineFragment changeFragment = change.getFragment();
      int baseStartLine = changeFragment.getStartLine(ThreeSide.BASE);
      int baseEndLine = changeFragment.getEndLine(ThreeSide.BASE);
      DocumentContent baseDiffContent = ThreeSide.BASE.select(myMergeRequest.getContents());
      Document baseDocument = baseDiffContent.getDocument();

      int resultStartLine = change.getStartLine();
      int resultEndLine = change.getEndLine();
      Document resultDocument = getEditor().getDocument();

      CharSequence baseContent = DiffUtil.getLinesContent(baseDocument, baseStartLine, baseEndLine);
      CharSequence resultContent = DiffUtil.getLinesContent(resultDocument, resultStartLine, resultEndLine);
      return !StringUtil.equals(baseContent, resultContent);
    }

    public void resolveChangeAutomatically(@NotNull TextMergeChange change, @NotNull ThreeSide side) {
      if (!canResolveChangeAutomatically(change, side)) return;

      if (change.isConflict()) {
        List<CharSequence> texts = ThreeSide.map(it -> DiffUtil.getLinesContent(getEditor(it).getDocument(), change.getStartLine(it), change.getEndLine(it)));

        CharSequence newContent = ComparisonMergeUtil.tryResolveConflict(texts.get(0), texts.get(1), texts.get(2));
        if (newContent == null) {
          LOG.warn(String.format("Can't resolve conflicting change:\n'%s'\n'%s'\n'%s'\n", texts.get(0), texts.get(1), texts.get(2)));
          return;
        }

        String[] newContentLines = LineTokenizer.tokenize(newContent, false);
        myModel.replaceChange(change.getIndex(), Arrays.asList(newContentLines));
        markChangeResolved(change);
      }
      else {
        Side masterSide = side.select(Side.LEFT,
                                      change.isChange(Side.LEFT) ? Side.LEFT : Side.RIGHT,
                                      Side.RIGHT);
        replaceChange(change, masterSide, false);
      }
    }

    private abstract class ApplySelectedChangesActionBase extends AnAction implements DumbAware {
      @Override
      public void update(@NotNull AnActionEvent e) {
        if (DiffUtil.isFromShortcut(e)) {
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

        String title = DiffBundle.message("message.do.in.merge.command", e.getPresentation().getText());

        executeMergeCommand(title, selectedChanges.size() > 1, selectedChanges, () -> apply(side, selectedChanges));
      }

      private boolean isSomeChangeSelected(@NotNull ThreeSide side) {
        EditorEx editor = getEditor(side);
        return DiffUtil.isSomeRangeSelected(editor, lines -> ContainerUtil.exists(getAllChanges(), change -> isChangeSelected(change, lines, side)));
      }

      @NotNull
      @RequiresEdt
      private List<TextMergeChange> getSelectedChanges(@NotNull ThreeSide side) {
        EditorEx editor = getEditor(side);
        BitSet lines = DiffUtil.getSelectedLines(editor);
        return ContainerUtil.filter(getChanges(), change -> isChangeSelected(change, lines, side));
      }

      private boolean isChangeSelected(@NotNull TextMergeChange change, @NotNull BitSet lines, @NotNull ThreeSide side) {
        if (!isEnabled(change)) return false;
        int line1 = change.getStartLine(side);
        int line2 = change.getEndLine(side);
        return DiffUtil.isSelectedByLine(lines, line1, line2);
      }

      @Nls
      protected abstract String getText(@NotNull ThreeSide side);

      protected abstract boolean isVisible(@NotNull ThreeSide side);

      protected abstract boolean isEnabled(@NotNull TextMergeChange change);

      @RequiresWriteLock
      protected abstract void apply(@NotNull ThreeSide side, @NotNull List<? extends TextMergeChange> changes);
    }

    private class IgnoreSelectedChangesSideAction extends ApplySelectedChangesActionBase {
      @NotNull private final Side mySide;

      IgnoreSelectedChangesSideAction(@NotNull Side side) {
        mySide = side;
        ActionUtil.copyFrom(this, mySide.select("Diff.IgnoreLeftSide", "Diff.IgnoreRightSide"));
      }

      @Override
      protected String getText(@NotNull ThreeSide side) {
        return DiffBundle.message("action.presentation.merge.ignore.text");
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
      protected void apply(@NotNull ThreeSide side, @NotNull List<? extends TextMergeChange> changes) {
        for (TextMergeChange change : changes) {
          ignoreChange(change, mySide, false);
        }
      }
    }

    private class IgnoreSelectedChangesAction extends ApplySelectedChangesActionBase {
      IgnoreSelectedChangesAction() {
        getTemplatePresentation().setIcon(AllIcons.Diff.Remove);
      }

      @Override
      protected String getText(@NotNull ThreeSide side) {
        return DiffBundle.message("action.presentation.merge.ignore.text");
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
      protected void apply(@NotNull ThreeSide side, @NotNull List<? extends TextMergeChange> changes) {
        for (TextMergeChange change : changes) {
          markChangeResolved(change);
        }
      }
    }

    private class ApplySelectedChangesAction extends ApplySelectedChangesActionBase {
      @NotNull private final Side mySide;

      ApplySelectedChangesAction(@NotNull Side side) {
        mySide = side;
        ActionUtil.copyFrom(this, mySide.select("Diff.ApplyLeftSide", "Diff.ApplyRightSide"));
      }

      @Override
      protected String getText(@NotNull ThreeSide side) {
        return side != ThreeSide.BASE ? DiffBundle.message("action.presentation.diff.accept.text") : getTemplatePresentation().getText();
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
      protected void apply(@NotNull ThreeSide side, @NotNull List<? extends TextMergeChange> changes) {
        for (int i = changes.size() - 1; i >= 0; i--) {
          replaceChange(changes.get(i), mySide, false);
        }
      }
    }

    private class ResolveSelectedChangesAction extends ApplySelectedChangesActionBase {
      @NotNull private final Side mySide;

      ResolveSelectedChangesAction(@NotNull Side side) {
        mySide = side;
      }

      @Override
      protected String getText(@NotNull ThreeSide side) {
        return DiffBundle.message("action.presentation.merge.resolve.using.side.text", mySide.getIndex());
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
      protected void apply(@NotNull ThreeSide side, @NotNull List<? extends TextMergeChange> changes) {
        for (int i = changes.size() - 1; i >= 0; i--) {
          replaceChange(changes.get(i), mySide, true);
        }
      }
    }

    private class ResolveSelectedConflictsAction extends ApplySelectedChangesActionBase {
      ResolveSelectedConflictsAction() {
        ActionUtil.copyFrom(this, "Diff.ResolveConflict");
      }

      @Override
      protected String getText(@NotNull ThreeSide side) {
        return DiffBundle.message("action.presentation.merge.resolve.automatically.text");
      }

      @Override
      protected boolean isVisible(@NotNull ThreeSide side) {
        return side == ThreeSide.BASE;
      }

      @Override
      protected boolean isEnabled(@NotNull TextMergeChange change) {
        return canResolveChangeAutomatically(change, ThreeSide.BASE);
      }

      @Override
      protected void apply(@NotNull ThreeSide side, @NotNull List<? extends TextMergeChange> changes) {
        for (int i = changes.size() - 1; i >= 0; i--) {
          TextMergeChange change = changes.get(i);
          resolveChangeAutomatically(change, ThreeSide.BASE);
        }
      }
    }

    public class ApplyNonConflictsAction extends DumbAwareAction {
      @NotNull private final ThreeSide mySide;

      public ApplyNonConflictsAction(@NotNull ThreeSide side, @NotNull @Nls String text) {
        String id = side.select("Diff.ApplyNonConflicts.Left", "Diff.ApplyNonConflicts", "Diff.ApplyNonConflicts.Right");
        ActionUtil.copyFrom(this, id);
        mySide = side;
        getTemplatePresentation().setText(text);
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(hasNonConflictedChanges(mySide));
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        applyNonConflictedChanges(mySide);
      }

      @Override
      public boolean displayTextInToolbar() {
        return true;
      }

      @Override
      public boolean useSmallerFontForTextInToolbar() {
        return true;
      }
    }

    public class MagicResolvedConflictsAction extends DumbAwareAction {
      public MagicResolvedConflictsAction() {
        ActionUtil.copyFrom(this, "Diff.MagicResolveConflicts");
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(hasResolvableConflictedChanges());
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        applyResolvableConflictedChanges();
      }
    }

    private class ShowDiffWithBaseAction extends DumbAwareAction {
      @NotNull private final ThreeSide mySide;

      ShowDiffWithBaseAction(@NotNull ThreeSide side) {
        mySide = side;
        String actionId = mySide.select("Diff.CompareWithBase.Left", "Diff.CompareWithBase.Result", "Diff.CompareWithBase.Right");
        ActionUtil.copyFrom(this, actionId);
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        DiffContent baseContent = ThreeSide.BASE.select(myMergeRequest.getContents());
        String baseTitle = ThreeSide.BASE.select(myMergeRequest.getContentTitles());

        DiffContent otherContent = mySide.select(myRequest.getContents());
        String otherTitle = mySide.select(myRequest.getContentTitles());

        SimpleDiffRequest request = new SimpleDiffRequest(myRequest.getTitle(), baseContent, otherContent, baseTitle, otherTitle);

        ThreeSide currentSide = getCurrentSide();
        LogicalPosition currentPosition = DiffUtil.getCaretPosition(getCurrentEditor());

        LogicalPosition resultPosition = transferPosition(currentSide, mySide, currentPosition);
        request.putUserData(DiffUserDataKeys.SCROLL_TO_LINE, Pair.create(Side.RIGHT, resultPosition.line));

        DiffManager.getInstance().showDiff(myProject, request, new DiffDialogHints(null, myPanel));
      }
    }

    //
    // Helpers
    //

    private class MyDividerPaintable implements DiffDividerDrawUtil.DividerPaintable {
      @NotNull private final Side mySide;

      MyDividerPaintable(@NotNull Side side) {
        mySide = side;
      }

      @Override
      public void process(@NotNull Handler handler) {
        ThreeSide left = mySide.select(ThreeSide.LEFT, ThreeSide.BASE);
        ThreeSide right = mySide.select(ThreeSide.BASE, ThreeSide.RIGHT);
        for (TextMergeChange mergeChange : myAllMergeChanges) {
          if (!mergeChange.isChange(mySide)) continue;
          boolean isResolved = mergeChange.isResolved(mySide);
          if (!handler.processResolvable(mergeChange.getStartLine(left), mergeChange.getEndLine(left),
                                         mergeChange.getStartLine(right), mergeChange.getEndLine(right),
                                         mergeChange.getDiffType(), isResolved)) {
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

    private class MyLineStatusMarkerRenderer extends LineStatusMarkerPopupRenderer {
      MyLineStatusMarkerRenderer(@NotNull LineStatusTrackerBase<?> tracker) {
        super(tracker);
      }

      @Nullable
      @Override
      protected MarkupEditorFilter getEditorFilter() {
        return editor -> editor == getEditor();
      }

      @Override
      public void scrollAndShow(@NotNull Editor editor, @NotNull Range range) {
        if (!myTracker.isValid()) return;
        final Document document = myTracker.getDocument();
        int line = Math.min(!range.hasLines() ? range.getLine2() : range.getLine2() - 1, getLineCount(document) - 1);

        int[] startLines = new int[]{
          transferPosition(ThreeSide.BASE, ThreeSide.LEFT, new LogicalPosition(line, 0)).line,
          line,
          transferPosition(ThreeSide.BASE, ThreeSide.RIGHT, new LogicalPosition(line, 0)).line
        };

        for (ThreeSide side : ThreeSide.values()) {
          DiffUtil.moveCaret(getEditor(side), side.select(startLines));
        }

        getEditor().getScrollingModel().scrollToCaret(ScrollType.CENTER);
        showAfterScroll(editor, range);
      }

      @NotNull
      @Override
      protected List<AnAction> createToolbarActions(@NotNull Editor editor, @NotNull Range range, @Nullable Point mousePosition) {
        List<AnAction> actions = new ArrayList<>();
        actions.add(new ShowPrevChangeMarkerAction(editor, range));
        actions.add(new ShowNextChangeMarkerAction(editor, range));
        actions.add(new MyRollbackLineStatusRangeAction(editor, range));
        actions.add(new ShowLineStatusRangeDiffAction(editor, range));
        actions.add(new CopyLineStatusRangeAction(editor, range));
        actions.add(new ToggleByWordDiffAction(editor, range, mousePosition));
        return actions;
      }

      private final class MyRollbackLineStatusRangeAction extends RangeMarkerAction {
        private MyRollbackLineStatusRangeAction(@NotNull Editor editor, @NotNull Range range) {
          super(editor, range, IdeActions.SELECTED_CHANGES_ROLLBACK);
        }

        @Override
        protected boolean isEnabled(@NotNull Editor editor, @NotNull Range range) {
          return true;
        }

        @Override
        protected void actionPerformed(@NotNull Editor editor, @NotNull Range range) {
          DiffUtil.moveCaretToLineRangeIfNeeded(editor, range.getLine1(), range.getLine2());
          myTracker.rollbackChanges(range);
        }
      }

      @Override
      protected void paint(@NotNull Editor editor, @NotNull Graphics g) {
        LineStatusMarkerDrawUtil.paintDefault(editor, g, myTracker, DefaultFlagsProvider.DEFAULT, JBUIScale.scale(2));
      }
    }

    private class NavigateToChangeMarkerAction extends DumbAwareAction {
      private final boolean myGoToNext;

      protected NavigateToChangeMarkerAction(boolean goToNext) {
        myGoToNext = goToNext;
        // TODO: reuse ShowChangeMarkerAction
        ActionUtil.copyFrom(this, myGoToNext ? "VcsShowNextChangeMarker" : "VcsShowPrevChangeMarker");
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(getTextSettings().isEnableLstGutterMarkersInMerge());
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        if (!myLineStatusTracker.isValid()) return;

        int line = getEditor().getCaretModel().getLogicalPosition().line;
        Range targetRange = myGoToNext ? myLineStatusTracker.getNextRange(line) : myLineStatusTracker.getPrevRange(line);
        if (targetRange != null) new MyLineStatusMarkerRenderer(myLineStatusTracker).scrollAndShow(getEditor(), targetRange);
      }
    }
  }

  private static class InnerChunkData {
    @NotNull public final List<CharSequence> text;

    InnerChunkData(@NotNull TextMergeChange change, @NotNull List<? extends Document> documents) {
      text = getChunks(change, documents);
    }

    @NotNull
    private static List<CharSequence> getChunks(@NotNull TextMergeChange change,
                                                @NotNull List<? extends Document> documents) {
      return ThreeSide.map(side -> {
        if (!change.isChange(side) || change.isResolved(side)) return null;

        int startLine = change.getStartLine(side);
        int endLine = change.getEndLine(side);
        if (startLine == endLine) return null;

        return DiffUtil.getLinesContent(side.select(documents), startLine, endLine);
      });
    }
  }
}
