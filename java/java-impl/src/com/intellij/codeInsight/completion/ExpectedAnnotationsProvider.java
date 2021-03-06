// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiType;
import com.intellij.psi.filters.getters.ExpectedTypesGetter;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.StandardPatterns.or;

/**
 * @author peter
 */
final class ExpectedAnnotationsProvider {
  static final ElementPattern<PsiElement> ANNOTATION_ATTRIBUTE_VALUE = or(
    PsiJavaPatterns.psiElement().withParent(PsiNameValuePair.class),
    PsiJavaPatterns.psiElement().withSuperParent(2, PsiNameValuePair.class));

  static void addCompletions(@NotNull PsiElement element, @NotNull CompletionResultSet result) {
    for (final PsiType type : ExpectedTypesGetter.getExpectedTypes(element, false)) {
      final PsiClass psiClass = PsiUtil.resolveClassInType(type);
      if (psiClass != null && psiClass.isAnnotationType()) {
        result.addElement(AllClassesGetter.createLookupItem(psiClass, AnnotationInsertHandler.INSTANCE));
      }
    }
  }
}
