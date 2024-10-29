// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge;

import com.intellij.diff.DiffContext;
import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManager;
import com.intellij.diff.actions.ProxyUndoRedoAction;
import com.intellij.diff.comparison.ComparisonManager;
import com.intellij.diff.comparison.ComparisonMergeUtil;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.comparison.DiffTooBigException;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.fragments.MergeLineFragment;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.statistics.MergeResultSource;
import com.intellij.diff.statistics.MergeStatisticsCollector;
import com.intellij.diff.tools.holders.EditorHolderFactory;
import com.intellij.diff.tools.holders.TextEditorHolder;
import com.intellij.diff.tools.simple.ThreesideTextDiffViewerEx;
import com.intellij.diff.tools.util.DiffNotifications;
import com.intellij.diff.tools.util.FoldingModelSupport;
import com.intellij.diff.tools.util.KeyboardModifierListener;
import com.intellij.diff.tools.util.StatusPanel;
import com.intellij.diff.tools.util.base.HighlightPolicy;
import com.intellij.diff.tools.util.base.IgnorePolicy;
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder;
import com.intellij.diff.tools.util.base.TextDiffViewerUtil;
import com.intellij.diff.tools.util.text.LineOffsets;
import com.intellij.diff.tools.util.text.LineOffsetsUtil;
import com.intellij.diff.tools.util.text.MergeInnerDifferences;
import com.intellij.diff.tools.util.text.TextDiffProviderBase;
import com.intellij.diff.util.*;
import com.intellij.icons.AllIcons;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.diff.DefaultFlagsProvider;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.LineStatusMarkerDrawUtil;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterFreePainterAreaState;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.progress.util.ProgressIndicatorWithDelayedPresentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ex.Range;
import com.intellij.openapi.vcs.ex.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.TreeTraversal;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.intellij.diff.merge.MergeImportUtil.getPsiFile;
import static com.intellij.diff.util.DiffUtil.getLineCount;
import static com.intellij.util.containers.ContainerUtil.ar;

@ApiStatus.Internal
public class MergeThreesideViewer extends ThreesideTextDiffViewerEx {
  @NotNull protected final MergeModelBase<TextMergeChange.State> myModel;

  @NotNull protected final ModifierProvider myModifierProvider;
  @NotNull protected final MyInnerDiffWorker myInnerDiffWorker;
  @NotNull protected final SimpleLineStatusTracker myLineStatusTracker;

  @NotNull protected final TextDiffProviderBase myTextDiffProvider;

  // all changes - both applied and unapplied ones
  @NotNull protected final List<TextMergeChange> myAllMergeChanges = new ArrayList<>();
  @NotNull protected IgnorePolicy myCurrentIgnorePolicy;

  protected boolean myInitialRediffStarted;
  protected boolean myInitialRediffFinished;
  protected boolean myContentModified;
  protected boolean myResolveImportConflicts;
  protected boolean myResolveImportsPossible;

  private List<PsiFile> myPsiFiles = new ArrayList<>();

  private final Action myCancelResolveAction;
  private final Action myLeftResolveAction;
  private final Action myRightResolveAction;
  protected final Action myAcceptResolveAction;
  private MergeStatisticsAggregator myAggregator;
  private ChangeReferenceProcessor myChangeReferenceProcessor;

  @NotNull protected final MergeContext myMergeContext;
  @NotNull protected final TextMergeRequest myMergeRequest;
  @NotNull protected final TextMergeViewer myTextMergeViewer;

  public MergeThreesideViewer(@NotNull DiffContext context,
                              @NotNull ContentDiffRequest request,
                              @NotNull MergeContext mergeContext,
                              @NotNull TextMergeRequest mergeRequest,
                              @NotNull TextMergeViewer mergeViewer) {
    super(context, request);
    myMergeContext = mergeContext;
    myMergeRequest = mergeRequest;
    myTextMergeViewer = mergeViewer;

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

    getTextSettings().addListener(new TextDiffSettingsHolder.TextDiffSettings.Listener() {
      @Override
      public void resolveConflictsInImportsChanged() {
        restartMergeResolveIfNeeded();
      }
    }, this);
    myCurrentIgnorePolicy = myTextDiffProvider.getIgnorePolicy();
    myResolveImportConflicts = getTextSettings().isAutoResolveImportConflicts();
    myCancelResolveAction = getResolveAction(MergeResult.CANCEL);
    myLeftResolveAction = getResolveAction(MergeResult.LEFT);
    myRightResolveAction = getResolveAction(MergeResult.RIGHT);
    myAcceptResolveAction = getResolveAction(MergeResult.RESOLVED);

    DiffUtil.registerAction(new NavigateToChangeMarkerAction(false), myPanel);
    DiffUtil.registerAction(new NavigateToChangeMarkerAction(true), myPanel);

    ProxyUndoRedoAction.register(getProject(), getEditor(), myContentPanel);
  }

  @Override
  protected @NotNull StatusPanel createStatusPanel() {
    return new MyMergeStatusPanel();
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

    AnAction additionalActions = ActionManager.getInstance().getAction("Diff.Conflicts.Additional.Actions");
    if (additionalActions instanceof ActionGroup) {
      group.add(additionalActions);
    }

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
    group.add(new ResetResolvedChangeAction());

    group.add(Separator.getInstance());
    group.add(ActionManager.getInstance().getAction("Diff.Conflicts.Additional.Actions"));
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
            !MergeUtil.showExitWithoutApplyingChangesDialog(myTextMergeViewer, myMergeRequest, myMergeContext, myContentModified)) {
          return;
        }
        doFinishMerge(result, MergeResultSource.DIALOG_BUTTON);
      }
    };
  }

  protected void doFinishMerge(@NotNull final MergeResult result, @NotNull MergeResultSource source) {
    logMergeResult(result, source);
    destroyChangedBlocks();
    myMergeContext.finishMerge(result);
  }

  //
  // Diff
  //

  private void restartMergeResolveIfNeeded() {
    if (isDisposed()) return;
    if (myTextDiffProvider.getIgnorePolicy().equals(myCurrentIgnorePolicy) &&
        getTextSettings().isAutoResolveImportConflicts() == myResolveImportConflicts) {
      return;
    }

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
        getTextSettings().setAutoResolveImportConflicts(myResolveImportConflicts);
        return;
      }
    }

    myInitialRediffFinished = false;
    doRediff();
  }

  protected boolean setInitialOutputContent(@NotNull CharSequence baseContent) {
    final Document outputDocument = myMergeRequest.getOutputContent().getDocument();

    return DiffUtil.executeWriteCommand(outputDocument, getProject(), DiffBundle.message("message.init.merge.content.command"), () -> {
      outputDocument.setText(baseContent);

      DiffUtil.putNonundoableOperation(getProject(), outputDocument);

      if (getTextSettings().isEnableLstGutterMarkersInMerge()) {
        myLineStatusTracker.setBaseRevision(baseContent);
        getEditor().getGutterComponentEx().setRightFreePaintersAreaState(EditorGutterFreePainterAreaState.SHOW);
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
                                         }), null, ProgressIndicatorWithDelayedPresentation.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS,
                                         ApplicationManager.getApplication().isUnitTestMode());
  }

  @NotNull
  protected Runnable doPerformRediff(@NotNull ProgressIndicator indicator) {
    try {
      List<CharSequence> sequences = new ArrayList<>();

      indicator.checkCanceled();
      IgnorePolicy ignorePolicy = myTextDiffProvider.getIgnorePolicy();

      List<DocumentContent> contents = myMergeRequest.getContents();
      MergeRange importRange = ReadAction.compute(() -> {
        sequences.addAll(ContainerUtil.map(contents, content -> content.getDocument().getImmutableCharSequence()));
        if (getTextSettings().isAutoResolveImportConflicts()) {
          initPsiFiles();
          boolean canImportsBeProcessedAutomatically = canImportsBeProcessedAutomatically();
          myResolveImportsPossible = canImportsBeProcessedAutomatically;
          if (canImportsBeProcessedAutomatically) {
            return MergeImportUtil.getImportMergeRange(myProject, myPsiFiles);
          }
        }
        return null;
      });

      MergeLineFragmentsWithImportMetadata lineFragments = getLineFragments(indicator, sequences, importRange, ignorePolicy);
      List<LineOffsets> lineOffsets = ContainerUtil.map(sequences, LineOffsetsUtil::create);
      List<MergeConflictType> conflictTypes = ContainerUtil.map(lineFragments.getFragments(), fragment -> {
        return MergeRangeUtil.getLineMergeType(fragment, sequences, lineOffsets, ignorePolicy.getComparisonPolicy());
      });

      FoldingModelSupport.Data foldingState =
        myFoldingModel.createState(lineFragments.getFragments(), lineOffsets, getFoldingModelSettings());

      return () -> apply(ThreeSide.BASE.select(sequences), lineFragments, conflictTypes, foldingState, ignorePolicy);
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

  @NotNull
  @ApiStatus.Internal
  public TextMergeRequest getMergeRequest() {
    return myMergeRequest;
  }

  private static MergeLineFragmentsWithImportMetadata getLineFragments(@NotNull ProgressIndicator indicator,
                                                                       @NotNull List<CharSequence> sequences,
                                                                       @Nullable MergeRange importRange,
                                                                       @NotNull IgnorePolicy ignorePolicy) {
    if (importRange != null) {
      return MergeImportUtil.getDividedFromImportsFragments(sequences, ignorePolicy.getComparisonPolicy(), importRange, indicator);
    }
    ComparisonManager manager = ComparisonManager.getInstance();

    List<MergeLineFragment> fragments = manager.mergeLines(sequences.get(0), sequences.get(1), sequences.get(2),
                                                           ignorePolicy.getComparisonPolicy(), indicator);
    return new MergeLineFragmentsWithImportMetadata(fragments);
  }

  private void initPsiFiles() {
    if (myProject == null) return;
    ArrayList<PsiFile> files = new ArrayList<>();
    for (ThreeSide value : ThreeSide.values()) {
      PsiFile psiFile = getPsiFile(value, myProject, myMergeRequest);
      if (psiFile != null) {
        files.add(psiFile);
      }
    }
    myPsiFiles = files;
  }


  @RequiresEdt
  private void apply(@NotNull CharSequence baseContent,
                     @NotNull MergeLineFragmentsWithImportMetadata fragmentsWithMetadata,
                     @NotNull List<? extends MergeConflictType> conflictTypes,
                     @Nullable FoldingModelSupport.Data foldingState,
                     @NotNull IgnorePolicy ignorePolicy) {
    if (isDisposed()) return;
    myFoldingModel.updateContext(myRequest, getFoldingModelSettings());
    clearDiffPresentation();
    resetChangeCounters();

    boolean success = setInitialOutputContent(baseContent);
    List<MergeLineFragment> fragments = fragmentsWithMetadata.getFragments();

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

      boolean isInImportRange = fragmentsWithMetadata.isIndexInImportRange(index);
      TextMergeChange change = new TextMergeChange(index, isInImportRange, fragment, conflictType, myTextMergeViewer);

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
    myResolveImportConflicts = getTextSettings().isAutoResolveImportConflicts();

    // build initial statistics
    int autoResolvableChanges = ContainerUtil.count(getAllChanges(), c -> canResolveChangeAutomatically(c, ThreeSide.BASE));

    myAggregator = new MergeStatisticsAggregator(
      getAllChanges().size(),
      autoResolvableChanges,
      getConflictsCount()
    );

    if (myResolveImportConflicts) {
      myChangeReferenceProcessor =
        new ChangeReferenceProcessor(myProject, getEditor(), myPsiFiles,
                                     ContainerUtil.map(myMergeRequest.getContents(), content -> content.getDocument()));
      List<TextMergeChange> importChanges = ContainerUtil.filter(getChanges(), change -> change.isImportChange());
      if (importChanges.size() != fragmentsWithMetadata.getFragments().size()) {
        for (TextMergeChange importChange : importChanges) {
          markChangeResolved(importChange);
        }
      }
    }
    if (getTextSettings().isAutoApplyNonConflictedChanges()) {
      if (hasNonConflictedChanges(ThreeSide.BASE)) {
        applyNonConflictedChanges(ThreeSide.BASE);
      }
    }
  }

  private boolean canImportsBeProcessedAutomatically() {
    try {
      return canSideBeProcessed(ThreeSide.LEFT) && canSideBeProcessed(ThreeSide.RIGHT);
    }
    catch (Exception e) {
      LOG.error(e);
    }
    return false;
  }

  private boolean canSideBeProcessed(ThreeSide side) {
    if (DumbService.isDumb(myProject)) return false;
    AtomicReference<Boolean> atLeastOnReferenceFound = new AtomicReference<>(false);
    return SyntaxTraverser.psiTraverser(side.select(myPsiFiles))
             .traverse(TreeTraversal.PLAIN_BFS)
             .processEach(element -> {
               PsiReference reference = element.getReference();
               if (reference == null) return true;
               atLeastOnReferenceFound.set(true);
               PsiElement resolved = reference.resolve();
               return resolved != null || reference.isSoft();
             }) && atLeastOnReferenceFound.get();
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

  public Action getLoadedResolveAction(@NotNull MergeResult result) {
    return switch (result) {
      case CANCEL -> myCancelResolveAction;
      case LEFT -> myLeftResolveAction;
      case RIGHT -> myRightResolveAction;
      case RESOLVED -> myAcceptResolveAction;
    };
  }

  public boolean isContentModified() {
    return myContentModified;
  }

  //
  // By-word diff
  //

  protected class MyInnerDiffWorker {
    @NotNull private final Set<TextMergeChange> myScheduled = new HashSet<>();

    @NotNull private final Alarm myAlarm = new Alarm(MergeThreesideViewer.this);
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
      ProgressIndicator progress =
        BackgroundTaskUtil.executeAndTryWait(indicator -> performRediff(scheduled, data, indicator), null, waitMillis, false);

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

  protected void onChangeResolved(@NotNull TextMergeChange change) {
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

        String title = DiffBundle.message("merge.all.changes.processed.title.text");
        @NlsSafe String message = XmlStringUtil.wrapInHtmlTag(DiffBundle.message("merge.all.changes.processed.message.text"), "a");
        DiffBalloons.showSuccessPopup(title, message, point, this, () -> {
          if (isDisposed() || myLoadingPanel.isLoading()) return;
          doFinishMerge(MergeResult.RESOLVED, MergeResultSource.NOTIFICATION);
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

  @ApiStatus.Internal
  @RequiresEdt
  public void resolveChangeWithAiAnswer(@NotNull TextMergeChange change, @NotNull List<String> newContentLines) {
    processChangesAndTransferData(Collections.singletonList(change), ThreeSide.BASE, (c) -> {
      return replaceChangeWithAi(change, newContentLines);
    });
  }

  private LineRange replaceChangeWithAi(@NotNull TextMergeChange change, @NotNull List<String> newContentLines) {
    if (change.isResolved()) return null;

    myModel.replaceChange(change.getIndex(), newContentLines);
    markChangeResolvedWithAI(change);
    return new LineRange(myModel.getLineStart(change.getIndex()), myModel.getLineEnd(change.getIndex()));
  }

  @ApiStatus.Internal
  @RequiresEdt
  private void markChangeResolvedWithAI(@NotNull TextMergeChange change) {
    myAggregator.wasResolvedByAi(change.getIndex());
    change.markChangeResolvedWithAI();
    markChangeResolved(change);
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
  public LineRange replaceChange(@NotNull TextMergeChange change,
                                 @NotNull Side side,
                                 boolean resolveChange) {
    if (change.isResolved(side)) return null;
    if (!change.isChange(side)) {
      markChangeResolved(change);
      return null;
    }

    ThreeSide sourceSide = side.select(ThreeSide.LEFT, ThreeSide.RIGHT);
    ThreeSide oppositeSide = side.select(ThreeSide.RIGHT, ThreeSide.LEFT);

    Document sourceDocument = getContent(sourceSide).getDocument();
    int sourceStartLine = change.getStartLine(sourceSide);
    int sourceEndLine = change.getEndLine(sourceSide);
    List<String> newContent = DiffUtil.getLines(sourceDocument, sourceStartLine, sourceEndLine);

    int newLineStart;
    if (change.isConflict()) {
      boolean append = change.isOnesideAppliedConflict();
      if (append) {
        newLineStart = myModel.getLineEnd(change.getIndex());
        myModel.appendChange(change.getIndex(), newContent);
      }
      else {
        myModel.replaceChange(change.getIndex(), newContent);
        newLineStart = myModel.getLineStart(change.getIndex());
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
      newLineStart = myModel.getLineStart(change.getIndex());
      markChangeResolved(change);
    }
    int newLineEnd = myModel.getLineEnd(change.getIndex());
    return new LineRange(newLineStart, newLineEnd);
  }

  @ApiStatus.Internal
  @RequiresWriteLock
  void resetResolvedChange(TextMergeChange change) {
    if (!change.isResolved()) return;
    MergeLineFragment changeFragment = change.getFragment();
    int startLine = changeFragment.getStartLine(ThreeSide.BASE);
    int endLine = changeFragment.getEndLine(ThreeSide.BASE);

    Document content = ThreeSide.BASE.select(myMergeRequest.getContents()).getDocument();
    List<String> baseContent = DiffUtil.getLines(content, startLine, endLine);

    myModel.replaceChange(change.getIndex(), baseContent);

    change.resetState();
    if (change.isResolvedWithAI()) {
      myAggregator.wasRolledBackAfterAI(change.getIndex());
    }
    onChangeResolved(change);
    myModel.invalidateHighlighters(change.getIndex());
  }

  protected class MyMergeModel extends MergeModelBase<TextMergeChange.State> {
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
      if (change.isResolvedWithAI()) {
        myAggregator.wasUndoneAfterAI(change.getIndex());
      }
      change.restoreState(state);
      if (wasResolved != change.isResolved()) onChangeResolved(change);
    }

    @Nullable
    @Override
    protected TextMergeChange.State processDocumentChange(int index, int oldLine1, int oldLine2, int shift) {
      TextMergeChange.State state = super.processDocumentChange(index, oldLine1, oldLine2, shift);

      TextMergeChange mergeChange = myAllMergeChanges.get(index);
      if (mergeChange.getStartLine() == mergeChange.getEndLine() &&
          mergeChange.getConflictType().getType() == MergeConflictType.Type.DELETED && !mergeChange.isResolved()) {
        markChangeResolved(mergeChange);
      }

      return state;
    }

    @Override
    protected void onRangeManuallyEdit(int index) {
      TextMergeChange change = myAllMergeChanges.get(index);
      if (change.isResolvedWithAI()) {
        myAggregator.wasEditedAfterAi(index);
      }
      else {
        myAggregator.wasEdited(index);
      }
    }
  }

  //
  // Actions
  //

  protected boolean hasNonConflictedChanges(@NotNull ThreeSide side) {
    return ContainerUtil.exists(getAllChanges(), change -> !change.isConflict() && canResolveChangeAutomatically(change, side));
  }

  protected void applyNonConflictedChanges(@NotNull ThreeSide side) {
    executeMergeCommand(DiffBundle.message("merge.dialog.apply.non.conflicted.changes.command"), true, null, () -> {
      resolveChangesAutomatically(ContainerUtil.filter(getAllChanges(), change -> !change.isConflict()), side);
    });

    TextMergeChange firstUnresolved = ContainerUtil.find(getAllChanges(), c -> !c.isResolved());
    if (firstUnresolved != null) doScrollToChange(firstUnresolved, true);
  }

  private boolean hasResolvableConflictedChanges() {
    return ContainerUtil.exists(getAllChanges(), change -> canResolveChangeAutomatically(change, ThreeSide.BASE));
  }

  @ApiStatus.Internal
  public void applyResolvableConflictedChanges() {
    List<TextMergeChange> changes = getAllChanges();
    executeMergeCommand(DiffBundle.message("message.resolve.simple.conflicts.command"), true, null, () -> {
      resolveChangesAutomatically(changes, ThreeSide.BASE);
    });

    TextMergeChange firstUnresolved = ContainerUtil.find(changes, c -> !c.isResolved());
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

  public void resolveChangesAutomatically(@NotNull List<? extends TextMergeChange> changes,
                                          @NotNull ThreeSide threeSide) {
    processChangesAndTransferData(changes, threeSide, (change) -> resolveChangeAutomatically(change, threeSide));
  }

  public void resolveSingleChangeAutomatically(@NotNull TextMergeChange change,
                                               @NotNull ThreeSide side) {
    resolveChangesAutomatically(Collections.singletonList(change), side);
  }

  public void replaceChanges(@NotNull List<? extends TextMergeChange> changes,
                             @NotNull Side side,
                             @NotNull Boolean resolveChanges) {
    processChangesAndTransferData(changes, side.select(ThreeSide.LEFT, ThreeSide.RIGHT),
                                  (change) -> replaceChange(change, side, resolveChanges));
  }

  public void replaceSingleChange(@NotNull TextMergeChange change,
                                  @NotNull Side side,
                                  boolean resolveChange) {
    replaceChanges(Collections.singletonList(change), side, resolveChange);
  }

  private void processChangesAndTransferData(@NotNull List<? extends TextMergeChange> changes, @NotNull ThreeSide side,
                                             @NotNull Function<TextMergeChange, LineRange> processor) {
    ArrayList<LineRange> newRanges = new ArrayList<>();
    List<TextMergeChange> filteredChanges = new ArrayList<>();
    for (TextMergeChange change : changes) {
      if (change.isImportChange()) {
        continue;
      }
      LineRange newRange = processor.apply(change);
      if (newRange != null) {
        newRanges.add(newRange);
        filteredChanges.add(change);
      }
    }
    transferReferenceData(filteredChanges, side, newRanges);
  }

  private void transferReferenceData(@NotNull List<? extends TextMergeChange> changes, @NotNull ThreeSide side, List<LineRange> newRanges) {
    if (myResolveImportConflicts && myPsiFiles.size() == 3) {
      Document document = getContent(ThreeSide.BASE).getDocument();
      List<RangeMarker> markers = ContainerUtil.map(newRanges, range ->
        document.createRangeMarker(DiffUtil.getLinesRange(document, range.start, range.end)));
      myChangeReferenceProcessor.process(side, changes, markers);
      markers.forEach(RangeMarker::dispose);
    }
  }

  public LineRange resolveChangeAutomatically(@NotNull TextMergeChange change,
                                              @NotNull ThreeSide side) {
    if (!canResolveChangeAutomatically(change, side)) return null;

    if (change.isConflict()) {
      List<CharSequence> texts =
        ThreeSide.map(it -> DiffUtil.getLinesContent(getEditor(it).getDocument(), change.getStartLine(it), change.getEndLine(it)));

      CharSequence newContent = ComparisonMergeUtil.tryResolveConflict(texts.get(0), texts.get(1), texts.get(2));
      if (newContent == null) {
        LOG.warn(String.format("Can't resolve conflicting change:\n'%s'\n'%s'\n'%s'\n", texts.get(0), texts.get(1), texts.get(2)));
        return null;
      }

      String[] newContentLines = LineTokenizer.tokenize(newContent, false);
      myModel.replaceChange(change.getIndex(), Arrays.asList(newContentLines));
      markChangeResolved(change);
      return new LineRange(myModel.getLineStart(change.getIndex()), myModel.getLineEnd(change.getIndex()));
    }
    else {
      Side masterSide = side.select(Side.LEFT,
                                    change.isChange(Side.LEFT) ? Side.LEFT : Side.RIGHT,
                                    Side.RIGHT);
      return replaceChange(change, masterSide, false);
    }
  }

  private static final Key<Boolean> EXTERNAL_OPERATION_IN_PROGRESS = Key.create("external.resolve.operation");

  /**
   * Allows running external heavy operations and blocks the UI during execution.
   */
  @ApiStatus.Internal
  @RequiresEdt
  public <T> void runExternalResolver(CompletableFuture<? extends T> operation,
                                      Consumer<T> resultHandler,
                                      Consumer<? super Throwable> errorHandler) {
    runBeforeExternalOperation();

    operation.whenComplete((result, throwable) -> {

      Runnable runnable = () -> {
        if (isDisposed()) return;
        runAfterExternalOperation();

        if (throwable != null) {
          errorHandler.accept(throwable);
          return;
        }

        if (result != null) {
          resultHandler.accept(result);
        }
      };

      ApplicationManager.getApplication().invokeLater(runnable, ModalityState.stateForComponent(getComponent()));
    });
  }

  protected boolean isExternalOperationInProgress() {
    return Boolean.TRUE.equals(myMergeContext.getUserData(EXTERNAL_OPERATION_IN_PROGRESS));
  }

  @RequiresEdt
  private void runBeforeExternalOperation() {
    myMergeContext.putUserData(EXTERNAL_OPERATION_IN_PROGRESS, true);
    enableResolveActions(false);
    getEditor().setViewer(true);

    for (TextMergeChange change : getAllChanges()) {
      change.reinstallHighlighters();
    }
  }

  @RequiresEdt
  private void runAfterExternalOperation() {
    myMergeContext.putUserData(EXTERNAL_OPERATION_IN_PROGRESS, null);
    enableResolveActions(true);
    getEditor().setViewer(false);

    for (TextMergeChange change : getAllChanges()) {
      change.reinstallHighlighters();
    }
  }

  private void enableResolveActions(boolean enable) {
    myLeftResolveAction.setEnabled(enable);
    myRightResolveAction.setEnabled(enable);
    myAcceptResolveAction.setEnabled(enable);
  }

  private abstract class ApplySelectedChangesActionBase extends AnAction implements DumbAware {
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

      presentation.setEnabledAndVisible(isSomeChangeSelected(side) && !isExternalOperationInProgress());
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

    @RequiresWriteLock
    protected abstract void apply(@NotNull ThreeSide side, @NotNull List<? extends TextMergeChange> changes);

    private boolean isSomeChangeSelected(@NotNull ThreeSide side) {
      EditorEx editor = getEditor(side);
      return DiffUtil.isSomeRangeSelected(editor,
                                          lines -> ContainerUtil.exists(getAllChanges(), change -> isChangeSelected(change, lines, side)));
    }

    @NotNull
    @RequiresEdt
    protected List<TextMergeChange> getSelectedChanges(@NotNull ThreeSide side) {
      EditorEx editor = getEditor(side);
      BitSet lines = DiffUtil.getSelectedLines(editor);
      return ContainerUtil.filter(getChanges(), change -> isChangeSelected(change, lines, side));
    }

    protected boolean isChangeSelected(@NotNull TextMergeChange change, @NotNull BitSet lines, @NotNull ThreeSide side) {
      if (!isEnabled(change)) return false;
      int line1 = change.getStartLine(side);
      int line2 = change.getEndLine(side);
      return DiffUtil.isSelectedByLine(lines, line1, line2);
    }

    @Nls
    protected abstract String getText(@NotNull ThreeSide side);

    protected abstract boolean isVisible(@NotNull ThreeSide side);

    protected abstract boolean isEnabled(@NotNull TextMergeChange change);
  }

  @ApiStatus.Internal
  void logMergeCancelled() {
    logMergeResult(MergeResult.CANCEL, MergeResultSource.DIALOG_CLOSING);
  }

  private void logMergeResult(MergeResult mergeResult, MergeResultSource source) {
    MergeStatisticsCollector.MergeResult statsResult = switch (mergeResult) {
      case CANCEL -> MergeStatisticsCollector.MergeResult.CANCELED;
      case RESOLVED -> MergeStatisticsCollector.MergeResult.SUCCESS;
      case LEFT, RIGHT -> null;
    };
    if (statsResult == null) return;
    myAggregator.setUnresolved(getChanges().size());
    MergeStatisticsCollector.INSTANCE.logMergeFinished(myProject, statsResult, source, myAggregator);
  }

  private class IgnoreSelectedChangesSideAction extends ApplySelectedChangesActionBase {
    @NotNull private final Side mySide;

    IgnoreSelectedChangesSideAction(@NotNull Side side) {
      mySide = side;
      ActionUtil.copyFrom(this, mySide.select("Diff.IgnoreLeftSide", "Diff.IgnoreRightSide"));
    }

    @Override
    protected void apply(@NotNull ThreeSide side, @NotNull List<? extends TextMergeChange> changes) {
      for (TextMergeChange change : changes) {
        ignoreChange(change, mySide, false);
      }
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

  private class ResetResolvedChangeAction extends ApplySelectedChangesActionBase {
    ResetResolvedChangeAction() {
      getTemplatePresentation().setIcon(AllIcons.Diff.Revert);
    }

    @Override
    protected void apply(@NotNull ThreeSide side, @NotNull List<? extends TextMergeChange> changes) {
      for (TextMergeChange change : changes) {
        resetResolvedChange(change);
      }
    }

    @Override
    protected @NotNull List<TextMergeChange> getSelectedChanges(@NotNull ThreeSide side) {
      EditorEx editor = getEditor(side);
      BitSet lines = DiffUtil.getSelectedLines(editor);
      return ContainerUtil.filter(getAllChanges(), change -> isChangeSelected(change, lines, side));
    }

    @Nls
    @Override
    protected String getText(@NotNull ThreeSide side) {
      return DiffBundle.message("action.presentation.diff.revert.text");
    }

    @Override
    protected boolean isVisible(@NotNull ThreeSide side) {
      return true;
    }

    @Override
    protected boolean isEnabled(@NotNull TextMergeChange change) {
      return change.isResolvedWithAI();
    }
  }

  private class ApplySelectedChangesAction extends ApplySelectedChangesActionBase {
    @NotNull private final Side mySide;

    ApplySelectedChangesAction(@NotNull Side side) {
      mySide = side;
      ActionUtil.copyFrom(this, mySide.select("Diff.ApplyLeftSide", "Diff.ApplyRightSide"));
    }

    @Override
    protected void apply(@NotNull ThreeSide side, @NotNull List<? extends TextMergeChange> changes) {
      replaceChanges(changes, mySide, false);
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
  }

  private class ResolveSelectedChangesAction extends ApplySelectedChangesActionBase {
    @NotNull private final Side mySide;

    ResolveSelectedChangesAction(@NotNull Side side) {
      mySide = side;
    }

    @Override
    protected void apply(@NotNull ThreeSide side, @NotNull List<? extends TextMergeChange> changes) {
      replaceChanges(changes, mySide, true);
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
  }

  private class ResolveSelectedConflictsAction extends ApplySelectedChangesActionBase {
    ResolveSelectedConflictsAction() {
      ActionUtil.copyFrom(this, "Diff.ResolveConflict");
    }

    @Override
    protected void apply(@NotNull ThreeSide side, @NotNull List<? extends TextMergeChange> changes) {
      resolveChangesAutomatically(changes, ThreeSide.BASE);
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
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(hasNonConflictedChanges(mySide) && !isExternalOperationInProgress());
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
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(hasResolvableConflictedChanges() && !isExternalOperationInProgress());
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
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(!isExternalOperationInProgress());
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
      init(myPanel, myTextMergeViewer);
    }

    @Override
    public void onModifiersChanged() {
      for (TextMergeChange change : myAllMergeChanges) {
        change.updateGutterActions(false);
      }
    }
  }

  private class MyLineStatusMarkerRenderer extends LineStatusTrackerMarkerRenderer {
    private final @NotNull LineStatusTrackerBase<?> myTracker;

    MyLineStatusMarkerRenderer(@NotNull LineStatusTrackerBase<?> tracker) {
      super(tracker, editor -> editor == getEditor());
      myTracker = tracker;
    }

    @Override
    public void scrollAndShow(@NotNull Editor editor, @NotNull com.intellij.openapi.vcs.ex.Range range) {
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
    protected List<AnAction> createToolbarActions(@NotNull Editor editor,
                                                  @NotNull com.intellij.openapi.vcs.ex.Range range,
                                                  @Nullable Point mousePosition) {
      List<AnAction> actions = new ArrayList<>();
      actions.add(new LineStatusMarkerPopupActions.ShowPrevChangeMarkerAction(editor, myTracker, range, this));
      actions.add(new LineStatusMarkerPopupActions.ShowNextChangeMarkerAction(editor, myTracker, range, this));
      actions.add(new MyRollbackLineStatusRangeAction(editor, range));
      actions.add(new LineStatusMarkerPopupActions.ShowLineStatusRangeDiffAction(editor, myTracker, range));
      actions.add(new LineStatusMarkerPopupActions.CopyLineStatusRangeAction(editor, myTracker, range));
      actions.add(new LineStatusMarkerPopupActions.ToggleByWordDiffAction(editor, myTracker, range, mousePosition, this));
      return actions;
    }

    private final class MyRollbackLineStatusRangeAction extends LineStatusMarkerPopupActions.RangeMarkerAction {
      private MyRollbackLineStatusRangeAction(@NotNull Editor editor, @NotNull com.intellij.openapi.vcs.ex.Range range) {
        super(editor, myTracker, range, IdeActions.SELECTED_CHANGES_ROLLBACK);
      }

      @Override
      protected boolean isEnabled(@NotNull Editor editor, @NotNull com.intellij.openapi.vcs.ex.Range range) {
        return true;
      }

      @Override
      protected void actionPerformed(@NotNull Editor editor, @NotNull com.intellij.openapi.vcs.ex.Range range) {
        DiffUtil.moveCaretToLineRangeIfNeeded(editor, range.getLine1(), range.getLine2());
        myTracker.rollbackChanges(range);
      }
    }

    @Override
    protected void paintGutterMarkers(@NotNull Editor editor, @NotNull List<? extends Range> ranges, @NotNull Graphics g) {
      int framingBorder = JBUIScale.scale(2);
      LineStatusMarkerDrawUtil.paintDefault(editor, g, ranges, DefaultFlagsProvider.DEFAULT, framingBorder);
    }

    @Override
    public String toString() {
      return "MergeThreesideViewer.MyLineStatusMarkerRenderer{" +
             "myTracker=" + myTracker +
             '}';
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
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
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

  private class MyMergeStatusPanel extends MyStatusPanel {
    /**
     * For classic UI.
     *
     * @see community/platform/icons/src/general/greenCheckmark.svg
     */
    private static final JBColor GREEN_CHECKMARK_DEFAULT_COLOR = new JBColor(0x368746, 0x50A661);
    private static final JBColor NO_CONFLICTS_FOREGROUND =
      JBColor.namedColor("VersionControl.Merge.Status.NoConflicts.foreground", GREEN_CHECKMARK_DEFAULT_COLOR);

    @Override
    protected @Nullable Icon getStatusIcon() {
      if (getChangesCount() == 0 && getConflictsCount() == 0) {
        return AllIcons.General.GreenCheckmark;
      }
      return null;
    }

    @Override
    protected @NotNull Color getStatusForeground() {
      if (getChangesCount() == 0 && getConflictsCount() == 0) {
        return NO_CONFLICTS_FOREGROUND;
      }
      return UIUtil.getLabelForeground();
    }
  }
}

