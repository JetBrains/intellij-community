// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.psi.PsiModifier.ABSTRACT;
import static com.intellij.psi.PsiModifier.SEALED;

/**
 * Utility methods related to sealed types
 */
public final class JavaPsiSealedUtil {
  /**
   * @param psiClass class to check
   * @return true if the class is abstract and sealed
   */
  public static boolean isAbstractSealed(@Nullable PsiClass psiClass) {
    return psiClass != null && isSealed(psiClass) && psiClass.hasModifierProperty(ABSTRACT);
  }

  private static boolean isSealed(@Nullable PsiClass psiClass) {
    return psiClass != null && (psiClass.hasModifierProperty(SEALED) || psiClass.getPermitsList() != null);
  }

  /**
   * @param psiClass PSI class to get a collection of direct permitted classes for
   * @return all permitted classes of psiClass. Note that if there are inheritors which are erroneously not listed
   * in the permit list, they won't be returned.
   */
  public static @NotNull Collection<PsiClass> getPermittedClasses(@NotNull PsiClass psiClass) {
    return CachedValuesManager.getCachedValue(psiClass, () -> {
      PsiReferenceList permitsList = psiClass.getPermitsList();
      Collection<PsiClass> results;
      if (permitsList == null) {
        results = SyntaxTraverser.psiTraverser(psiClass.getContainingFile())
          .filter(PsiClass.class)
          //local classes and anonymous classes must not extend sealed
          .filter(cls -> !(cls instanceof PsiAnonymousClass || PsiUtil.isLocalClass(cls)))
          .filter(cls -> cls.isInheritor(psiClass, false))
          .toList();
      }
      else {
        results = Stream.of(permitsList.getReferencedTypes())
          .map(type -> type.resolve()).filter(Objects::nonNull)
          .collect(Collectors.toCollection(LinkedHashSet::new));
      }
      return CachedValueProvider.Result.create(results, PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  /**
   * Finds permitted classes recursively: if a permitted class is an abstract sealed class, 
   * its permitted classes are also returned. The main purpose of this method is to build a list
   * of classes that needs to be covered by switch on a sealed class.
   * 
   * @param psiClass PSI class to find all (direct and indirect) permitted classes for
   * @return all (direct and indirect) permitted classes for
   */
  public static Set<PsiClass> getAllPermittedClasses(@NotNull PsiClass psiClass) {
    return CachedValuesManager.getCachedValue(psiClass, () -> {
      Set<PsiClass> result = new HashSet<>();
      Set<PsiClass> visitedClasses = new HashSet<>();
      Queue<PsiClass> notVisitedClasses = new LinkedList<>();
      notVisitedClasses.add(psiClass);
      while (!notVisitedClasses.isEmpty()) {
        PsiClass notVisitedClass = notVisitedClasses.poll();
        if (!isAbstractSealed(notVisitedClass) || visitedClasses.contains(notVisitedClass)) continue;
        visitedClasses.add(notVisitedClass);
        Collection<PsiClass> permittedClasses = getPermittedClasses(notVisitedClass);
        result.addAll(permittedClasses);
        notVisitedClasses.addAll(permittedClasses);
      }
      return CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  /**
   * Builds a set of abstract sealed classes, which are direct or indirect superclasses of supplied classes.
   * 
   * @param classes set of classes from sealed hierarchy
   * @return set of abstract sealed classes, which are direct or indirect superclasses of supplied classes
   */
  public static Set<PsiClass> findSealedUpperClasses(Set<PsiClass> classes) {
    HashSet<PsiClass> sealedUpperClasses = new HashSet<>();
    Set<PsiClass> visited = new HashSet<>();
    Queue<PsiClass> nonVisited = new ArrayDeque<>(classes);
    while (!nonVisited.isEmpty()) {
      PsiClass polled = nonVisited.poll();
      if (!visited.add(polled)) {
        continue;
      }
      PsiClassType[] types = polled.getSuperTypes();
      for (PsiClassType type : types) {
        PsiClass superClass = PsiUtil.resolveClassInClassTypeOnly(TypeConversionUtil.erasure(type));
        if (isAbstractSealed(superClass)) {
          nonVisited.add(superClass);
          sealedUpperClasses.add(superClass);
        }
      }
    }
    return sealedUpperClasses;
  }
}
