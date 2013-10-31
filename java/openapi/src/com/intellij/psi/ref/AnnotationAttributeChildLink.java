/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.ref;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
*/
public class AnnotationAttributeChildLink extends PsiChildLink<PsiAnnotation, PsiAnnotationMemberValue> {
  private final String myAttributeName;

  public AnnotationAttributeChildLink(@NotNull @NonNls String attributeName) {
    myAttributeName = attributeName;
  }

  @NotNull
  public String getAttributeName() {
    return myAttributeName;
  }

  @Override
  public PsiAnnotationMemberValue findLinkedChild(@Nullable PsiAnnotation psiAnnotation) {
    if (psiAnnotation == null) return null;

    return psiAnnotation.findDeclaredAttributeValue(myAttributeName);
  }

  @Override
  @NotNull
  public PsiAnnotationMemberValue createChild(@NotNull PsiAnnotation psiAnnotation) throws IncorrectOperationException {
    final PsiExpression nullValue = JavaPsiFacade.getElementFactory(psiAnnotation.getProject()).createExpressionFromText(PsiKeyword.NULL, null);
    psiAnnotation.setDeclaredAttributeValue(myAttributeName, nullValue);
    return ObjectUtils.assertNotNull(psiAnnotation.findDeclaredAttributeValue(myAttributeName));
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final AnnotationAttributeChildLink link = (AnnotationAttributeChildLink)o;

    if (!myAttributeName.equals(link.myAttributeName)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myAttributeName.hashCode();
  }
}
