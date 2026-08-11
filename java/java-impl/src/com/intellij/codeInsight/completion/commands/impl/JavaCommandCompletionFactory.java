// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl;

import com.intellij.codeInsight.completion.command.CommandCompletionFactory;
import com.intellij.codeInsight.completion.commands.JavaCommandCompletionSupport;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJShellFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaToken;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * The suffixes are registered separately in {@code intellij.java.frontback.impl},
 * see {@link JavaCommandCompletionSupport}.
 */
@ApiStatus.Internal
public final class JavaCommandCompletionFactory implements CommandCompletionFactory, DumbAware {

  @Override
  public boolean isApplicable(@NotNull PsiFile psiFile, int offset) {
    if (!(psiFile instanceof PsiJavaFile)) return false;
    //Doesn't work well. Disable for now
    if (psiFile instanceof PsiJShellFile) return false;
    if (JavaCommandCompletionSupport.isAfterTypeElementDotsInParameterList(psiFile, offset, 1)) return false;
    if (isInsideStringLiteral(psiFile, offset)) return false;
    return true;
  }

  private static boolean isInsideStringLiteral(@NotNull PsiFile file, int offset) {
    PsiElement elementAt = file.findElementAt(offset);
    if (!(elementAt instanceof PsiJavaToken psiJavaToken &&
          (psiJavaToken.getTokenType() == JavaTokenType.STRING_LITERAL ||
           psiJavaToken.getTokenType() == JavaTokenType.TEXT_BLOCK_LITERAL
          ))) {
      return false;
    }

    return psiJavaToken.getTextRange().containsOffset(offset);
  }
}
