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
import com.intellij.diff.comparison.ComparisonManager;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.comparison.DiffTooBigException;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.fragments.MergeLineFragment;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.util.DiffNotifications;
import com.intellij.diff.tools.util.base.HighlightPolicy;
import com.intellij.diff.tools.util.base.IgnorePolicy;
import com.intellij.diff.tools.util.base.TextDiffViewerUtil;
import com.intellij.diff.tools.util.side.ThreesideTextDiffViewer;
import com.intellij.diff.util.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class SimpleThreesideDiffViewer extends ThreesideTextDiffViewerEx {
  public static final Logger LOG = Logger.getInstance(SimpleThreesideDiffViewer.class);

  @NotNull private final List<SimpleThreesideDiffChange> myDiffChanges = new ArrayList<>();
  @NotNull private final List<SimpleThreesideDiffChange> myInvalidDiffChanges = new ArrayList<>();

  public SimpleThreesideDiffViewer(@NotNull DiffContext context, @NotNull DiffRequest request) {
    super(context, (ContentDiffRequest)request);
  }

  @NotNull
  @Override
  protected List<AnAction> createToolbarActions() {
    List<AnAction> group = new ArrayList<>();

    group.add(new MyIgnorePolicySettingAction());
    group.add(new MyHighlightPolicySettingAction());
    group.add(new MyToggleExpandByDefaultAction());
    group.add(new MyToggleAutoScrollAction());
    group.add(new MyEditorReadOnlyLockAction());
    group.add(myEditorSettingsAction);

    group.add(Separator.getInstance());
    group.add(new TextShowPartialDiffAction(PartialDiffMode.LEFT_BASE));
    group.add(new TextShowPartialDiffAction(PartialDiffMode.BASE_RIGHT));
    group.add(new TextShowPartialDiffAction(PartialDiffMode.LEFT_RIGHT));

    group.add(Separator.getInstance());
    group.addAll(super.createToolbarActions());

    return group;
  }

  @NotNull
  @Override
  protected List<AnAction> createPopupActions() {
    List<AnAction> group = new ArrayList<>();

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

      final List<DiffContent> contents = myRequest.getContents();
      final List<Document> documents = ContainerUtil.map(contents, content -> ((DocumentContent)content).getDocument());

      final List<CharSequence> sequences = ReadAction.compute(() -> {
        indicator.checkCanceled();
        return ContainerUtil.map(documents, Document::getImmutableCharSequence);
      });

      final ComparisonPolicy comparisonPolicy = getIgnorePolicy().getComparisonPolicy();

      ComparisonManager manager = ComparisonManager.getInstance();
      List<MergeLineFragment> lineFragments = manager.compareLines(sequences.get(0), sequences.get(1), sequences.get(2),
                                                                   comparisonPolicy, indicator);

      List<MergeConflictType> conflictTypes = ReadAction.compute(() -> {
        indicator.checkCanceled();
        return ContainerUtil.map(lineFragments, (fragment) -> DiffUtil.getLineMergeType(fragment, documents, comparisonPolicy));
      });

      List<MergeInnerDifferences> innerFragments = null;
      if (getHighlightPolicy().isFineFragments()) {
        innerFragments = new ArrayList<>(lineFragments.size());

        for (int i = 0; i < lineFragments.size(); i++) {
          final MergeLineFragment fragment = lineFragments.get(i);
          final MergeConflictType conflictType = conflictTypes.get(i);

          List<CharSequence> chunks = ReadAction.compute(() -> {
            indicator.checkCanceled();
            return ThreeSide.map(side -> {
              if (!conflictType.isChange(side)) return null;
              return getChunkContent(fragment, documents, side);
            });
          });

          innerFragments.add(DiffUtil.compareThreesideInner(chunks, comparisonPolicy, indicator));
        }
      }

      return apply(lineFragments, conflictTypes, innerFragments);
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
  private static CharSequence getChunkContent(@NotNull MergeLineFragment fragment,
                                              @NotNull List<Document> documents,
                                              @NotNull ThreeSide side) {
    int startLine = fragment.getStartLine(side);
    int endLine = fragment.getEndLine(side);
    return startLine != endLine ? DiffUtil.getLinesContent(side.select(documents), startLine, endLine) : null;
  }

  @NotNull
  private Runnable apply(@NotNull final List<MergeLineFragment> fragments,
                         @NotNull final List<MergeConflictType> conflictTypes,
                         @Nullable final List<MergeInnerDifferences> innerDifferences) {
    return () -> {
      myFoldingModel.updateContext(myRequest, getFoldingModelSettings());
      clearDiffPresentation();

      resetChangeCounters();
      for (int i = 0; i < fragments.size(); i++) {
        MergeLineFragment fragment = fragments.get(i);
        MergeConflictType conflictType = conflictTypes.get(i);
        MergeInnerDifferences innerFragments = innerDifferences != null ? innerDifferences.get(i) : null;

        SimpleThreesideDiffChange change = new SimpleThreesideDiffChange(fragment, conflictType, innerFragments, this);
        myDiffChanges.add(change);
        onChangeAdded(change);
      }

      myFoldingModel.install(fragments, myRequest, getFoldingModelSettings());

      myInitialScrollHelper.onRediff();

      myContentPanel.repaintDividers();
      myStatusPanel.update();
    };
  }

  @Override
  @CalledInAwt
  protected void destroyChangedBlocks() {
    super.destroyChangedBlocks();
    for (SimpleThreesideDiffChange change : myDiffChanges) {
      change.destroy();
    }
    myDiffChanges.clear();

    for (SimpleThreesideDiffChange change : myInvalidDiffChanges) {
      change.destroy();
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

    List<Document> documents = ContainerUtil.map(getEditors(), Editor::getDocument);
    ThreeSide side = ThreeSide.fromValue(documents, e.getDocument());
    if (side == null) {
      LOG.warn("Unknown document changed");
      return;
    }

    LineRange lineRange = DiffUtil.getAffectedLineRange(e);
    int shift = DiffUtil.countLinesShift(e);

    List<SimpleThreesideDiffChange> invalid = new ArrayList<>();
    for (SimpleThreesideDiffChange change : myDiffChanges) {
      if (change.processChange(lineRange.start, lineRange.end, shift, side)) {
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
  @Override
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
}
