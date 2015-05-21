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
import com.intellij.diff.util.DiffDividerDrawUtil;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Side;
import com.intellij.diff.util.ThreeSide;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.BooleanGetter;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

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

    @Override
    public void dispose() {
      if (!isConflictResolved()) myMergeRequest.applyResult(MergeResult.CANCEL);
      Disposer.dispose(myViewer);
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
      // all changes - both applied and unapplied ones
      @NotNull private final List<TextMergeChange> myAllMergeChanges = new ArrayList<TextMergeChange>();
      private boolean myInitialRediffDone;

      private final List<TextMergeChange> myChangesToUpdate = new ArrayList<TextMergeChange>();

      public MyThreesideViewer(@NotNull DiffContext context, @NotNull ContentDiffRequest request) {
        super(context, request);

        myPanel.setBottomPanel(createBottomPanel());
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
      protected JComponent createBottomPanel() {
        return MergeUtil.createAcceptActionsPanel(new MergeUtil.AcceptActionProcessor() {
          @Override
          public boolean isEnabled(@NotNull MergeResult result) {
            return true;
          }

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
            myMergeRequest.applyResult(result);
            myMergeContext.closeDialog();
          }
        });
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
            myChangesToUpdate.add(change); // document state is not updated yet - can't reinstall range here
          }
        }
      }

      @Override
      protected void onDocumentChange(@NotNull DocumentEvent e) {
        super.onDocumentChange(e);

        for (TextMergeChange change : myChangesToUpdate) {
          change.reinstallHighlighter();
        }
      }

      //
      // Getters
      //

      @NotNull
      protected List<TextMergeChange> getAllChanges() {
        return myAllMergeChanges;
      }

      @NotNull
      protected List<TextMergeChange> getChanges() {
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
            if (!mergeChange.getType().isChange(mySide)) continue;
            Color color = mergeChange.getDiffType().getColor(getEditor(ThreeSide.BASE));
            if (!handler.process(mergeChange.getStartLine(left), mergeChange.getEndLine(left),
                                 mergeChange.getStartLine(right), mergeChange.getEndLine(right),
                                 color)) {
              return;
            }
          }
        }
      }
    }
  }
}
