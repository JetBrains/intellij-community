// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge;import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Side;
import com.intellij.diff.util.ThreeSide;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.DumbAware;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.BitSet;
import java.util.List;

class MergeThreesideViewerActions {
  private static abstract class ApplySelectedChangesActionBase extends AnAction implements DumbAware {
    protected @NotNull MergeThreesideViewer myViewer;

    protected ApplySelectedChangesActionBase(@NotNull MergeThreesideViewer viewer) { myViewer = viewer; }

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

      ThreeSide side = myViewer.getEditorSide(editor);
      if (side == null) {
        presentation.setEnabledAndVisible(false);
        return;
      }

      if (!isVisible(side)) {
        presentation.setEnabledAndVisible(false);
        return;
      }

      presentation.setText(getText(side));

      presentation.setEnabledAndVisible(isSomeChangeSelected(side) && !myViewer.isExternalOperationInProgress());
    }

    @Override
    public void actionPerformed(final @NotNull AnActionEvent e) {
      Editor editor = e.getData(CommonDataKeys.EDITOR);
      final ThreeSide side = myViewer.getEditorSide(editor);
      if (editor == null || side == null) return;

      final List<TextMergeChange> selectedChanges = getSelectedChanges(side);
      if (selectedChanges.isEmpty()) return;

      String title = DiffBundle.message("message.do.in.merge.command", e.getPresentation().getText());
      myViewer.executeMergeCommand(title, selectedChanges.size() > 1, selectedChanges, () -> apply(side, selectedChanges));
    }

    @RequiresWriteLock
    protected abstract void apply(@NotNull ThreeSide side, @NotNull List<? extends TextMergeChange> changes);

    private boolean isSomeChangeSelected(@NotNull ThreeSide side) {
      EditorEx editor = myViewer.getEditor(side);
      return DiffUtil.isSomeRangeSelected(editor,
                                          lines -> ContainerUtil.exists(myViewer.getAllChanges(),
                                                                        change -> isChangeSelected(change, lines, side)));
    }

    @RequiresEdt
    protected @NotNull @Unmodifiable List<TextMergeChange> getSelectedChanges(@NotNull ThreeSide side) {
      EditorEx editor = myViewer.getEditor(side);
      BitSet lines = DiffUtil.getSelectedLines(editor);
      return ContainerUtil.filter(myViewer.getChanges(), change -> isChangeSelected(change, lines, side));
    }

    protected boolean isChangeSelected(@NotNull TextMergeChange change, @NotNull BitSet lines, @NotNull ThreeSide side) {
      if (!isEnabled(change)) return false;
      int line1 = change.getStartLine(side);
      int line2 = change.getEndLine(side);
      return DiffUtil.isSelectedByLine(lines, line1, line2);
    }

    protected abstract @Nls String getText(@NotNull ThreeSide side);

    protected abstract boolean isVisible(@NotNull ThreeSide side);

    protected abstract boolean isEnabled(@NotNull TextMergeChange change);
  }

  public static class IgnoreSelectedChangesSideAction extends ApplySelectedChangesActionBase {
    private final @NotNull Side mySide;

    IgnoreSelectedChangesSideAction(@NotNull MergeThreesideViewer viewer, @NotNull Side side) {
      super(viewer);
      mySide = side;
      ActionUtil.copyFrom(this, mySide.select("Diff.IgnoreLeftSide", "Diff.IgnoreRightSide"));
    }

    @Override
    protected void apply(@NotNull ThreeSide side, @NotNull List<? extends TextMergeChange> changes) {
      for (TextMergeChange change : changes) {
        myViewer.ignoreChange(change, mySide, false);
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

  public static class IgnoreSelectedChangesAction extends ApplySelectedChangesActionBase {
    IgnoreSelectedChangesAction(@NotNull MergeThreesideViewer viewer) {
      super(viewer);
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
        myViewer.markChangeResolved(change);
      }
    }
  }

  public static class ResetResolvedChangeAction extends ApplySelectedChangesActionBase {
    ResetResolvedChangeAction(@NotNull MergeThreesideViewer viewer) {
      super(viewer);
      getTemplatePresentation().setIcon(AllIcons.Diff.Revert);
    }

    @Override
    protected void apply(@NotNull ThreeSide side, @NotNull List<? extends TextMergeChange> changes) {
      for (TextMergeChange change : changes) {
        myViewer.resetResolvedChange(change);
      }
    }

    @Override
    protected @Unmodifiable @NotNull List<TextMergeChange> getSelectedChanges(@NotNull ThreeSide side) {
      EditorEx editor = myViewer.getEditor(side);
      BitSet lines = DiffUtil.getSelectedLines(editor);
      return ContainerUtil.filter(myViewer.getAllChanges(), change -> isChangeSelected(change, lines, side));
    }

    @Override
    protected @Nls String getText(@NotNull ThreeSide side) {
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

  public static class ApplySelectedChangesAction extends ApplySelectedChangesActionBase {
    private final @NotNull Side mySide;

    ApplySelectedChangesAction(@NotNull MergeThreesideViewer viewer, @NotNull Side side) {
      super(viewer);
      mySide = side;
      ActionUtil.copyFrom(this, mySide.select("Diff.ApplyLeftSide", "Diff.ApplyRightSide"));
    }

    @Override
    protected void apply(@NotNull ThreeSide side, @NotNull List<? extends TextMergeChange> changes) {
      myViewer.replaceChanges(changes, mySide, false);
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

  public static class ResolveSelectedChangesAction extends ApplySelectedChangesActionBase {
    private final @NotNull Side mySide;

    ResolveSelectedChangesAction(@NotNull MergeThreesideViewer viewer, @NotNull Side side) {
      super(viewer);
      mySide = side;
    }

    @Override
    protected void apply(@NotNull ThreeSide side, @NotNull List<? extends TextMergeChange> changes) {
      myViewer.replaceChanges(changes, mySide, true);
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

  public static class ResolveSelectedConflictsAction extends ApplySelectedChangesActionBase {
    ResolveSelectedConflictsAction(@NotNull MergeThreesideViewer viewer) {
      super(viewer);
      ActionUtil.copyFrom(this, "Diff.ResolveConflict");
    }

    @Override
    protected void apply(@NotNull ThreeSide side, @NotNull List<? extends TextMergeChange> changes) {
      myViewer.resolveChangesAutomatically(changes, ThreeSide.BASE);
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
      return myViewer.canResolveChangeAutomatically(change, ThreeSide.BASE);
    }
  }
}
