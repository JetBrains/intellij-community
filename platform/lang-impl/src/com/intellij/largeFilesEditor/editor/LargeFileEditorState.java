// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.largeFilesEditor.editor;

import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

final class LargeFileEditorState implements FileEditorState {
  long caretPageNumber = 0;
  int caretSymbolOffsetInPage = 0;

  @Override
  public boolean canBeMergedWith(@NotNull FileEditorState otherState, @NotNull FileEditorStateLevel level) {
    if (otherState instanceof LargeFileEditorState state) {
      return caretPageNumber == state.caretPageNumber
             && caretSymbolOffsetInPage == state.caretSymbolOffsetInPage;
    }
    return false;
  }

  @Override
  public @NonNls String toString() {
    return "[p" + caretPageNumber + ",s" + caretSymbolOffsetInPage + "]";  // 'p' - Page number, 's' - Symbol offset in Page
  }
}
