// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.util.Processor;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public final class InheritanceUtil {
  private InheritanceUtil() { }

  /**
   * @param aClass     a class to check.
   * @param baseClass  supposed base class.
   * @param checkDeep  true to check deeper than aClass.super (see {@linkplain PsiClass#isInheritor(PsiClass, boolean)}).
   * @return true if aClass is the baseClass or baseClass inheritor
   */
  public static boolean isInheritorOrSelf(@Nullable PsiClass aClass, @Nullable PsiClass baseClass, boolean checkDeep) {
    if (aClass == null || baseClass == null) return false;
    PsiManager manager = aClass.getManager();
    return manager.areElementsEquivalent(baseClass, aClass) || aClass.isInheritor(baseClass, checkDeep);
  }

  public static boolean processSupers(@Nullable PsiClass aClass, boolean includeSelf, @NotNull Processor<? super PsiClass> superProcessor) {
    if (aClass == null) return true;

    if (includeSelf && !superProcessor.process(aClass)) return false;

    return processSupers(aClass, superProcessor, new HashSet<>());
  }

  private static boolean processSupers(@NotNull PsiClass aClass, @NotNull Processor<? super PsiClass> superProcessor, @NotNull Set<? super PsiClass> visited) {
    if (!visited.add(aClass)) return true;

    for (final PsiClass intf : aClass.getInterfaces()) {
      if (!superProcessor.process(intf) || !processSupers(intf, superProcessor, visited)) return false;
    }
    final PsiClass superClass = aClass.getSuperClass();
    if (superClass != null) {
      if (!superProcessor.process(superClass) || !processSupers(superClass, superProcessor, visited)) return false;
    }
    return true;
  }

  @Contract("null, _ -> false")
  public static boolean isInheritor(@Nullable PsiType type, @NotNull @NonNls final String baseClassName) {
    if (type instanceof PsiClassType) {
      PsiUtil.ensureValidType(type);
      return isInheritor(((PsiClassType)type).resolve(), baseClassName);
    }

    if (type instanceof PsiIntersectionType) {
      for (PsiType conjunct : ((PsiIntersectionType)type).getConjuncts()) {
        if (isInheritor(conjunct, baseClassName)) return true;
      }
    }

    return false;
  }

  @Contract("null, _ -> false")
  public static boolean isInheritor(@Nullable PsiClass psiClass, @NotNull @NonNls String baseClassName) {
    return isInheritor(psiClass, false, baseClassName);
  }

  @Contract("null, _, _ -> false")
  public static boolean isInheritor(@Nullable PsiClass psiClass, final boolean strict, @NotNull @NonNls String baseClassName) {
    if (psiClass == null) {
      return false;
    }

    final PsiClass base = JavaPsiFacade.getInstance(psiClass.getProject()).findClass(baseClassName, psiClass.getResolveScope());
    if (base == null) {
      return false;
    }

    return strict ? psiClass.isInheritor(base, true) : isInheritorOrSelf(psiClass, base, true);
  }

  /**
   * Gets all superclasses. Classes are added to result in DFS order
   * @param aClass
   * @param results
   * @param includeNonProject
   */
  public static void getSuperClasses(@NotNull PsiClass aClass, @NotNull Set<? super PsiClass> results, boolean includeNonProject) {
    getSuperClassesOfList(aClass.getSuperTypes(), results, includeNonProject, new HashSet<>(), aClass.getManager());
  }

  public static LinkedHashSet<PsiClass> getSuperClasses(@NotNull PsiClass aClass) {
    LinkedHashSet<PsiClass> result = new LinkedHashSet<>();
    getSuperClasses(aClass, result, true);
    return result;
  }


    private static void getSuperClassesOfList(PsiClassType @NotNull [] types,
                                              @NotNull Set<? super PsiClass> results,
                                              boolean includeNonProject,
                                              @NotNull Set<? super PsiClass> visited,
                                              @NotNull PsiManager manager) {
    for (PsiClassType type : types) {
      PsiClass resolved = type.resolve();
      if (resolved != null && visited.add(resolved)) {
        if (includeNonProject || manager.isInProject(resolved)) {
          results.add(resolved);
        }
        getSuperClassesOfList(resolved.getSuperTypes(), results, includeNonProject, visited, manager);
      }
    }
  }

  public static boolean hasEnclosingInstanceInScope(@NotNull PsiClass aClass,
                                                    PsiElement scope,
                                                    boolean isSuperClassAccepted,
                                                    boolean isTypeParamsAccepted) {
    return hasEnclosingInstanceInScope(aClass, scope, psiClass -> isSuperClassAccepted, isTypeParamsAccepted);
  }

  public static boolean hasEnclosingInstanceInScope(@NotNull PsiClass aClass,
                                                    PsiElement scope,
                                                    @NotNull Condition<? super PsiClass> isSuperClassAccepted,
                                                    boolean isTypeParamsAccepted) {
    return findEnclosingInstanceInScope(aClass, scope, isSuperClassAccepted, isTypeParamsAccepted) != null;
  }

  @Nullable
  public static PsiClass findEnclosingInstanceInScope(@NotNull PsiClass aClass,
                                                      PsiElement scope,
                                                      @NotNull Condition<? super PsiClass> isSuperClassAccepted,
                                                      boolean isTypeParamsAccepted) {
    PsiManager manager = aClass.getManager();
    PsiElement place = scope;
    while (place != null && !(place instanceof PsiFile)) {
      if (place instanceof PsiClass) {
        if (isSuperClassAccepted.value((PsiClass)place)) {
          if (isInheritorOrSelf((PsiClass)place, aClass, true)) return (PsiClass)place;
        }
        else {
          if (manager.areElementsEquivalent(place, aClass)) return aClass;
        }
        if (isTypeParamsAccepted && place instanceof PsiTypeParameter) {
          return (PsiClass)place;
        }
      }
      if (place instanceof PsiModifierListOwner) {
        final PsiModifierList modifierList = ((PsiModifierListOwner)place).getModifierList();
        if (modifierList != null && modifierList.hasModifierProperty(PsiModifier.STATIC)) {
          return null;
        }
      }
      place = place.getParent();
    }
    return null;
  }

  public static boolean processSuperTypes(@NotNull PsiType type, boolean includeSelf, @NotNull Processor<? super PsiType> processor) {
    if (includeSelf && !processor.process(type)) return false;
    return processSuperTypes(type, processor, new HashSet<>());
  }

  private static boolean processSuperTypes(PsiType type, Processor<? super PsiType> processor, Set<? super PsiType> visited) {
    if (!visited.add(type)) return true;
    for (PsiType superType : type.getSuperTypes()) {
      if (!processor.process(superType)) return false;
      processSuperTypes(superType, processor, visited);
    }
    return true;
  }

  @Nullable
  private static PsiClass getCircularClass(@NotNull PsiClass aClass, @NotNull Collection<? super PsiClass> usedClasses) {
    if (usedClasses.contains(aClass)) {
      return aClass;
    }
    try {
      usedClasses.add(aClass);
      PsiClassType[] superTypes = aClass.getSuperTypes();
      for (PsiClassType superType : superTypes) {
        PsiClass circularClass = getCircularClassInner(superType.resolve(), usedClasses);
        if (circularClass != null) return circularClass;
        for (PsiAnnotation annotation : superType.getAnnotations()) {
          circularClass = getCircularClassInner(annotation.resolveAnnotationType(), usedClasses);
          if (circularClass != null) return circularClass;
        }
      }
    }
    finally {
      usedClasses.remove(aClass);
    }
    return null;
  }

  @Nullable
  private static PsiClass getCircularClassInner(@Nullable PsiElement superType,
                                                @NotNull Collection<? super PsiClass> usedClasses) {
    while (superType instanceof PsiClass) {
      if (!CommonClassNames.JAVA_LANG_OBJECT.equals(((PsiClass)superType).getQualifiedName())) {
        PsiClass circularClass = getCircularClass((PsiClass)superType, usedClasses);
        if (circularClass != null) return circularClass;
      }
      // check class qualifier
      superType = superType.getParent();
    }
    return null;
  }

  /**
   * Detects a circular inheritance
   * @param aClass a class to check
   * @return a class which is a part of the inheritance loop; null if no circular inheritance was detected
   */
  @Nullable
  public static PsiClass getCircularClass(@NotNull PsiClass aClass) {
    return getCircularClass(aClass, new HashSet<>());
  }
}
