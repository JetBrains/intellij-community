// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands;

import com.intellij.codeInsight.completion.command.CommandCompletionSuffixProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiTypeElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * The part of Java command completion that has to be available on the remote development frontend:
 * the trigger suffixes (Java uses the defaults) and the cheap parameter list check shared with
 * {@link com.intellij.codeInsight.editorActions.JavaTypedHandlerBase}.
 * <p>
 * The factory itself lives in {@code intellij.java.impl}, see
 * {@code com.intellij.codeInsight.completion.commands.impl.JavaCommandCompletionFactory}.
 */
@ApiStatus.Internal
public final class JavaCommandCompletionSupport implements CommandCompletionSuffixProvider {

  private JavaCommandCompletionSupport() {
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
