// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.patterns;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PsiJavaPatterns.psiNameValuePair;

/**
 * @author peter
 */
public final class PsiAnnotationPattern extends PsiElementPattern<PsiAnnotation, PsiAnnotationPattern> {
  static final PsiAnnotationPattern PSI_ANNOTATION_PATTERN = new PsiAnnotationPattern();

  private PsiAnnotationPattern() {
    super(PsiAnnotation.class);
  }

  public PsiAnnotationPattern qName(final ElementPattern<String> pattern) {
    return with(new PatternCondition<PsiAnnotation>("qName") {
      @Override
      public boolean accepts(@NotNull final PsiAnnotation psiAnnotation, final ProcessingContext context) {
        return pattern.accepts(psiAnnotation.getQualifiedName(), context);
      }
    });
  }
  public PsiAnnotationPattern qName(@NonNls final String qname) {
    return with(new PatternCondition<PsiAnnotation>("qName") {
      @Override
      public boolean accepts(@NotNull final PsiAnnotation psiAnnotation, final ProcessingContext context) {
        return psiAnnotation.hasQualifiedName(qname);
      }
    });
  }

  public PsiAnnotationPattern insideAnnotationAttribute(@NotNull final String attributeName, @NotNull final ElementPattern<? extends PsiAnnotation> parentAnnoPattern) {
    return with(new PatternCondition<PsiAnnotation>("insideAnnotationAttribute") {
      final PsiNameValuePairPattern attrPattern = psiNameValuePair().withName(attributeName).withSuperParent(2, parentAnnoPattern);

      @Override
      public boolean accepts(@NotNull PsiAnnotation annotation, ProcessingContext context) {
        PsiElement attr = getParentElement(annotation);
        if (attr instanceof PsiArrayInitializerMemberValue) attr = getParentElement(attr);
        return attrPattern.accepts(attr);
      }
    });
  }

  private PsiElement getParentElement(@NotNull PsiElement element) {
    return getParent(element);
  }
}
