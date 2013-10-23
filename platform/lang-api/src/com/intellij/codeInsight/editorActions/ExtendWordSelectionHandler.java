/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface ExtendWordSelectionHandler {
  ExtensionPointName<ExtendWordSelectionHandler> EP_NAME = ExtensionPointName.create("com.intellij.extendWordSelectionHandler");
  
  boolean canSelect(PsiElement e);

  List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor);

  /**
   * Returns minimal selection length for given element.
   * 
   * Sometimes the length of word selection should be bounded below. 
   * E.g. it is useful in languages that requires prefixes for variable (php, less, etc.).
   * By default this kind of variables will be selected without prefix: @<selection>variable</selection>,
   * but it make sense to exclude this range from selection list. 
   * So if this method returns 9 as a minimal length of selection
   * then first selection range for @variable will be: <selection>@variable</selection>.
   * 
   * @param element element at caret
   * @param text text in editor
   * @param cursorOffset current caret offset in editor
   * @return minimal selection length for given element
   */
  int getMinimalTextRangeLength(@NotNull PsiElement element, @NotNull CharSequence text, int cursorOffset);
}