// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands;

import com.intellij.codeInsight.completion.command.CommandCompletionFactory;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiJShellFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiTypeElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class JavaCommandCompletionFactory implements CommandCompletionFactory, DumbAware {

  @Override
  public boolean isApplicable(@NotNull PsiFile psiFile, int offset) {
    if (!(psiFile instanceof PsiJavaFile)) return false;
    //Doesn't work well. Disable for now
    if (psiFile instanceof PsiJShellFile) return false;
    if (isAfterTypeElementDotsInParameterList(psiFile, offset, 1)) return false;
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

  public static boolean isAfterTypeElementDotsInParameterList(@NotNull PsiFile cloneFile, int offset, int expectedDotsCount) {
    PsiFile originalFile = cloneFile.getOriginalFile();
    String text = originalFile.getFileDocument().getText();
    int dots = 0;
    if (text.length() > offset && text.charAt(offset) == '.') {
      dots++;
    }
    if (text.length() > offset + 1 && text.charAt(offset + 1) == '.') {
      dots++;
    }
    if (dots != expectedDotsCount) return false;
    PsiElement firstElement = cloneFile.findElementAt(offset - 1);
    if (firstElement == null) return false;
    return firstElement instanceof PsiIdentifier identifier &&
           identifier.getParent() instanceof PsiJavaCodeReferenceElement referenceElement &&
           referenceElement.getParent() instanceof PsiTypeElement typeElement &&
           ((typeElement.getParent() instanceof PsiParameter parameter &&
             parameter.getParent() instanceof PsiParameterList) ||
            (typeElement.getParent() instanceof PsiParameterList));
  }
}
