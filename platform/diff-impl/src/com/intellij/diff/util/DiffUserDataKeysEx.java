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
package com.intellij.diff.util;

import com.intellij.diff.tools.util.LineFragmentCache;
import com.intellij.openapi.diff.DiffNavigationContext;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.util.Key;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public interface DiffUserDataKeysEx extends DiffUserDataKeys {
  //
  // DiffRequest
  //
  enum ScrollToPolicy {
    FIRST_CHANGE, LAST_CHANGE;

    @Nullable
    public <T> T select(@NotNull List<T> changes) {
      if (this == FIRST_CHANGE) return ContainerUtil.getFirstItem(changes);
      if (this == LAST_CHANGE) return ContainerUtil.getLastItem(changes);
      throw new IllegalStateException();
    }
  }

  Key<ScrollToPolicy> SCROLL_TO_CHANGE = Key.create("Diff.ScrollToChange");
  Key<LogicalPosition[]> EDITORS_CARET_POSITION = Key.create("Diff.EditorsCaretPosition");

  Key<DiffNavigationContext> NAVIGATION_CONTEXT = Key.create("Diff.NavigationContext");
  Key<LineFragmentCache> LINE_FRAGMENT_CACHE = Key.create("Diff.LineFragmentCache");

  //
  // DiffContext
  //

  Key<String> PLACE = Key.create("Diff.Place");
  Key<JComponent> BOTTOM_PANEL = Key.create("Diff.BottomPanel"); // Could implement Disposable

  Key<Boolean> SHOW_READ_ONLY_LOCK = Key.create("Diff.ShowReadOnlyLock");
}
