/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiType;
import com.intellij.psi.filters.getters.ExpectedTypesGetter;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.StandardPatterns.or;

/**
 * @author peter
 */
class ExpectedAnnotationsProvider extends CompletionProvider<CompletionParameters> {
  static final ElementPattern<PsiElement> ANNOTATION_ATTRIBUTE_VALUE = or(
    PsiJavaPatterns.psiElement().withParent(PsiNameValuePair.class),
    PsiJavaPatterns.psiElement().withSuperParent(2, PsiNameValuePair.class));

  @Override
  public void addCompletions(@NotNull final CompletionParameters parameters,
                             final ProcessingContext context,
                             @NotNull final CompletionResultSet result) {
    final PsiElement element = parameters.getPosition();

    for (final PsiType type : ExpectedTypesGetter.getExpectedTypes(element, false)) {
      final PsiClass psiClass = PsiUtil.resolveClassInType(type);
      if (psiClass != null && psiClass.isAnnotationType()) {
        result.addElement(AllClassesGetter.createLookupItem(psiClass, AnnotationInsertHandler.INSTANCE));
      }
    }
  }
}
