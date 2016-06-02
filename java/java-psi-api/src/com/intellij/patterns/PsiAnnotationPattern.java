/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
public class PsiAnnotationPattern extends PsiElementPattern<PsiAnnotation, PsiAnnotationPattern> {
  static final PsiAnnotationPattern PSI_ANNOTATION_PATTERN = new PsiAnnotationPattern();

  private PsiAnnotationPattern() {
    super(PsiAnnotation.class);
  }

  public PsiAnnotationPattern qName(final ElementPattern<String> pattern) {
    return with(new PatternCondition<PsiAnnotation>("qName") {
      public boolean accepts(@NotNull final PsiAnnotation psiAnnotation, final ProcessingContext context) {
        return pattern.accepts(psiAnnotation.getQualifiedName(), context);
      }
    });
  }
  public PsiAnnotationPattern qName(@NonNls String qname) {
    return qName(StandardPatterns.string().equalTo(qname));
  }

  public PsiAnnotationPattern insideAnnotationAttribute(@NotNull final String attributeName, @NotNull final ElementPattern<PsiAnnotation> parentAnnoPattern) {
    return with(new PatternCondition<PsiAnnotation>("insideAnnotationAttribute") {
      final PsiNameValuePairPattern attrPattern = psiNameValuePair().withName(attributeName).withSuperParent(2, parentAnnoPattern);

      @Override
      public boolean accepts(@NotNull PsiAnnotation annotation, ProcessingContext context) {
        PsiElement attr = getParent(annotation);
        if (attr instanceof PsiArrayInitializerMemberValue) attr = getParent(attr);
        return attrPattern.accepts(attr);
      }
    });
  }
}
