// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util.proximity;

import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.ProximityLocation;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public final class JavaInheritanceWeigher extends ProximityWeigher {
  private static final NotNullLazyKey<Set<String>, ProximityLocation> PLACE_SUPER_CLASSES = NotNullLazyKey.createLazyKey("PLACE_SUPER_CLASSES",
                                                                                                                  location -> {
                                                                                                                    final HashSet<String> result = new HashSet<>();
                                                                                                                    PsiClass contextClass = PsiTreeUtil.getContextOfType(location.getPosition(), PsiClass.class, false);
                                                                                                                    Processor<PsiClass> processor = psiClass -> {
                                                                                                                      ContainerUtil.addIfNotNull(result, psiClass.getQualifiedName());
                                                                                                                      return true;
                                                                                                                    };
                                                                                                                    while (contextClass != null) {
                                                                                                                      InheritanceUtil.processSupers(contextClass, true, processor);
                                                                                                                      contextClass = contextClass.getContainingClass();
                                                                                                                    }
                                                                                                                    return result;
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
