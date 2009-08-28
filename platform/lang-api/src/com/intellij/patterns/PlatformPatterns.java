/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.patterns;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.pom.PomTarget;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class PlatformPatterns extends StandardPatterns {

  public static PsiElementPattern.Capture<PsiElement> psiElement() {
    return new PsiElementPattern.Capture<PsiElement>(PsiElement.class);
  }

  public static PsiElementPattern.Capture<PomTargetPsiElement> pomElement(final ElementPattern<? extends PomTarget> targetPattern) {
    return new PsiElementPattern.Capture<PomTargetPsiElement>(PomTargetPsiElement.class).with(new PatternCondition<PomTargetPsiElement>("withPomTarget") {
      @Override
      public boolean accepts(@NotNull final PomTargetPsiElement element, final ProcessingContext context) {
        return targetPattern.accepts(element.getTarget(), context);
      }
    });
  }

  public static PsiFilePattern.Capture<PsiFile> psiFile() {
    return new PsiFilePattern.Capture<PsiFile>(PsiFile.class);
  }

  public static <T extends PsiFile> PsiFilePattern.Capture<T> psiFile(Class<T> fileClass) {
    return new PsiFilePattern.Capture<T>(fileClass);
  }

  public static PsiElementPattern.Capture<PsiElement> psiElement(IElementType type) {
    return psiElement().withElementType(type);
  }

  public static <T extends PsiElement> PsiElementPattern.Capture<T> psiElement(final Class<T> aClass) {
    return new PsiElementPattern.Capture<T>(aClass);
  }

  public static IElementTypePattern elementType() {
    return new IElementTypePattern();
  }

  public static VirtualFilePattern virtualFile() {
    return new VirtualFilePattern();
  }
}
