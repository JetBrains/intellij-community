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
package com.intellij.diff.tools.util;

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

  protected abstract void scrollToChange(@NotNull T change);

  @Override
  public boolean canGoNext() {
    List<? extends T> changes = getChanges();
    if (changes.isEmpty()) return false;

    EditorEx editor = getEditor();
    int line = editor.getCaretModel().getLogicalPosition().line;
    if (line == editor.getDocument().getLineCount() - 1) return false;

    T lastChange = changes.get(changes.size() - 1);
    if (getStartLine(lastChange) <= line) return false;

    return true;
  }

  @Override
  public void goNext() {
    List<? extends T> changes = getChanges();
    int line = getEditor().getCaretModel().getLogicalPosition().line;

    T next = null;
    for (int i = 0; i < changes.size(); i++) {
      T change = changes.get(i);
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