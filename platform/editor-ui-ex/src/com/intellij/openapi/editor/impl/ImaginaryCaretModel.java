// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class ImaginaryCaretModel implements CaretModel {
  private final ImaginaryEditor myEditor;
  private final ImaginaryCaret myCaret;

  private static final Logger LOG = Logger.getInstance(ImaginaryCaretModel.class);

  public ImaginaryCaretModel(ImaginaryEditor editor) {
    myEditor = editor;
    myCaret = new ImaginaryCaret(this);
  }

  protected ImaginaryEditor getEditor() {
    return myEditor;
  }

  @Override
  public @NotNull Caret getCurrentCaret() {
    return myCaret;
  }

  protected RuntimeException notImplemented() {
    return myEditor.notImplemented();
  }

  @Override
  public void addCaretListener(@NotNull CaretListener listener) {
    LOG.info("Called ImaginaryCaretModel#addCaretListener which is stubbed and has no implementation");
  }

  @Override
  public void removeCaretListener(@NotNull CaretListener listener) {
    LOG.info("Called ImaginaryCaretModel#removeCaretListener which is stubbed and has no implementation");
  }

  @Override
  public TextAttributes getTextAttributes() {
    throw notImplemented();
  }

  @Override
  public boolean supportsMultipleCarets() {
    throw notImplemented();
  }

  @Override
  public int getMaxCaretCount() {
    throw notImplemented();
  }

  @Override
  public @NotNull Caret getPrimaryCaret() {
    return myCaret;
  }

  @Override
  public int getCaretCount() {
    return 1;
  }

  @Override
  public @NotNull List<Caret> getAllCarets() {
    return Collections.singletonList(myCaret);
  }

  @Override
  public @Nullable Caret getCaretAt(@NotNull VisualPosition pos) {
    throw notImplemented();
  }

  @Override
  public @Nullable Caret addCaret(@NotNull VisualPosition pos, boolean makePrimary) {
    throw notImplemented();
  }

  @Override
  public @Nullable Caret addCaret(@NotNull LogicalPosition pos, boolean makePrimary) {
    throw notImplemented();
  }

  @Override
  public boolean removeCaret(@NotNull Caret caret) {
    throw notImplemented();
  }

  @Override
  public void removeSecondaryCarets() {
    throw notImplemented();
  }

  @Override
  public void setCaretsAndSelections(@NotNull List<? extends CaretState> caretStates) {
    setCaretsAndSelections(caretStates, true);
  }

  @Override
  public void setCaretsAndSelections(@NotNull List<? extends CaretState> caretStates, boolean updateSystemSelection) {
    if (caretStates.size() != 1) {
      LOG.error("Imaginary caret does not support multicaret. caretStates=" + caretStates);
    }
    CaretState state = caretStates.get(0);
    if (state.getCaretPosition() != null) {
      myCaret.moveToOffset(myEditor.logicalPositionToOffset(state.getCaretPosition()));
    }
    if (state.getSelectionStart() != null && state.getSelectionEnd() != null && !state.getSelectionStart().equals(state.getSelectionEnd())) {
      myCaret.setSelection(myEditor.logicalPositionToOffset(state.getSelectionStart()),
                           myEditor.logicalPositionToOffset(state.getSelectionEnd()));
    }
  }

  @Override
  public @NotNull List<CaretState> getCaretsAndSelections() {
    return Collections.singletonList(
      new CaretState(myCaret.getLogicalPosition(),
                     0,
                     myEditor.offsetToLogicalPosition(myCaret.getSelectionStart()),
                     myEditor.offsetToLogicalPosition(myCaret.getSelectionEnd()))
    );
  }

  @Override
  public void runForEachCaret(@NotNull CaretAction action) {
    action.perform(myCaret);
  }

  @Override
  public void runForEachCaret(@NotNull CaretAction action, boolean reverseOrder) {
    action.perform(myCaret);
  }

  @Override
  public void addCaretActionListener(@NotNull CaretActionListener listener, @NotNull Disposable disposable) {
    throw notImplemented();
  }

  @Override
  public void runBatchCaretOperation(@NotNull Runnable runnable) {
    throw notImplemented();
  }
}
