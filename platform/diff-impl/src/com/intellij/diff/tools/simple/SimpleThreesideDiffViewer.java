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
import com.intellij.diff.comparison.ByLine;
import com.intellij.diff.comparison.ComparisonMergeUtil;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.comparison.DiffTooBigException;
import com.intellij.diff.comparison.iterables.FairDiffIterable;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.fragments.MergeLineFragment;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.util.DiffNotifications;
import com.intellij.diff.tools.util.base.IgnorePolicy;
import com.intellij.diff.tools.util.base.TextDiffViewerUtil;
import com.intellij.diff.tools.util.side.ThreesideTextDiffViewer;
import com.intellij.diff.util.DiffDividerDrawUtil;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Side;
import com.intellij.diff.util.ThreeSide;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Computable;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class SimpleThreesideDiffViewer extends ThreesideTextDiffViewerEx {
  public static final Logger LOG = Logger.getInstance(SimpleThreesideDiffViewer.class);

  @NotNull private final List<SimpleThreesideDiffChange> myDiffChanges = new ArrayList<SimpleThreesideDiffChange>();

  public SimpleThreesideDiffViewer(@NotNull DiffContext context, @NotNull DiffRequest request) {
    super(context, (ContentDiffRequest)request);
  }

  @NotNull
  @Override
  protected List<AnAction> createToolbarActions() {
    List<AnAction> group = new ArrayList<AnAction>();

    group.add(new MyIgnorePolicySettingAction());
    //group.add(new MyHighlightPolicySettingAction()); // TODO
    group.add(new MyToggleExpandByDefaultAction());
    group.add(new MyToggleAutoScrollAction());
    group.add(new MyEditorReadOnlyLockAction());
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
    group.add(new MyIgnorePolicySettingAction().getPopupGroup());
    //group.add(Separator.getInstance());
    //group.add(new MyHighlightPolicySettingAction().getPopupGroup());
    group.add(Separator.getInstance());
    group.add(new MyToggleAutoScrollAction());
    group.add(new MyToggleExpandByDefaultAction());

    return group;
  }

  //
  // Diff
  //

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

      final List<DiffContent> contents = myRequest.getContents();
      List<CharSequence> sequences = ApplicationManager.getApplication().runReadAction(new Computable<List<CharSequence>>() {
        @Override
        public List<CharSequence> compute() {
          return ContainerUtil.map(contents, new Function<DiffContent, CharSequence>() {
            @Override
            public CharSequence fun(DiffContent diffContent) {
              return ((DocumentContent)diffContent).getDocument().getImmutableCharSequence();
            }
          });
        }
      });

      ComparisonPolicy comparisonPolicy = getIgnorePolicy().getComparisonPolicy();
      FairDiffIterable fragments1 = ByLine.compareTwoStepFair(sequences.get(1), sequences.get(0), comparisonPolicy, indicator);
      FairDiffIterable fragments2 = ByLine.compareTwoStepFair(sequences.get(1), sequences.get(2), comparisonPolicy, indicator);
      List<MergeLineFragment> mergeFragments = ComparisonMergeUtil.buildFair(fragments1, fragments2, indicator);

      return apply(mergeFragments, comparisonPolicy);
    }
    catch (DiffTooBigException e) {
      return applyNotification(DiffNotifications.DIFF_TOO_BIG);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.error(e);
      return applyNotification(DiffNotifications.ERROR);
    }
  }

  @NotNull
  private Runnable apply(@NotNull final List<MergeLineFragment> fragments,
                         @NotNull final ComparisonPolicy comparisonPolicy) {
    return new Runnable() {
      @Override
      public void run() {
        myFoldingModel.updateContext(myRequest, getFoldingModelSettings());
        clearDiffPresentation();

        resetChangeCounters();
        for (MergeLineFragment fragment : fragments) {
          SimpleThreesideDiffChange change = new SimpleThreesideDiffChange(fragment, getEditors(), comparisonPolicy);
          myDiffChanges.add(change);
          onChangeAdded(change);
        }

        myFoldingModel.install(fragments, myRequest, getFoldingModelSettings());

        myInitialScrollHelper.onRediff();

        myContentPanel.repaintDividers();
        myStatusPanel.update();
      }
    };
  }

  protected void destroyChangedBlocks() {
    super.destroyChangedBlocks();
    for (SimpleThreesideDiffChange change : myDiffChanges) {
      change.destroyHighlighter();
    }
    myDiffChanges.clear();
  }

  //
  // Impl
  //

  @Override
  @CalledInAwt
  protected void onBeforeDocumentChange(@NotNull DocumentEvent e) {
    super.onBeforeDocumentChange(e);
    if (myDiffChanges.isEmpty()) return;

    ThreeSide side = null;
    if (e.getDocument() == getEditor(ThreeSide.LEFT).getDocument()) side = ThreeSide.LEFT;
    if (e.getDocument() == getEditor(ThreeSide.RIGHT).getDocument()) side = ThreeSide.RIGHT;
    if (e.getDocument() == getEditor(ThreeSide.BASE).getDocument()) side = ThreeSide.BASE;
    if (side == null) {
      LOG.warn("Unknown document changed");
      return;
    }

    int line1 = e.getDocument().getLineNumber(e.getOffset());
    int line2 = e.getDocument().getLineNumber(e.getOffset() + e.getOldLength()) + 1;
    int shift = DiffUtil.countLinesShift(e);

    List<SimpleThreesideDiffChange> invalid = new ArrayList<SimpleThreesideDiffChange>();
    for (SimpleThreesideDiffChange change : myDiffChanges) {
      if (change.processChange(line1, line2, shift, side)) {
        invalid.add(change);
        change.destroyHighlighter();
      }
    }
    myDiffChanges.removeAll(invalid);
  }

  @NotNull
  private IgnorePolicy getIgnorePolicy() {
    IgnorePolicy policy = getTextSettings().getIgnorePolicy();
    if (policy == IgnorePolicy.IGNORE_WHITESPACES_CHUNKS) return IgnorePolicy.IGNORE_WHITESPACES;
    return policy;
  }

  //
  // Getters
  //

  @NotNull
  protected List<SimpleThreesideDiffChange> getChanges() {
    return myDiffChanges;
  }

  @NotNull
  @Override
  protected DiffDividerDrawUtil.DividerPaintable getDividerPaintable(@NotNull Side side) {
    return new MyDividerPaintable(side);
  }

  //
  // Misc
  //

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static boolean canShowRequest(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return ThreesideTextDiffViewer.canShowRequest(context, request);
  }

  //
  // Actions
  //

  private class MyIgnorePolicySettingAction extends TextDiffViewerUtil.IgnorePolicySettingAction {
    public MyIgnorePolicySettingAction() {
      super(getTextSettings());
    }

    @NotNull
    @Override
    protected IgnorePolicy getCurrentSetting() {
      return getIgnorePolicy();
    }

    @NotNull
    @Override
    protected List<IgnorePolicy> getAvailableSettings() {
      ArrayList<IgnorePolicy> settings = ContainerUtil.newArrayList(IgnorePolicy.values());
      settings.remove(IgnorePolicy.IGNORE_WHITESPACES_CHUNKS);
      return settings;
    }

    @Override
    protected void onSettingsChanged() {
      rediff();
    }
  }

  protected class MyEditorReadOnlyLockAction extends TextDiffViewerUtil.EditorReadOnlyLockAction {
    public MyEditorReadOnlyLockAction() {
      super(getContext(), getEditableEditors());
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

      for (SimpleThreesideDiffChange diffChange : myDiffChanges) {
        if (!diffChange.getType().isChange(mySide)) continue;
        if (!handler.process(diffChange.getStartLine(left), diffChange.getEndLine(left),
                             diffChange.getStartLine(right), diffChange.getEndLine(right),
                             diffChange.getDiffType().getColor(getEditor(ThreeSide.BASE)))) {
          return;
        }
      }
    }
  }
}
