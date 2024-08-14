// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.javadoc;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Describes a markdown code block */
public interface PsiMarkdownCodeBlock extends PsiElement {

  /** @return The text of the code block, without superfluous elements */
  @NotNull
  String getCodeText();

  /** @return The language for this code block */
  @Nullable Language getCodeLanguage();
}
