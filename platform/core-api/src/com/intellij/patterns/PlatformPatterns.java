// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.patterns;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.PomTarget;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * Factory for {@link PsiElement}, {@link PomTarget}, {@link IElementType} and {@link VirtualFile}-based patterns.
 */
public class PlatformPatterns extends StandardPatterns {

  public static PsiElementPattern.Capture<PsiElement> psiElement() {
    return new PsiElementPattern.Capture<>(PsiElement.class);
  }

  public static PsiElementPattern.Capture<PsiComment> psiComment() {
    return new PsiElementPattern.Capture<>(PsiComment.class);
  }

  public static PsiElementPattern.Capture<PomTargetPsiElement> pomElement(final ElementPattern<? extends PomTarget> targetPattern) {
    return new PsiElementPattern.Capture<>(PomTargetPsiElement.class).with(new PatternCondition<PomTargetPsiElement>("withPomTarget") {
      @Override
      public boolean accepts(@NotNull final PomTargetPsiElement element, final ProcessingContext context) {
        return targetPattern.accepts(element.getTarget(), context);
      }
    });
  }

  public static PsiFilePattern.Capture<PsiFile> psiFile() {
    return new PsiFilePattern.Capture<>(PsiFile.class);
  }

  public static <T extends PsiFile> PsiFilePattern.Capture<T> psiFile(Class<T> fileClass) {
    return new PsiFilePattern.Capture<>(fileClass);
  }

  public static PsiElementPattern.Capture<PsiElement> psiElement(IElementType type) {
    return psiElement().withElementType(type);
  }

  public static <T extends PsiElement> PsiElementPattern.Capture<T> psiElement(final Class<T> aClass) {
    return new PsiElementPattern.Capture<>(aClass);
  }

  public static IElementTypePattern elementType() {
    return new IElementTypePattern();
  }

  public static VirtualFilePattern virtualFile() {
    return new VirtualFilePattern();
  }
}
