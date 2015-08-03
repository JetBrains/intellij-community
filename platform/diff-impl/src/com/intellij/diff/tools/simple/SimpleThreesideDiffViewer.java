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
import com.intellij.diff.comparison.*;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.fragments.DiffFragment;
import com.intellij.diff.fragments.MergeLineFragment;
import com.intellij.diff.fragments.MergeLineFragmentImpl;
import com.intellij.diff.fragments.MergeWordFragment;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.util.DiffNotifications;
import com.intellij.diff.tools.util.base.HighlightPolicy;
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
import com.intellij.openapi.editor.Document;
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
  @NotNull private final List<SimpleThreesideDiffChange> myInvalidDiffChanges = new ArrayList<SimpleThreesideDiffChange>();

  public SimpleThreesideDiffViewer(@NotNull DiffContext context, @NotNull DiffRequest request) {
    super(context, (ContentDiffRequest)request);
  }

  @NotNull
  @Override
  protected List<AnAction> createToolbarActions() {
    List<AnAction> group = new ArrayList<AnAction>();

    group.add(new MyIgnorePolicySettingAction());
    group.add(new MyHighlightPolicySettingAction());
    group.add(new MyToggleExpandByDefaultAction());
    group.add(new MyToggleAutoScrollAction());
    group.add(new MyEditorReadOnlyLockAction());
    group.add(myEditorSettingsAction);

    group.add(Separator.getInstance());
    group.add(new ShowLeftBasePartialDiffAction());
    group.add(new ShowBaseRightPartialDiffAction());
    group.add(new ShowLeftRightPartialDiffAction());

    group.add(Separator.getInstance());
    group.addAll(super.createToolbarActions());

    return group;
  }

  @NotNull
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

    group.add(Separator.getInstance());
    group.addAll(super.createPopupActions());

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

      List<DiffContent> contents = myRequest.getContents();
      final Document[] documents = new Document[3];
      documents[0] = ((DocumentContent)contents.get(0)).getDocument();
      documents[1] = ((DocumentContent)contents.get(1)).getDocument();
      documents[2] = ((DocumentContent)contents.get(2)).getDocument();

      CharSequence[] sequences = ApplicationManager.getApplication().runReadAction(new Computable<CharSequence[]>() {
        @Override
        public CharSequence[] compute() {
          CharSequence[] sequences = new CharSequence[3];
          sequences[0] = documents[0].getImmutableCharSequence();
          sequences[1] = documents[1].getImmutableCharSequence();
          sequences[2] = documents[2].getImmutableCharSequence();
          return sequences;
        }
      });

      ComparisonPolicy comparisonPolicy = getIgnorePolicy().getComparisonPolicy();
      List<MergeLineFragment> lineFragments = ByLine.compareTwoStep(sequences[0], sequences[1], sequences[2],
                                                                    comparisonPolicy, indicator);

      if (getHighlightPolicy().isFineFragments()) {
        List<MergeLineFragment> fineLineFragments = new ArrayList<MergeLineFragment>(lineFragments.size());

        for (final MergeLineFragment fragment : lineFragments) {
          CharSequence[] chunks = ApplicationManager.getApplication().runReadAction(new Computable<CharSequence[]>() {
            @Override
            public CharSequence[] compute() {
              indicator.checkCanceled();
              CharSequence[] chunks = new CharSequence[3];
              chunks[0] = getChunkContent(fragment, documents, ThreeSide.LEFT);
              chunks[1] = getChunkContent(fragment, documents, ThreeSide.BASE);
              chunks[2] = getChunkContent(fragment, documents, ThreeSide.RIGHT);
              return chunks;
            }
          });

          List<MergeWordFragment> wordFragments = getWordFragments(chunks, comparisonPolicy, indicator);
          fineLineFragments.add(new MergeLineFragmentImpl(fragment, wordFragments));
        }

        lineFragments = fineLineFragments;
      }

      return apply(lineFragments, comparisonPolicy);
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

  @Nullable
  private static CharSequence getChunkContent(@NotNull MergeLineFragment fragment, @NotNull Document[] documents, @NotNull ThreeSide side) {
    int startLine = fragment.getStartLine(side);
    int endLine = fragment.getEndLine(side);
    return startLine != endLine ? DiffUtil.getLinesContent(side.select(documents), startLine, endLine) : null;
  }

  @Nullable
  private static List<MergeWordFragment> getWordFragments(@NotNull CharSequence[] chunks,
                                                          @NotNull ComparisonPolicy comparisonPolicy,
                                                          @NotNull ProgressIndicator indicator) {
    assert chunks[0] != null || chunks[1] != null || chunks[2] != null; // ---
    if (chunks[0] == null && chunks[1] == null ||
        chunks[0] == null && chunks[2] == null ||
        chunks[1] == null && chunks[2] == null) { // =--, -=-, --=
      return null;
    }

    if (chunks[0] != null && chunks[1] != null && chunks[2] != null) { // ===
      return ByWord.compare(chunks[0], chunks[1], chunks[2], comparisonPolicy, indicator);
    }

    // ==-, =-=, -==
    final ThreeSide side1 = chunks[0] != null ? ThreeSide.LEFT : ThreeSide.BASE;
    final ThreeSide side2 = chunks[2] != null ? ThreeSide.RIGHT : ThreeSide.BASE;
    CharSequence chunk1 = side1.select(chunks);
    CharSequence chunk2 = side2.select(chunks);

    if (chunks[1] != null && ComparisonManager.getInstance().isEquals(chunk1, chunk2, comparisonPolicy)) {
      return null; // unmodified - deleted
    }

    List<DiffFragment> wordConflicts = ByWord.compare(chunk1, chunk2, comparisonPolicy, indicator);

    return ContainerUtil.map(wordConflicts, new Function<DiffFragment, MergeWordFragment>() {
      @Override
      public MergeWordFragment fun(DiffFragment fragment) {
        return new MyWordFragment(side1, side2, fragment);
      }
    });
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

    for (SimpleThreesideDiffChange change : myInvalidDiffChanges) {
      change.destroyHighlighter();
    }
    myInvalidDiffChanges.clear();
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
      }
    }

    if (!invalid.isEmpty()) {
      myDiffChanges.removeAll(invalid);
      myInvalidDiffChanges.addAll(invalid);
    }
  }

  @NotNull
  private IgnorePolicy getIgnorePolicy() {
    IgnorePolicy policy = getTextSettings().getIgnorePolicy();
    if (policy == IgnorePolicy.IGNORE_WHITESPACES_CHUNKS) return IgnorePolicy.IGNORE_WHITESPACES;
    return policy;
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
  public List<SimpleThreesideDiffChange> getChanges() {
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
        if (!diffChange.isChange(mySide)) continue;
        if (!handler.process(diffChange.getStartLine(left), diffChange.getEndLine(left),
                             diffChange.getStartLine(right), diffChange.getEndLine(right),
                             diffChange.getDiffType().getColor(getEditor(ThreeSide.BASE)))) {
          return;
        }
      }
    }
  }

  private static class MyWordFragment implements MergeWordFragment {
    @NotNull private final ThreeSide mySide1;
    @NotNull private final ThreeSide mySide2;
    @NotNull private final DiffFragment myFragment;

    public MyWordFragment(@NotNull ThreeSide side1,
                          @NotNull ThreeSide side2,
                          @NotNull DiffFragment fragment) {
      assert side1 != side2;
      mySide1 = side1;
      mySide2 = side2;
      myFragment = fragment;
    }

    @Override
    public int getStartOffset(@NotNull ThreeSide side) {
      if (side == mySide1) return myFragment.getStartOffset1();
      if (side == mySide2) return myFragment.getStartOffset2();
      return 0;
    }

    @Override
    public int getEndOffset(@NotNull ThreeSide side) {
      if (side == mySide1) return myFragment.getEndOffset1();
      if (side == mySide2) return myFragment.getEndOffset2();
      return 0;
    }
  }
}
