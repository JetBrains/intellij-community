/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.patterns;

import com.intellij.psi.PsiAnnotation;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class PsiAnnotationPattern extends PsiElementPattern<PsiAnnotation, PsiAnnotationPattern> {
  protected PsiAnnotationPattern() {
    super(PsiAnnotation.class);
  }

  public PsiAnnotationPattern qName(final ElementPattern pattern) {
    return with(new PatternCondition<PsiAnnotation>("qName") {
      public boolean accepts(@NotNull final PsiAnnotation psiAnnotation, final ProcessingContext context) {
        return pattern.getCondition().accepts(psiAnnotation.getQualifiedName(), context);
      }
    });
  }
  public PsiAnnotationPattern qName(@NonNls String qname) {
    return qName(StandardPatterns.string().equalTo(qname));
  }
}
