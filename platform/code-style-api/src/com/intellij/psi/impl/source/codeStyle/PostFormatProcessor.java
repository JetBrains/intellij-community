// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;

/**
 * The processor is run after core formatter primarily to handle non-whitespace changes, for example to add code block braces and other
 * elements. If only whitespace changes are allowed, for example when ad hoc formatting of changed PSI elements is performed, the
 * processor is not called.
 */
public interface PostFormatProcessor {
  ExtensionPointName<PostFormatProcessor> EP_NAME = ExtensionPointName.create("com.intellij.postFormatProcessor");

  /**
   * Process the given source element and returns its formatted equivalent.
   *
   * @param source   The source element to format.
   * @param settings The root code style settings to use.
   *
   * @return The resulting element containing necessary changes. Note: the element must be valid! Thus, if there are PSI modifications
   * which do not invalidate the source element, the same source element can be returned. But in other cases it should be a valid
   * element equivalent from the updated PSI tree.
   */
  @NotNull
  PsiElement processElement(@NotNull PsiElement source, @NotNull CodeStyleSettings settings);

  /**
   * Process the source PSI file within the given text range.
   *
   * @param source          The source PSI file.
   * @param rangeToReformat The range within which the changes can be made.
   * @param settings        The root code style settings to use.
   *
   * @return The updated text range after the changes.
   */
  @NotNull
  TextRange processText(@NotNull PsiFile source, @NotNull TextRange rangeToReformat, @NotNull CodeStyleSettings settings);
}
