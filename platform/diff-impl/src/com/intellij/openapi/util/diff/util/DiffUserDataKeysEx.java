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
package com.intellij.openapi.util.diff.util;

import com.intellij.openapi.diff.DiffNavigationContext;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.diff.tools.util.LineFragmentCache;

import javax.swing.*;

public interface DiffUserDataKeysEx extends DiffUserDataKeys {
  //
  // DiffRequest
  //

  Key<DiffNavigationContext> NAVIGATION_CONTEXT = Key.create("Diff.NavigationContext");
  Key<LineFragmentCache> LINE_FRAGMENT_CACHE = Key.create("Diff.LineFragmentCache");

  //
  // DiffContext
  //

  Key<JComponent> BOTTOM_PANEL = Key.create("Diff.BottomPanel"); // Could implement Disposable
}
