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

import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.ProximityLocation;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.NotNullFunction;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * @author peter
*/
public class JavaInheritanceWeigher extends ProximityWeigher {
  private static final NotNullLazyKey<Set<String>, ProximityLocation> PLACE_SUPER_CLASSES = NotNullLazyKey.create("PLACE_SUPER_CLASSES", new NotNullFunction<ProximityLocation, Set<String>>() {
    @NotNull
    @Override
    public Set<String> fun(ProximityLocation location) {
      final HashSet<String> result = new HashSet<>();
      PsiClass contextClass = PsiTreeUtil.getContextOfType(location.getPosition(), PsiClass.class, false);
      Processor<PsiClass> processor = psiClass -> {
        ContainerUtilRt.addIfNotNull(result, psiClass.getQualifiedName());
        return true;
      };
      while (contextClass != null) {
        InheritanceUtil.processSupers(contextClass, true, processor);
        contextClass = contextClass.getContainingClass();
      }
      return result;
    }
  });

  @Override
  public Comparable weigh(@NotNull final PsiElement element, @NotNull final ProximityLocation location) {
    if (location.getPosition() == null || !(element instanceof PsiClass)) {
      return null;
    }
    if (isTooGeneral((PsiClass)element)) return false;

    Set<String> superClasses = PLACE_SUPER_CLASSES.getValue(location);
    if (superClasses.isEmpty()) {
      return false;
    }

    final PsiElement position = location.getPosition();
    PsiClass placeClass = findPlaceClass(element, position);
    if (placeClass == null) return false;

    PsiClass elementClass = placeClass;
    while (elementClass != null) {
      if (superClasses.contains(elementClass.getQualifiedName())) {
        return true;
      }
      elementClass = elementClass.getContainingClass();
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
