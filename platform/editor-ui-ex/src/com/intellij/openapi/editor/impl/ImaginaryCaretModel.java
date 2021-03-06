// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

class ImaginaryCaretModel implements CaretModel {
  private final ImaginaryEditor myEditor;
  private final ImaginaryCaret myCaret;

  ImaginaryCaretModel(ImaginaryEditor editor) {
    myEditor = editor;
    myCaret = new ImaginaryCaret(this);
  }

  ImaginaryEditor getEditor() {
    return myEditor;
  }

  @NotNull
  @Override
  public Caret getCurrentCaret() {
    return myCaret;
  }

  protected RuntimeException notImplemented() {
    return myEditor.notImplemented();
  }

  @Override
  public void addCaretListener(@NotNull CaretListener listener) {
    throw notImplemented();
  }

  @Override
  public void removeCaretListener(@NotNull CaretListener listener) {
    throw notImplemented();
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

  @NotNull
  @Override
  public Caret getPrimaryCaret() {
    return myCaret;
  }

  @Override
  public int getCaretCount() {
    return 1;
  }

  @NotNull
  @Override
  public List<Caret> getAllCarets() {
    return Collections.singletonList(myCaret);
  }

  @Nullable
  @Override
  public Caret getCaretAt(@NotNull VisualPosition pos) {
    throw notImplemented();
  }

  @Nullable
  @Override
  public Caret addCaret(@NotNull VisualPosition pos, boolean makePrimary) {
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
    throw notImplemented();
  }

  @Override
  public void setCaretsAndSelections(@NotNull List<? extends CaretState> caretStates, boolean updateSystemSelection) {
    throw notImplemented();
  }

  @NotNull
  @Override
  public List<CaretState> getCaretsAndSelections() {
    throw notImplemented();
  }

  @Override
  public void runForEachCaret(@NotNull CaretAction action) {
    throw notImplemented();
  }

  @Override
  public void runForEachCaret(@NotNull CaretAction action, boolean reverseOrder) {
    throw notImplemented();
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
