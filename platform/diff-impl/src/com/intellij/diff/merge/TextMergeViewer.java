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
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.tools.simple.ThreesideTextDiffViewerEx;
import com.intellij.diff.tools.util.DiffNotifications;
import com.intellij.diff.tools.util.KeyboardModifierListener;
import com.intellij.diff.tools.util.base.HighlightPolicy;
import com.intellij.diff.tools.util.base.IgnorePolicy;
import com.intellij.diff.tools.util.text.LineOffsets;
import com.intellij.diff.tools.util.text.LineOffsetsUtil;
import com.intellij.diff.tools.util.text.MergeInnerDifferences;
import com.intellij.diff.tools.util.text.TextDiffProviderBase;
import com.intellij.diff.util.*;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ex.*;
import com.intellij.openapi.vcs.ex.Range;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

import static com.intellij.diff.util.DiffUtil.getLineCount;
import static com.intellij.util.containers.ContainerUtil.ar;

public class TextMergeViewer implements MergeTool.MergeViewer {
  private static final Logger LOG = Logger.getInstance(TextMergeViewer.class);

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

    return ContainerUtil.list(left, output, right);
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

    components.closeHandler = () -> MergeUtil.showExitWithoutApplyingChangesDialog(this, myMergeRequest, myMergeContext);

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
    @NotNull private final MergeModelBase myModel;

    @NotNull private final ModifierProvider myModifierProvider;
    @NotNull private final MyInnerDiffWorker myInnerDiffWorker;
    @NotNull private final MyLineStatusTracker myLineStatusTracker;

    @NotNull private final TextDiffProviderBase myTextDiffProvider;

    // all changes - both applied and unapplied ones
    @NotNull private final List<TextMergeChange> myAllMergeChanges = new ArrayList<>();

    private boolean myInitialRediffStarted;
    private boolean myInitialRediffFinished;
    private boolean myContentModified;

    public MyThreesideViewer(@NotNull DiffContext context, @NotNull ContentDiffRequest request) {
      super(context, request);

      myModel = new MyMergeModel(getProject(), getEditor().getDocument());

      myModifierProvider = new ModifierProvider();
      myInnerDiffWorker = new MyInnerDiffWorker();

      myLineStatusTracker = new MyLineStatusTracker(getProject(), getEditor().getDocument());

      myTextDiffProvider = new TextDiffProviderBase(getTextSettings(),
                                                    myInnerDiffWorker::onSettingsChanged,
                                                    this,
                                                    ar(IgnorePolicy.DEFAULT),
                                                    ar(HighlightPolicy.BY_LINE, HighlightPolicy.BY_WORD));

      DiffUtil.registerAction(new ApplySelectedChangesAction(Side.LEFT, true), myPanel);
      DiffUtil.registerAction(new ApplySelectedChangesAction(Side.RIGHT, true), myPanel);
      DiffUtil.registerAction(new IgnoreSelectedChangesSideAction(Side.LEFT, true), myPanel);
      DiffUtil.registerAction(new IgnoreSelectedChangesSideAction(Side.RIGHT, true), myPanel);
      DiffUtil.registerAction(new ResolveSelectedConflictsAction(true), myPanel);
      DiffUtil.registerAction(new MyShowPrevChangeMarkerAction(null), myPanel);
      DiffUtil.registerAction(new MyShowNextChangeMarkerAction(null), myPanel);

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
      super.onDispose();
    }

    @NotNull
    @Override
    protected List<AnAction> createToolbarActions() {
      List<AnAction> group = new ArrayList<>();

      group.addAll(myTextDiffProvider.getToolbarActions());
      group.add(new MyToggleAutoScrollAction());
      group.add(myEditorSettingsAction);

      DefaultActionGroup diffGroup = new DefaultActionGroup("Compare With", true);
      diffGroup.getTemplatePresentation().setIcon(AllIcons.Actions.Diff);
      diffGroup.add(new TextShowPartialDiffAction(PartialDiffMode.LEFT_MIDDLE, true));
      diffGroup.add(new TextShowPartialDiffAction(PartialDiffMode.RIGHT_MIDDLE, true));
      diffGroup.add(new TextShowPartialDiffAction(PartialDiffMode.LEFT_RIGHT, true));
      diffGroup.add(new ShowDiffWithBaseAction(ThreeSide.LEFT));
      diffGroup.add(new ShowDiffWithBaseAction(ThreeSide.BASE));
      diffGroup.add(new ShowDiffWithBaseAction(ThreeSide.RIGHT));
      group.add(diffGroup);

      group.add(Separator.getInstance());
      group.add(new ApplyNonConflictsAction(ThreeSide.BASE));
      group.add(new ApplyNonConflictsAction(ThreeSide.LEFT));
      group.add(new ApplyNonConflictsAction(ThreeSide.RIGHT));

      return group;
    }

    @NotNull
    @Override
    protected List<AnAction> createEditorPopupActions() {
      List<AnAction> group = new ArrayList<>();

      group.add(new ApplySelectedChangesAction(Side.LEFT, false));
      group.add(new ApplySelectedChangesAction(Side.RIGHT, false));
      group.add(new ResolveSelectedChangesAction(Side.LEFT));
      group.add(new ResolveSelectedChangesAction(Side.RIGHT));
      group.add(new IgnoreSelectedChangesSideAction(Side.LEFT, false));
      group.add(new IgnoreSelectedChangesSideAction(Side.RIGHT, false));
      group.add(new ResolveSelectedConflictsAction(false));
      group.add(new IgnoreSelectedChangesAction());

      group.add(Separator.getInstance());
      group.addAll(super.createEditorPopupActions());

      return group;
    }

    @Nullable
    @Override
    protected List<AnAction> createPopupActions() {
      List<AnAction> group = new ArrayList<>();

      group.addAll(myTextDiffProvider.getPopupActions());
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
            if ((getChangesCount() > 0 || getConflictsCount() > 0) &&
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

    private boolean setInitialOutputContent() {
      final Document baseDocument = ThreeSide.BASE.select(myMergeRequest.getContents()).getDocument();
      final Document outputDocument = myMergeRequest.getOutputContent().getDocument();

      return DiffUtil.executeWriteCommand(outputDocument, getProject(), "Init merge content", () -> {
        outputDocument.setText(baseDocument.getCharsSequence());

        DiffUtil.putNonundoableOperation(getProject(), outputDocument);

        if (getTextSettings().isEnableLstGutterMarkersInMerge()) {
          myLineStatusTracker.setBaseRevision(baseDocument.getCharsSequence());
          getEditor().getGutterComponentEx().setForceShowRightFreePaintersArea(true);
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

      // we need invokeLater() here because viewer is partially-initialized (ex: there are no toolbar or status panel)
      // user can see this state while we're showing progress indicator, so we want let init() to finish.
      ApplicationManager.getApplication().invokeLater(() -> {
        ProgressManager.getInstance().run(new Task.Modal(getProject(), "Computing Differences...", true) {
          private Runnable myCallback;

          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            myCallback = doPerformRediff(indicator);
          }

          @Override
          public void onCancel() {
            myMergeContext.finishMerge(MergeResult.CANCEL);
          }

          @Override
          public void onThrowable(@NotNull Throwable error) {
            LOG.error(error);
            myMergeContext.finishMerge(MergeResult.CANCEL);
          }

          @Override
          public void onSuccess() {
            if (isDisposed()) return;
            myCallback.run();
          }
        });
      });
    }

    @NotNull
    protected Runnable doPerformRediff(@NotNull ProgressIndicator indicator) {
      try {
        indicator.checkCanceled();

        List<DocumentContent> contents = myMergeRequest.getContents();
        List<CharSequence> sequences = ReadAction.compute(() -> {
          indicator.checkCanceled();
          return ContainerUtil.map(contents, content -> content.getDocument().getImmutableCharSequence());
        });
        List<LineOffsets> lineOffsets = ContainerUtil.map(sequences, LineOffsetsUtil::create);

        ComparisonManager manager = ComparisonManager.getInstance();
        List<MergeLineFragment> lineFragments = manager.compareLines(sequences.get(0), sequences.get(1), sequences.get(2),
                                                                     ComparisonPolicy.DEFAULT, indicator);

        List<MergeConflictType> conflictTypes = ContainerUtil.map(lineFragments, fragment -> {
          return DiffUtil.getLineMergeType(fragment, sequences, lineOffsets, ComparisonPolicy.DEFAULT);
        });

        return apply(lineFragments, conflictTypes);
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
    private Runnable apply(@NotNull final List<MergeLineFragment> fragments,
                           @NotNull final List<MergeConflictType> conflictTypes) {
      return () -> {
        clearDiffPresentation();

        boolean success = setInitialOutputContent();
        if (!success) {
          myPanel.addNotification(DiffNotifications.createNotification("Can't resolve conflicts in a read-only file"));
          return;
        }

        myModel.setChanges(ContainerUtil.map(fragments, f -> new LineRange(f.getStartLine(ThreeSide.BASE),
                                                                           f.getEndLine(ThreeSide.BASE))));

        resetChangeCounters();
        for (int index = 0; index < fragments.size(); index++) {
          MergeLineFragment fragment = fragments.get(index);
          MergeConflictType conflictType = conflictTypes.get(index);

          TextMergeChange change = new TextMergeChange(index, fragment, conflictType, TextMergeViewer.this);
          myAllMergeChanges.add(change);
          onChangeAdded(change);
        }

        myInitialScrollHelper.onRediff();

        myContentPanel.repaintDividers();
        myStatusPanel.update();

        getEditor().setViewer(false);

        myInnerDiffWorker.onSettingsChanged();
        myInitialRediffFinished = true;

        if (myViewer.getTextSettings().isAutoApplyNonConflictedChanges()) {
          if (hasNonConflictedChanges(ThreeSide.BASE)) {
            applyNonConflictedChanges(ThreeSide.BASE);
          }
        }
      };
    }

    @Override
    @CalledInAwt
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
        boolean enabled = myTextDiffProvider.getHighlightPolicy() == HighlightPolicy.BY_WORD;
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

      @CalledInAwt
      public void stop() {
        if (myProgress != null) myProgress.cancel();
        myProgress = null;
        myScheduled.clear();
        myAlarm.cancelAllRequests();
      }

      @CalledInAwt
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
        myAlarm.addRequest(this::launchRediff, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS);
      }

      @CalledInAwt
      private void launchRediff() {
        myStatusPanel.setBusy(true);
        myProgress = new EmptyProgressIndicator();

        final List<TextMergeChange> scheduled = ContainerUtil.newArrayList(myScheduled);
        myScheduled.clear();

        List<Document> documents = ThreeSide.map((side) -> getEditor(side).getDocument());
        final List<InnerChunkData> data = ContainerUtil.map(scheduled, change -> new InnerChunkData(change, documents));

        final ProgressIndicator indicator = myProgress;
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
          performRediff(scheduled, data, indicator);
        });
      }

      @CalledInBackground
      private void performRediff(@NotNull final List<TextMergeChange> scheduled,
                                 @NotNull final List<InnerChunkData> data,
                                 @NotNull final ProgressIndicator indicator) {
        final List<MergeInnerDifferences> result = new ArrayList<>(data.size());
        for (InnerChunkData chunkData : data) {
          result.add(DiffUtil.compareThreesideInner(chunkData.text, ComparisonPolicy.DEFAULT, indicator));
        }

        ApplicationManager.getApplication().invokeLater(() -> {
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
          RelativePoint point = new RelativePoint(component, new Point(component.getWidth() / 2, JBUI.scale(5)));

          String message = DiffBundle.message("merge.all.changes.processed.message.text");
          DiffUtil.showSuccessPopup(message, point, this, () -> {
            if (isDisposed()) return;
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
    public ModifierProvider getModifierProvider() {
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
    public boolean executeMergeCommand(@Nullable String commandName,
                                       boolean underBulkUpdate,
                                       @Nullable List<TextMergeChange> affected,
                                       @NotNull Runnable task) {
      myContentModified = true;

      TIntArrayList affectedIndexes = null;
      if (affected != null) {
        affectedIndexes = new TIntArrayList(affected.size());
        for (TextMergeChange change : affected) {
          affectedIndexes.add(change.getIndex());
        }
      }

      return myModel.executeMergeCommand(commandName, null, UndoConfirmationPolicy.DEFAULT, underBulkUpdate, affectedIndexes, task);
    }

    public boolean executeMergeCommand(@Nullable String commandName,
                                       @Nullable List<TextMergeChange> affected,
                                       @NotNull Runnable task) {
      return executeMergeCommand(commandName, false, affected, task);
    }

    @CalledInAwt
    public void markChangeResolved(@NotNull TextMergeChange change) {
      if (change.isResolved()) return;
      change.setResolved(Side.LEFT, true);
      change.setResolved(Side.RIGHT, true);

      onChangeResolved(change);
      myModel.invalidateHighlighters(change.getIndex());
    }

    @CalledInAwt
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

    @CalledWithWriteLock
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
      public MyMergeModel(@Nullable Project project, @NotNull Document document) {
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
            mergeChange.getDiffType() == TextDiffType.DELETED && !mergeChange.isResolved()) {
          myViewer.markChangeResolved(mergeChange);
        }

        return state;
      }
    }

    //
    // Actions
    //

    private boolean hasNonConflictedChanges(@NotNull ThreeSide side) {
      return ContainerUtil.exists(getAllChanges(), change -> canApplyNonConflictedChange(change, side));
    }

    private void applyNonConflictedChanges(@NotNull ThreeSide side) {
      executeMergeCommand("Apply Non Conflicted Changes", true, null, () -> {
        List<TextMergeChange> allChanges = ContainerUtil.newArrayList(getAllChanges());
        for (TextMergeChange change : allChanges) {
          applyNonConflictedChange(change, side);
        }
      });

      TextMergeChange firstUnresolved = ContainerUtil.find(getAllChanges(), c -> !c.isResolved());
      if (firstUnresolved != null) doScrollToChange(firstUnresolved, true);
    }

    public boolean canApplyNonConflictedChange(@NotNull TextMergeChange change, @NotNull ThreeSide side) {
      if (change.isConflict()) {
        return side == ThreeSide.BASE &&
               change.getType().canBeResolved() &&
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
      DiffContent baseDiffContent = ThreeSide.BASE.select(myMergeRequest.getContents());
      Document baseDocument = ((DocumentContent)baseDiffContent).getDocument();

      int resultStartLine = change.getStartLine();
      int resultEndLine = change.getEndLine();
      Document resultDocument = getEditor().getDocument();

      CharSequence baseContent = DiffUtil.getLinesContent(baseDocument, baseStartLine, baseEndLine);
      CharSequence resultContent = DiffUtil.getLinesContent(resultDocument, resultStartLine, resultEndLine);
      return !StringUtil.equals(baseContent, resultContent);
    }

    public void applyNonConflictedChange(@NotNull TextMergeChange change, @NotNull ThreeSide side) {
      if (!canApplyNonConflictedChange(change, side)) return;

      if (change.isConflict()) {
        List<CharSequence> texts = ThreeSide.map(it -> {
          return DiffUtil.getLinesContent(getEditor(it).getDocument(), change.getStartLine(it), change.getEndLine(it));
        });

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

        executeMergeCommand(title, selectedChanges.size() > 1, selectedChanges, () -> {
          apply(side, selectedChanges);
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

        List<TextMergeChange> affectedChanges = new ArrayList<>();
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
        ActionUtil.copyFrom(this, mySide.select("Diff.IgnoreLeftSide", "Diff.IgnoreRightSide"));
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
        ActionUtil.copyFrom(this, mySide.select("Diff.ApplyLeftSide", "Diff.ApplyRightSide"));
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
          replaceChange(changes.get(i), mySide, true);
        }
      }
    }

    private class ResolveSelectedConflictsAction extends ApplySelectedChangesActionBase {
      public ResolveSelectedConflictsAction(boolean shortcut) {
        super(shortcut);
        ActionUtil.copyFrom(this, "Diff.ResolveConflict");
      }

      @Override
      protected String getText(@NotNull ThreeSide side) {
        return "Resolve Automatically";
      }

      @Override
      protected boolean isVisible(@NotNull ThreeSide side) {
        return side == ThreeSide.BASE;
      }

      @Override
      protected boolean isEnabled(@NotNull TextMergeChange change) {
        return canApplyNonConflictedChange(change, ThreeSide.BASE);
      }

      @Override
      protected void apply(@NotNull ThreeSide side, @NotNull List<TextMergeChange> changes) {
        for (int i = changes.size() - 1; i >= 0; i--) {
          TextMergeChange change = changes.get(i);
          applyNonConflictedChange(change, ThreeSide.BASE);
        }
      }
    }

    public class ApplyNonConflictsAction extends DumbAwareAction {
      @NotNull private final ThreeSide mySide;

      public ApplyNonConflictsAction(@NotNull ThreeSide side) {
        String id = side.select("Diff.ApplyNonConflicts.Left", "Diff.ApplyNonConflicts", "Diff.ApplyNonConflicts.Right");
        ActionUtil.copyFrom(this, id);
        mySide = side;
      }

      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(hasNonConflictedChanges(mySide));
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        applyNonConflictedChanges(mySide);
      }
    }

    private class ShowDiffWithBaseAction extends DumbAwareAction {
      @NotNull private final ThreeSide mySide;

      public ShowDiffWithBaseAction(@NotNull ThreeSide side) {
        mySide = side;
        String actionId = mySide.select("Diff.CompareWithBase.Left", "Diff.CompareWithBase.Result", "Diff.CompareWithBase.Right");
        ActionUtil.copyFrom(this, actionId);
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
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

    private class MyLineStatusTracker extends LineStatusTrackerBase {
      public MyLineStatusTracker(@Nullable Project project, @NotNull Document document) {
        super(project, document);
      }

      @Override
      protected void createHighlighter(@NotNull final Range range) {
        myApplication.assertIsDispatchThread();
        if (range.getHighlighter() != null) {
          LOG.error("Multiple highlighters registered for the same Range");
          return;
        }

        int first = range.getLine1() < getLineCount(myDocument) ?
                    myDocument.getLineStartOffset(range.getLine1()) :
                    myDocument.getTextLength();
        int second = range.getLine2() < getLineCount(myDocument) ?
                     myDocument.getLineStartOffset(range.getLine2()) :
                     myDocument.getTextLength();

        MarkupModel markupModel = getEditor().getMarkupModel();

        RangeHighlighter highlighter = LineStatusMarkerRenderer.createRangeHighlighter(range, new TextRange(first, second), markupModel);
        highlighter.setLineMarkerRenderer(new MyLineStatusMarkerRenderer(range));

        range.setHighlighter(highlighter);
      }

      @Nullable
      @Override
      protected VirtualFile getVirtualFile() {
        return FileDocumentManager.getInstance().getFile(myDocument);
      }
    }

    private class MyLineStatusMarkerRenderer extends LineStatusMarkerRenderer {
      private final Range myRange;

      public MyLineStatusMarkerRenderer(Range range) {
        super(range);
        myRange = range;
      }

      @Override
      public boolean canDoAction(MouseEvent e) {
        return isInsideMarkerArea(e);
      }

      @Override
      public void doAction(Editor editor1, MouseEvent e) {
        LineStatusMarkerPopup popup = new MyLineStatusMarkerPopup(myRange);
        popup.showHint(e);
      }

      @Override
      protected int getFramingBorderSize() {
        return JBUI.scale(2);
      }
    }

    private class MyLineStatusMarkerPopup extends LineStatusMarkerPopup {
      public MyLineStatusMarkerPopup(@NotNull Range range) {
        super(myLineStatusTracker, getEditor(), range);
      }

      @Override
      public void scrollAndShow() {
        if (!myTracker.isValid()) return;
        final Document document = myTracker.getDocument();
        int line = Math.min(myRange.getType() == Range.DELETED ? myRange.getLine2() : myRange.getLine2() - 1, getLineCount(document) - 1);

        int[] startLines = new int[]{
          transferPosition(ThreeSide.BASE, ThreeSide.LEFT, new LogicalPosition(line, 0)).line,
          line,
          transferPosition(ThreeSide.BASE, ThreeSide.RIGHT, new LogicalPosition(line, 0)).line
        };

        for (ThreeSide side : ThreeSide.values()) {
          DiffUtil.moveCaret(getEditor(side), side.select(startLines));
        }

        getEditor().getScrollingModel().scrollToCaret(ScrollType.CENTER);
        showAfterScroll();
      }

      @NotNull
      @Override
      protected List<AnAction> createToolbarActions(@Nullable Point mousePosition) {
        List<AnAction> actions = new ArrayList<>();
        actions.add(new MyShowPrevChangeMarkerAction(myRange));
        actions.add(new MyShowNextChangeMarkerAction(myRange));
        actions.add(new ShowLineStatusRangeDiffAction(myTracker, myRange, myEditor));
        actions.add(new CopyLineStatusRangeAction(myTracker, myRange));
        actions.add(new ToggleByWordDiffAction(myRange, mousePosition));
        return actions;
      }
    }

    private abstract class ShowChangeMarkerAction extends DumbAwareAction {
      @Nullable private final Range myRange;

      protected ShowChangeMarkerAction(@Nullable Range range, @NotNull String actionId) {
        myRange = range;
        ActionUtil.copyFrom(this, actionId);
      }

      @Nullable
      protected abstract Range getTargetRange(@NotNull Range range);

      @Nullable
      protected abstract Range getTargetRange(int line);

      @Override
      public void update(AnActionEvent e) {
        boolean isKeyboardShortcut = myRange == null;
        boolean enabled = getTextSettings().isEnableLstGutterMarkersInMerge();
        enabled &= isKeyboardShortcut || myLineStatusTracker.isValid() && getTargetRange(myRange) != null;
        e.getPresentation().setEnabled(enabled);
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        if (!myLineStatusTracker.isValid()) return;
        int line = getEditor().getCaretModel().getLogicalPosition().line;
        Range targetRange = myRange != null ? getTargetRange(myRange) : getTargetRange(line);
        if (targetRange != null) new MyLineStatusMarkerPopup(targetRange).scrollAndShow();
      }
    }

    private class MyShowPrevChangeMarkerAction extends ShowChangeMarkerAction {
      public MyShowPrevChangeMarkerAction(@Nullable Range range) {
        super(range, "VcsShowPrevChangeMarker");
      }

      @Nullable
      @Override
      protected Range getTargetRange(@NotNull Range range) {
        return myLineStatusTracker.getPrevRange(range);
      }

      @Nullable
      @Override
      protected Range getTargetRange(int line) {
        return myLineStatusTracker.getPrevRange(line);
      }
    }

    private class MyShowNextChangeMarkerAction extends ShowChangeMarkerAction {
      public MyShowNextChangeMarkerAction(@Nullable Range range) {
        super(range, "VcsShowNextChangeMarker");
      }

      @Nullable
      @Override
      protected Range getTargetRange(@NotNull Range range) {
        return myLineStatusTracker.getNextRange(range);
      }

      @Nullable
      @Override
      protected Range getTargetRange(int line) {
        return myLineStatusTracker.getNextRange(line);
      }
    }

    private class ToggleByWordDiffAction extends LineStatusMarkerPopup.ToggleByWordDiffActionBase {
      @NotNull private final Range myRange;
      @Nullable private final Point myMousePosition;

      public ToggleByWordDiffAction(@NotNull Range range, @Nullable Point mousePosition) {
        myRange = range;
        myMousePosition = mousePosition;
      }

      @Override
      protected void reshowPopup() {
        new MyLineStatusMarkerPopup(myRange).showHintAt(myMousePosition);
      }
    }
  }

  private static class InnerChunkData {
    @NotNull public final List<CharSequence> text;

    public InnerChunkData(@NotNull TextMergeChange change, @NotNull List<Document> documents) {
      text = getChunks(change, documents);
    }

    @NotNull
    private static List<CharSequence> getChunks(@NotNull TextMergeChange change,
                                                @NotNull List<Document> documents) {
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
