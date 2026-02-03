/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.editor.actionSystem;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.util.text.ImmutableCharSequence;
import org.jetbrains.annotations.NotNull;

/**
 * Combined text/caret model that is used to record oncoming changes before acquiring
 * a write lock and making actual changes to {@link Document} or {@link CaretModel}.
 * <p>
 * The draft is used to perform preemptive rendering in editor before acquiring a write lock.
 * <p>
 * The interface is simplistic and can be extended later to support multiple carets, selection, etc.
 *
 * @see TypedAction#beforeActionPerformed(Editor, char, DataContext, ActionPlan)
 */
public interface ActionPlan {
  /**
   * Retrieves the current text content.
   *
   * @return text content.
   */
  @NotNull
  ImmutableCharSequence getText();

  /**
   * Replaces the specified range of text with the specified string.
   *
   * @param begin the start offset of the range to replace.
   * @param end   the end offset of the range to replace.
   * @param s     the text to replace with.
   */
  void replace(int begin, int end, String s);

  /**
   * Returns the offset of the caret in the text.
   *
   * @return the caret offset.
   */
  int getCaretOffset();

  /**
   * Moves the caret to the specified offset in the text.
   *
   * @param offset the offset to move to.
   */
  void setCaretOffset(int offset);
}
