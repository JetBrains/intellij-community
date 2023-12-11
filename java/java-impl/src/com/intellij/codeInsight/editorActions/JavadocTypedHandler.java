// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JavadocTypedHandler extends AbstractBasicJavadocTypedHandler {
  @Override
  public boolean isJavaFile(@Nullable PsiFile file) {
    return file instanceof PsiJavaFile;
  }

  @Override
  public boolean isSingleHtmlTag(@NotNull String tagName) {
    return HtmlUtil.isSingleHtmlTag(tagName, false);
  }
}