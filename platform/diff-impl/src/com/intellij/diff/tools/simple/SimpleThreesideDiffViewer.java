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
import com.intellij.diff.comparison.DiffTooBigException;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.util.DiffNotifications;
import com.intellij.diff.tools.util.FoldingModelSupport;
import com.intellij.diff.tools.util.base.TextDiffViewerUtil;
import com.intellij.diff.tools.util.side.ThreesideTextDiffViewer;
import com.intellij.diff.tools.util.text.FineMergeLineFragment;
import com.intellij.diff.tools.util.text.MergeInnerDifferences;
import com.intellij.diff.tools.util.text.SimpleThreesideTextDiffProvider;
import com.intellij.diff.util.*;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.CalledWithWriteLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public class SimpleThreesideDiffViewer extends ThreesideTextDiffViewerEx {
  @NotNull private final SimpleThreesideTextDiffProvider myTextDiffProvider;

  @NotNull private final List<SimpleThreesideDiffChange> myDiffChanges = new ArrayList<>();
  @NotNull private final List<SimpleThreesideDiffChange> myInvalidDiffChanges = new ArrayList<>();

  public SimpleThreesideDiffViewer(@NotNull DiffContext context, @NotNull DiffRequest request) {
    super(context, (ContentDiffRequest)request);

    myTextDiffProvider = new SimpleThreesideTextDiffProvider(getTextSettings(), this::rediff, this);
  }

  @NotNull
  @Override
  protected List<AnAction> createToolbarActions() {
    List<AnAction> group = new ArrayList<>();

    DefaultActionGroup diffGroup = new DefaultActionGroup("Compare Contents", true);
    diffGroup.getTemplatePresentation().setIcon(AllIcons.Actions.Diff);
    diffGroup.add(Separator.create("Compare Contents"));
    diffGroup.add(new TextShowPartialDiffAction(PartialDiffMode.MIDDLE_LEFT, false));
    diffGroup.add(new TextShowPartialDiffAction(PartialDiffMode.MIDDLE_RIGHT, false));
    diffGroup.add(new TextShowPartialDiffAction(PartialDiffMode.LEFT_RIGHT, false));
    group.add(diffGroup);
    group.add(Separator.getInstance());

    group.addAll(myTextDiffProvider.getToolbarActions());

    group.add(new MyToggleExpandByDefaultAction());
    group.add(new MyToggleAutoScrollAction());
    group.add(new MyEditorReadOnlyLockAction());
    group.add(myEditorSettingsAction);

    group.add(Separator.getInstance());
    group.addAll(super.createToolbarActions());

    return group;
  }

  @NotNull
  @Override
  protected List<AnAction> createPopupActions() {
    List<AnAction> group = new ArrayList<>(myTextDiffProvider.getPopupActions());
    group.add(new MyToggleAutoScrollAction());
    group.add(new MyToggleExpandByDefaultAction());

    group.add(Separator.getInstance());
    group.addAll(super.createPopupActions());

    return group;
  }

  @NotNull
  @Override
  protected List<AnAction> createEditorPopupActions() {
    List<AnAction> group = new ArrayList<>();

    group.add(new ReplaceSelectedChangesAction(ThreeSide.LEFT, ThreeSide.BASE));
    group.add(new ReplaceSelectedChangesAction(ThreeSide.RIGHT, ThreeSide.BASE));
    group.add(new ReplaceSelectedChangesAction(ThreeSide.BASE, ThreeSide.LEFT));
    group.add(new ReplaceSelectedChangesAction(ThreeSide.BASE, ThreeSide.RIGHT));

    group.add(Separator.getInstance());
    group.addAll(super.createEditorPopupActions());

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

      List<CharSequence> sequences = ContainerUtil.map(getContents(), content -> content.getDocument().getImmutableCharSequence());

      List<FineMergeLineFragment> lineFragments = myTextDiffProvider.compare(sequences.get(0), sequences.get(1), sequences.get(2),
                                                                             indicator);
      FoldingModelSupport.Data foldingState = myFoldingModel.createState(lineFragments, getFoldingModelSettings());

      return apply(lineFragments, foldingState);
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

  @NotNull
  private static MergeConflictType invertConflictType(@NotNull MergeConflictType oldConflictType) {
    TextDiffType oldDiffType = oldConflictType.getDiffType();

    if (oldDiffType != TextDiffType.INSERTED && oldDiffType != TextDiffType.DELETED) {
      return oldConflictType;
    }

    return new MergeConflictType(oldDiffType == TextDiffType.DELETED ? TextDiffType.INSERTED : TextDiffType.DELETED,
                                 oldConflictType.isChange(Side.LEFT), oldConflictType.isChange(Side.RIGHT),
                                 oldConflictType.canBeResolved());
  }

  @NotNull
  private MergeConflictType convertConflictType(@NotNull FineMergeLineFragment fragment) {
    MergeConflictType conflictType = fragment.getConflictType();
    if (DiffUtil.getUserData(myRequest, myContext, DiffUserDataKeys.THREESIDE_DIFF_WITH_RESULT) == Boolean.TRUE) {
      conflictType = invertConflictType(conflictType);
    }
    return conflictType;
  }

  @NotNull
  private Runnable apply(@NotNull final List<FineMergeLineFragment> fragments, @Nullable FoldingModelSupport.Data foldingState) {
    return () -> {
      myFoldingModel.updateContext(myRequest, getFoldingModelSettings());
      clearDiffPresentation();

      resetChangeCounters();
      for (FineMergeLineFragment fragment : fragments) {
        MergeConflictType conflictType = convertConflictType(fragment);
        MergeInnerDifferences innerFragments = fragment.getInnerFragments();

        SimpleThreesideDiffChange change = new SimpleThreesideDiffChange(fragment, conflictType, innerFragments, this);
        myDiffChanges.add(change);
        onChangeAdded(change);
      }

      myFoldingModel.install(foldingState, myRequest, getFoldingModelSettings());

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

      for (SimpleThreesideDiffChange change : invalid) {
        change.markInvalid();
      }
    }
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

  protected boolean isEditable(@NotNull ThreeSide side) {
    return DiffUtil.isEditable(getEditor(side));
  }

  protected boolean isSomeChangeSelected(@NotNull ThreeSide side) {
    if (getChanges().isEmpty()) return false;

    EditorEx editor = getEditor(side);
    return DiffUtil.isSomeRangeSelected(editor, lines ->
      ContainerUtil.exists(getChanges(), change -> isChangeSelected(change, lines, side)));
  }

  @NotNull
  @CalledInAwt
  private List<SimpleThreesideDiffChange> getSelectedChanges(@NotNull ThreeSide side) {
    EditorEx editor = getEditor(side);
    BitSet lines = DiffUtil.getSelectedLines(editor);
    return ContainerUtil.filter(getChanges(), change -> isChangeSelected(change, lines, side));
  }

  //
  // Modification operations
  //

  @CalledWithWriteLock
  public void replaceChange(@NotNull SimpleThreesideDiffChange change, @NotNull ThreeSide sourceSide, @NotNull ThreeSide outputSide) {
    if (!change.isValid()) return;

    DiffUtil.applyModification(getEditor(outputSide).getDocument(), change.getStartLine(outputSide), change.getEndLine(outputSide),
                               getEditor(sourceSide).getDocument(), change.getStartLine(sourceSide), change.getEndLine(sourceSide));

    myDiffChanges.remove(change);
    change.markInvalid();
    change.destroy();
  }

  //
  // Actions
  //

  protected class MyEditorReadOnlyLockAction extends TextDiffViewerUtil.EditorReadOnlyLockAction {
    public MyEditorReadOnlyLockAction() {
      super(getContext(), getEditableEditors());
    }
  }

  static String getReplaceActionId(@NotNull ThreeSide master, @NotNull ThreeSide modifiedSide) {
    if (master == ThreeSide.LEFT && modifiedSide == ThreeSide.BASE) return "Diff.ApplyLeftSide";
    if (master == ThreeSide.RIGHT && modifiedSide == ThreeSide.BASE) return "Diff.ApplyRightSide";
    return null;
  }

  private class ReplaceSelectedChangesAction extends SelectedChangesActionBase {
    @NotNull protected final ThreeSide mySourceSide;
    @NotNull protected final ThreeSide myModifiedSide;

    ReplaceSelectedChangesAction(@NotNull ThreeSide sourceSide, @NotNull ThreeSide modifiedSide) {
      mySourceSide = sourceSide;
      myModifiedSide = modifiedSide;

      String keymapActionId = getReplaceActionId(sourceSide, modifiedSide);
      if (keymapActionId != null) copyShortcutFrom(ActionManager.getInstance().getAction(keymapActionId));
    }

    @Override
    protected boolean isVisible(@NotNull ThreeSide side) {
      if (!isEditable(myModifiedSide)) return false;
      return !isBothEditable() || side == mySourceSide;
    }

    protected boolean isBothEditable() {
      return isEditable(mySourceSide) && isEditable(myModifiedSide);
    }

    @NotNull
    @Override
    protected String getText(@NotNull ThreeSide side) {
      return "Accept";
    }

    @Nullable
    @Override
    protected Icon getIcon(@NotNull ThreeSide side) {
      Side arrowDirection = Side.fromLeft(mySourceSide == ThreeSide.LEFT ||
                                          myModifiedSide == ThreeSide.RIGHT);
      return DiffUtil.getArrowIcon(arrowDirection);
    }

    @Override
    protected void doPerform(@NotNull AnActionEvent e, @NotNull ThreeSide side, @NotNull List<SimpleThreesideDiffChange> changes) {
      if (!isEditable(myModifiedSide)) return;

      String title = "Accept selected changes";
      DiffUtil.executeWriteCommand(getEditor(myModifiedSide).getDocument(), e.getProject(), title, () -> {
        for (SimpleThreesideDiffChange change : changes) {
          replaceChange(change, mySourceSide, myModifiedSide);
        }
      });
    }
  }

  protected abstract class SelectedChangesActionBase extends DumbAwareAction {
    @Override
    public void update(@NotNull AnActionEvent e) {
      if (DiffUtil.isFromShortcut(e)) {
        // consume shortcut even if there are nothing to do - avoid calling some other action
        e.getPresentation().setEnabledAndVisible(true);
        return;
      }

      Editor editor = e.getData(CommonDataKeys.EDITOR);
      ThreeSide side = ThreeSide.fromValue(getEditors(), editor);
      if (side == null || !isVisible(side)) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }

      e.getPresentation().setText(getText(side));
      e.getPresentation().setIcon(getIcon(side));

      e.getPresentation().setVisible(true);
      e.getPresentation().setEnabled(isSomeChangeSelected(side));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Editor editor = e.getData(CommonDataKeys.EDITOR);
      final ThreeSide side = ThreeSide.fromValue(getEditors(), editor);
      if (side == null) return;

      final List<SimpleThreesideDiffChange> selectedChanges = getSelectedChanges(side);
      if (selectedChanges.isEmpty()) return;

      doPerform(e, side, ContainerUtil.reverse(selectedChanges));
    }

    protected abstract boolean isVisible(@NotNull ThreeSide side);

    @NotNull
    protected abstract String getText(@NotNull ThreeSide side);

    @Nullable
    protected abstract Icon getIcon(@NotNull ThreeSide side);

    @CalledWithWriteLock
    protected abstract void doPerform(@NotNull AnActionEvent e, @NotNull ThreeSide side, @NotNull List<SimpleThreesideDiffChange> changes);
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
