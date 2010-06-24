/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi.util.proximity;

import com.intellij.openapi.util.NullableLazyKey;
import com.intellij.psi.*;
import com.intellij.psi.util.ProximityLocation;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
*/
public class JavaInheritanceWeigher extends ProximityWeigher {
  private static final NullableLazyKey<PsiClass, ProximityLocation> PLACE_CLASS = NullableLazyKey.create("PLACE_CLASS", new NullableFunction<ProximityLocation, PsiClass>() {
    @Override
    public PsiClass fun(ProximityLocation location) {
      return PsiTreeUtil.getContextOfType(location.getPosition(), PsiClass.class, false);
    }
  });

  public Comparable weigh(@NotNull final PsiElement element, final ProximityLocation location) {
    if (element instanceof PsiClass && isTooGeneral((PsiClass)element)) return false;
    if (element instanceof PsiMethod && isTooGeneral(((PsiMethod)element).getContainingClass())) return false;

    PsiClass contextClass = PLACE_CLASS.getValue(location);
    if (contextClass == null) {
      return false;
    }

    PsiClass placeClass = findPlaceClass(element, location.getPosition());
    if (placeClass == null) return false;

    while (contextClass != null) {
      PsiClass elementClass = placeClass;
      while (elementClass != null) {
        if (contextClass.isInheritor(elementClass, true)) return true;
        elementClass = elementClass.getContainingClass();
      }
      contextClass = contextClass.getContainingClass();
    }
    return false;
  }

  @Nullable
  private static PsiClass findPlaceClass(PsiElement element, PsiElement position) {
    if (position.getParent() instanceof PsiReferenceExpression) {
      final PsiExpression qualifierExpression = ((PsiReferenceExpression)position.getParent()).getQualifierExpression();
      if (qualifierExpression != null) {
        final PsiType type = qualifierExpression.getType();
        if (type instanceof PsiClassType) {
          final PsiClass psiClass = ((PsiClassType)type).resolve();
          if (psiClass != null) {
            return psiClass;
          }
        }
      }
    }
    return PsiTreeUtil.getContextOfType(element, PsiClass.class, false);
  }

  private static boolean isTooGeneral(@Nullable final PsiClass element) {
    if (element == null) return true;

    @NonNls final String qname = element.getQualifiedName();
    return qname == null || qname.startsWith("java.lang.");
  }
}
