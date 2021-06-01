// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Allows customizing <em>Edit | Extend/Shrink Selection</em> by providing additional text-ranges that can be selected.
 * <p>
 * Default behavior for extending/shrinking selections is based on the structure of the underlying PSI tree.
 * This EP allows for adding intermediate steps when a selection is extended/shrunk.
 * <p>
 * One use-case for custom languages is a function-call {@code f(a,b)} where the function call node has its two arguments
 * as children. With one of the arguments selected, extending the selection would directly grow the region to the whole function call.
 * However, one might want to select all arguments as an intermediate step.
 * This can be achieved by returning {@code true} from {@link #canSelect(PsiElement)} for the function call node,
 * and returning the region that covers all arguments in {@link #select(PsiElement, CharSequence, int, Editor)}.
 * <p>
 * Register in extension point {@code com.intellij.extendWordSelectionHandler}.
 * <p>
 * Please see the <a href="https://plugins.jetbrains.com/docs/intellij/text-selection.html#extendingshrinking-text-selection">IntelliJ Platform Docs</a>
 * for a high-level overview.
 *
 * @see SelectWordUtil
 */
public interface ExtendWordSelectionHandler {
  ExtensionPointName<ExtendWordSelectionHandler> EP_NAME = ExtensionPointName.create("com.intellij.extendWordSelectionHandler");

  /**
   * @return {@code true} if a {@link PsiElement} provides additional text ranges for extending/shrinking a selection and
   * {@link #select(PsiElement, CharSequence, int, Editor)} should be called.
   */
  boolean canSelect(@NotNull PsiElement e);

  /**
   * @return List of additional text ranges that are used as additional steps when extending/shrinking a selection.
   */
  @Nullable
  List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor);
}