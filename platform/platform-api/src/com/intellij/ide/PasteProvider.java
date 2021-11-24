/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.ide;

import com.intellij.openapi.actionSystem.DataContext;
import org.jetbrains.annotations.NotNull;

/**
 * If defined as {@code com.intellij.customPasteProvider} extension, can modify the behaviour of 'Paste' action in editor:
 * if {@link #isPasteEnabled(DataContext)} returns {@code true}, then {@link #performPaste(DataContext)} is executed instead of default
 * paste logic. Transferable to paste can be retrieved from passed {@code dataContext} using
 * {@link com.intellij.openapi.editor.actions.PasteAction#TRANSFERABLE_PROVIDER} key.
 */
public interface PasteProvider {
  void performPaste(@NotNull DataContext dataContext);

  /**
   * Should perform fast and memory cheap negation. May return incorrect true.
   * See #12326
   */
  boolean isPastePossible(@NotNull DataContext dataContext);
  boolean isPasteEnabled(@NotNull DataContext dataContext);
}
