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

package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;

/**
 * @author yole
 */
public interface JoinLinesHandlerDelegate {
  ExtensionPointName<JoinLinesHandlerDelegate> EP_NAME = ExtensionPointName.create("com.intellij.joinLinesHandler");

  /**
   * Tries to join lines at the specified position of the specified file.
   * Method is called on a document where part of whitespace between lines is already stripped,
   * and it has a chance to smooth out the join point.
   *
   * @param document where the lines are
   * @param file where the lines are
   * @param start offset where the whitespace between lines starts
   * @param end offset where the whitespace between lines ends
   * @return the position to place the caret after the operation, or -1 if this handler was not able
   *         to perform the operation.
   */
  int tryJoinLines(Document document, PsiFile file, int start, final int end);

  /** Return this from {@link #tryJoinLines} if it could not join the lines. */
  int CANNOT_JOIN = -1;
}
