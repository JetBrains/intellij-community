// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util;

import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.editor.ex.EditorEx;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class PrevNextDifferenceIterableBase<T> implements PrevNextDifferenceIterable {
  @NotNull
  protected abstract List<? extends T> getChanges();

  @NotNull
  protected abstract EditorEx getEditor();

  protected abstract int getStartLine(@NotNull T change);

  protected abstract int getEndLine(@NotNull T change);

  protected void scrollToChange(@NotNull T change) {
    DiffUtil.scrollEditor(getEditor(), getStartLine(change), true);
  }

  @Override
  public boolean canGoNext() {
    List<? extends T> changes = getChanges();
    if (changes.isEmpty()) return false;

    EditorEx editor = getEditor();
    int line = editor.getCaretModel().getLogicalPosition().line;
    if (line == DiffUtil.getLineCount(editor.getDocument()) - 1) return false;

    T lastChange = changes.get(changes.size() - 1);
    if (getStartLine(lastChange) <= line) return false;

    return true;
  }

  @Override
  public void goNext() {
    List<? extends T> changes = getChanges();
    int line = getEditor().getCaretModel().getLogicalPosition().line;

    T next = null;
    for (T change : changes) {
      if (getStartLine(change) <= line) continue;

      next = change;
      break;
    }

    assert next != null;
    scrollToChange(next);
  }

  @Override
  public boolean canGoPrev() {
    List<? extends T> changes = getChanges();
    if (changes.isEmpty()) return false;

    int line = getEditor().getCaretModel().getLogicalPosition().line;
    if (line == 0) return false;

    T firstChange = changes.get(0);
    if (getEndLine(firstChange) > line) return false;
    if (getStartLine(firstChange) >= line) return false;

    return true;
  }

  @Override
  public void goPrev() {
    List<? extends T> changes = getChanges();
    int line = getEditor().getCaretModel().getLogicalPosition().line;

    T prev = null;
    for (int i = 0; i < changes.size(); i++) {
      T change = changes.get(i);

      T next = i < changes.size() - 1 ? changes.get(i + 1) : null;
      if (next == null || getEndLine(next) > line || getStartLine(next) >= line) {
        prev = change;
        break;
      }
    }

    assert prev != null;
    scrollToChange(prev);
  }
}
