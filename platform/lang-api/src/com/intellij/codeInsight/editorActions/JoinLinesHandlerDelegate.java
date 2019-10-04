// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Allows to customize <em>Edit|Join Lines</em>.
 *
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
   * @param file     where the lines are
   * @param start    offset where the whitespace between lines starts
   * @param end      offset where the whitespace between lines ends
   * @return the position to place the caret after the operation, or {@link #CANNOT_JOIN} if this handler was not able
   * to perform the operation.
   */
  int tryJoinLines(@NotNull Document document, @NotNull PsiFile file, int start, final int end);

  /**
   * Return this from {@link #tryJoinLines} if it could not join the lines.
   */
  int CANNOT_JOIN = -1;
}
