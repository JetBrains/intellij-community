// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.merge;

import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.LineRange;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

public abstract class MergeModelBase<S extends MergeModelBase.State> implements Disposable {
  private static final Logger LOG = Logger.getInstance(MergeModelBase.class);

  @Nullable private final Project myProject;
  @NotNull private final Document myDocument;
  @Nullable private final UndoManager myUndoManager;

  @NotNull private final IntList myStartLines=new IntArrayList();
  @NotNull private final IntList myEndLines=new IntArrayList();

  @NotNull private final IntSet myChangesToUpdate = new IntOpenHashSet();
  private int myBulkChangeUpdateDepth;

  private boolean myInsideCommand;

  private boolean myDisposed;

  public MergeModelBase(@Nullable Project project, @NotNull Document document) {
    myProject = project;
    myDocument = document;
    myUndoManager = myProject != null ? UndoManager.getInstance(myProject) : UndoManager.getGlobalInstance();

    myDocument.addDocumentListener(new MyDocumentListener(), this);
  }

  @Override
  @RequiresEdt
  public void dispose() {
    if (myDisposed) return;
    myDisposed = true;

    LOG.assertTrue(myBulkChangeUpdateDepth == 0);

    myStartLines.clear();
    myEndLines.clear();
  }

  public boolean isDisposed() {
    return myDisposed;
  }

  public int getChangesCount() {
    return myStartLines.size();
  }

  public int getLineStart(int index) {
    return myStartLines.getInt(index);
  }

  public int getLineEnd(int index) {
    return myEndLines.getInt(index);
  }

  public void setChanges(@NotNull List<? extends LineRange> changes) {
    myStartLines.clear();
    myEndLines.clear();

    for (LineRange range : changes) {
      myStartLines.add(range.start);
      myEndLines.add(range.end);
    }
  }

  @RequiresEdt
  public boolean isInsideCommand() {
    return myInsideCommand;
  }

  private void setLineStart(int index, int line) {
    myStartLines.set(index, line);
  }

  private void setLineEnd(int index, int line) {
    myEndLines.set(index, line);
  }

  //
  // Repaint
  //

  @RequiresEdt
  public void invalidateHighlighters(int index) {
    if (myBulkChangeUpdateDepth > 0) {
      myChangesToUpdate.add(index);
    }
    else {
      reinstallHighlighters(index);
    }
  }

  @RequiresEdt
  public void enterBulkChangeUpdateBlock() {
    myBulkChangeUpdateDepth++;
  }

  @RequiresEdt
  public void exitBulkChangeUpdateBlock() {
    myBulkChangeUpdateDepth--;
    LOG.assertTrue(myBulkChangeUpdateDepth >= 0);

    if (myBulkChangeUpdateDepth == 0) {
      myChangesToUpdate.forEach((IntConsumer)index -> {
        reinstallHighlighters(index);
      });
      myChangesToUpdate.clear();
    }
  }

  @RequiresEdt
  protected abstract void reinstallHighlighters(int index);

  //
  // Undo
  //

  @NotNull
  @RequiresEdt
  protected abstract S storeChangeState(int index);

  @RequiresEdt
  protected void restoreChangeState(@NotNull S state) {
    setLineStart(state.myIndex, state.myStartLine);
    setLineEnd(state.myIndex, state.myEndLine);
  }

  @Nullable
  @RequiresEdt
  protected S processDocumentChange(int index, int oldLine1, int oldLine2, int shift) {
    int line1 = getLineStart(index);
    int line2 = getLineEnd(index);

    DiffUtil.UpdatedLineRange newRange = DiffUtil.updateRangeOnModification(line1, line2, oldLine1, oldLine2, shift);

    // RangeMarker can be updated in a different way
    boolean rangeAffected = newRange.damaged || (oldLine2 >= line1 && oldLine1 <= line2);

    S oldState = rangeAffected ? storeChangeState(index) : null;

    setLineStart(index, newRange.startLine);
    setLineEnd(index, newRange.endLine);

    return oldState;
  }

  private class MyDocumentListener implements DocumentListener {
    @Override
    public void beforeDocumentChange(@NotNull DocumentEvent e) {
      if (isDisposed()) return;
      enterBulkChangeUpdateBlock();

      if (getChangesCount() == 0) return;

      LineRange lineRange = DiffUtil.getAffectedLineRange(e);
      int shift = DiffUtil.countLinesShift(e);

      List<S> corruptedStates = new SmartList<>();
      for (int index = 0; index < getChangesCount(); index++) {
        S oldState = processDocumentChange(index, lineRange.start, lineRange.end, shift);
        if (oldState == null) continue;

        invalidateHighlighters(index);
        if (!isInsideCommand()) corruptedStates.add(oldState);
      }

      if (myUndoManager != null && !corruptedStates.isEmpty()) {
        // document undo is registered inside onDocumentChange, so our undo() will be called after its undo().
        // thus thus we can avoid checks for isUndoInProgress() (to avoid modification of the same TextMergeChange by this listener)
        myUndoManager.undoableActionPerformed(new MyUndoableAction(MergeModelBase.this, corruptedStates, true));
      }
    }

    @Override
    public void documentChanged(@NotNull DocumentEvent e) {
      if (isDisposed()) return;
      exitBulkChangeUpdateBlock();
    }
  }

  public boolean executeMergeCommand(@Nullable @NlsContexts.Command String commandName,
                                     @Nullable String commandGroupId,
                                     @NotNull UndoConfirmationPolicy confirmationPolicy,
                                     boolean underBulkUpdate,
                                     @Nullable IntList affectedChanges,
                                     @NotNull Runnable task) {
    IntList allAffectedChanges = affectedChanges != null ? collectAffectedChanges(affectedChanges) : null;
    return DiffUtil.executeWriteCommand(myProject, myDocument, commandName, commandGroupId, confirmationPolicy, underBulkUpdate, () -> {
      LOG.assertTrue(!myInsideCommand);

      // We should restore states after changes in document (by DocumentUndoProvider) to avoid corruption by our onBeforeDocumentChange()
      // Undo actions are performed in backward order, while redo actions are performed in forward order.
      // Thus we should register two UndoableActions.

      myInsideCommand = true;
      enterBulkChangeUpdateBlock();
      try {
        registerUndoRedo(true, allAffectedChanges);
        try {
          task.run();
        }
        finally {
          registerUndoRedo(false, allAffectedChanges);
        }
      }
      finally {
        exitBulkChangeUpdateBlock();
        myInsideCommand = false;
      }
    });
  }

  private void registerUndoRedo(boolean undo, @Nullable IntList affectedChanges) {
    if (myUndoManager == null) return;

    List<S> states;
    if (affectedChanges != null) {
      states = new ArrayList<>(affectedChanges.size());
      affectedChanges.forEach((IntConsumer)(index) -> {
        states.add(storeChangeState(index));
      });
    }
    else {
      states = new ArrayList<>(getChangesCount());
      for (int index = 0; index < getChangesCount(); index++) {
        states.add(storeChangeState(index));
      }
    }
    myUndoManager.undoableActionPerformed(new MyUndoableAction(this, states, undo));
  }

  private static final class MyUndoableAction extends BasicUndoableAction {
    @NotNull private final WeakReference<MergeModelBase<?>> myModelRef;
    @NotNull private final List<? extends State> myStates;
    private final boolean myUndo;

    MyUndoableAction(@NotNull MergeModelBase<?> model, @NotNull List<? extends State> states, boolean undo) {
      super(model.myDocument);
      myModelRef = new WeakReference<>(model);

      myStates = states;
      myUndo = undo;
    }

    @Override
    public void undo() {
      MergeModelBase<?> model = myModelRef.get();
      if (model != null && myUndo) restoreStates(model);
    }

    @Override
    public void redo() {
      MergeModelBase<?> model = myModelRef.get();
      if (model != null && !myUndo) restoreStates(model);
    }

    private void restoreStates(@NotNull MergeModelBase model) {
      if (model.isDisposed()) return;
      if (model.getChangesCount() == 0) return;

      model.enterBulkChangeUpdateBlock();
      try {
        for (State state : myStates) {
          //noinspection unchecked
          model.restoreChangeState(state);
          model.invalidateHighlighters(state.myIndex);
        }
      }
      finally {
        model.exitBulkChangeUpdateBlock();
      }
    }
  }

  //
  // Actions
  //

  @RequiresWriteLock
  public void replaceChange(int index, @NotNull List<? extends CharSequence> newContent) {
    LOG.assertTrue(isInsideCommand());
    int outputStartLine = getLineStart(index);
    int outputEndLine = getLineEnd(index);

    DiffUtil.applyModification(myDocument, outputStartLine, outputEndLine, newContent);

    if (outputStartLine == outputEndLine) { // onBeforeDocumentChange() should process other cases correctly
      int newOutputEndLine = outputStartLine + newContent.size();
      moveChangesAfterInsertion(index, outputStartLine, newOutputEndLine);
    }
  }

  @RequiresWriteLock
  public void appendChange(int index, @NotNull List<? extends CharSequence> newContent) {
    LOG.assertTrue(isInsideCommand());
    int outputStartLine = getLineStart(index);
    int outputEndLine = getLineEnd(index);

    DiffUtil.applyModification(myDocument, outputEndLine, outputEndLine, newContent);

    int newOutputEndLine = outputEndLine + newContent.size();
    moveChangesAfterInsertion(index, outputStartLine, newOutputEndLine);
  }

  /*
   * We want to include inserted block into change, so we are updating endLine(BASE).
   *
   * It could break order of changes if there are other changes that starts/ends at this line.
   * So we should check all other changes and shift them if necessary.
   */
  private void moveChangesAfterInsertion(int index,
                                         int newOutputStartLine,
                                         int newOutputEndLine) {
    LOG.assertTrue(isInsideCommand());

    if (getLineStart(index) != newOutputStartLine ||
        getLineEnd(index) != newOutputEndLine) {
      setLineStart(index, newOutputStartLine);
      setLineEnd(index, newOutputEndLine);
      invalidateHighlighters(index);
    }

    boolean beforeChange = true;
    for (int otherIndex = 0; otherIndex < getChangesCount(); otherIndex++) {
      int startLine = getLineStart(otherIndex);
      int endLine = getLineEnd(otherIndex);
      if (endLine < newOutputStartLine) continue;
      if (startLine > newOutputEndLine) break;
      if (index == otherIndex) {
        beforeChange = false;
        continue;
      }

      int newStartLine = beforeChange ? Math.min(startLine, newOutputStartLine) : newOutputEndLine;
      int newEndLine = beforeChange ? Math.min(endLine, newOutputStartLine) : Math.max(endLine, newOutputEndLine);
      if (startLine != newStartLine || endLine != newEndLine) {
        setLineStart(otherIndex, newStartLine);
        setLineEnd(otherIndex, newEndLine);
        invalidateHighlighters(otherIndex);
      }
    }
  }

  /*
   * Nearby changes could be affected as well (ex: by moveChangesAfterInsertion)
   *
   * null means all changes could be affected
   */
  @NotNull
  @RequiresEdt
  private IntList collectAffectedChanges(@NotNull IntList directChanges) {
    IntList result = new IntArrayList(directChanges.size());

    int directArrayIndex = 0;
    int otherIndex = 0;
    while (directArrayIndex < directChanges.size() && otherIndex < getChangesCount()) {
      int directIndex = directChanges.getInt(directArrayIndex);

      if (directIndex == otherIndex) {
        result.add(directIndex);
        otherIndex++;
        continue;
      }

      int directStart = getLineStart(directIndex);
      int directEnd = getLineEnd(directIndex);
      int otherStart = getLineStart(otherIndex);
      int otherEnd = getLineEnd(otherIndex);

      if (otherEnd < directStart) {
        otherIndex++;
        continue;
      }
      if (otherStart > directEnd) {
        directArrayIndex++;
        continue;
      }

      result.add(otherIndex);
      otherIndex++;
    }

    LOG.assertTrue(directChanges.size() <= result.size());
    return result;
  }

  //
  // Helpers
  //

  public static class State {
    public final int myIndex;
    public final int myStartLine;
    public final int myEndLine;

    public State(int index, int startLine, int endLine) {
      myIndex = index;
      myStartLine = startLine;
      myEndLine = endLine;
    }
  }
}
