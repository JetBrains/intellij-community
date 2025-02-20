// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.core;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * Utilities related to Java PSI modifiers
 * 
 * @see PsiModifier
 * @see PsiModifierList
 */
public final class JavaPsiModifierUtil {
  
  private static final Map<String, Set<String>> ourInterfaceIncompatibleModifiers = ContainerUtil.<String, Set<String>>immutableMapBuilder()
    .put(PsiModifier.ABSTRACT, ContainerUtil.immutableSet())
    .put(PsiModifier.PACKAGE_LOCAL, ContainerUtil.immutableSet(PsiModifier.PRIVATE, PsiModifier.PUBLIC, PsiModifier.PROTECTED))
    .put(PsiModifier.PRIVATE, ContainerUtil.immutableSet(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PROTECTED))
    .put(PsiModifier.PUBLIC, ContainerUtil.immutableSet(PsiModifier.PACKAGE_LOCAL, PsiModifier.PRIVATE, PsiModifier.PROTECTED))
    .put(PsiModifier.PROTECTED, ContainerUtil.immutableSet(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PRIVATE))
    .put(PsiModifier.STRICTFP, ContainerUtil.immutableSet())
    .put(PsiModifier.STATIC, ContainerUtil.immutableSet())
    .put(PsiModifier.SEALED, ContainerUtil.immutableSet(PsiModifier.NON_SEALED))
    .put(PsiModifier.NON_SEALED, ContainerUtil.immutableSet(PsiModifier.SEALED))
    .build();
  private static final Map<String, Set<String>> ourMethodIncompatibleModifiers = ContainerUtil.<String, Set<String>>immutableMapBuilder()
    .put(PsiModifier.ABSTRACT, ContainerUtil.immutableSet(
      PsiModifier.NATIVE, PsiModifier.STATIC, PsiModifier.FINAL, PsiModifier.PRIVATE, PsiModifier.STRICTFP, PsiModifier.SYNCHRONIZED,
      PsiModifier.DEFAULT))
    .put(PsiModifier.NATIVE, ContainerUtil.immutableSet(PsiModifier.ABSTRACT, PsiModifier.STRICTFP))
    .put(PsiModifier.PACKAGE_LOCAL, ContainerUtil.immutableSet(PsiModifier.PRIVATE, PsiModifier.PUBLIC, PsiModifier.PROTECTED))
    .put(PsiModifier.PRIVATE, ContainerUtil.immutableSet(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PROTECTED))
    .put(PsiModifier.PUBLIC, ContainerUtil.immutableSet(PsiModifier.PACKAGE_LOCAL, PsiModifier.PRIVATE, PsiModifier.PROTECTED))
    .put(PsiModifier.PROTECTED, ContainerUtil.immutableSet(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PRIVATE))
    .put(PsiModifier.STATIC, ContainerUtil.immutableSet(PsiModifier.ABSTRACT, PsiModifier.DEFAULT))
    .put(PsiModifier.DEFAULT, ContainerUtil.immutableSet(PsiModifier.ABSTRACT, PsiModifier.STATIC, PsiModifier.PRIVATE))
    .put(PsiModifier.SYNCHRONIZED, ContainerUtil.immutableSet(PsiModifier.ABSTRACT))
    .put(PsiModifier.STRICTFP, ContainerUtil.immutableSet(PsiModifier.ABSTRACT))
    .put(PsiModifier.FINAL, ContainerUtil.immutableSet(PsiModifier.ABSTRACT))
    .build();
  
  private static final Map<String, Set<String>> ourFieldIncompatibleModifiers = ContainerUtil.<String, Set<String>>immutableMapBuilder()
    .put(PsiModifier.FINAL, ContainerUtil.immutableSet(PsiModifier.VOLATILE))
    .put(PsiModifier.PACKAGE_LOCAL, ContainerUtil.immutableSet(PsiModifier.PRIVATE, PsiModifier.PUBLIC, PsiModifier.PROTECTED))
    .put(PsiModifier.PRIVATE, ContainerUtil.immutableSet(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PROTECTED))
    .put(PsiModifier.PUBLIC, ContainerUtil.immutableSet(PsiModifier.PACKAGE_LOCAL, PsiModifier.PRIVATE, PsiModifier.PROTECTED))
    .put(PsiModifier.PROTECTED, ContainerUtil.immutableSet(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PRIVATE))
    .put(PsiModifier.STATIC, ContainerUtil.immutableSet())
    .put(PsiModifier.TRANSIENT, ContainerUtil.immutableSet())
    .put(PsiModifier.VOLATILE, ContainerUtil.immutableSet(PsiModifier.FINAL))
    .build();
  private static final Map<String, Set<String>> ourClassIncompatibleModifiers = ContainerUtil.<String, Set<String>>immutableMapBuilder()
    .put(PsiModifier.ABSTRACT, ContainerUtil.immutableSet(PsiModifier.FINAL))
    .put(PsiModifier.FINAL, ContainerUtil.immutableSet(PsiModifier.ABSTRACT, PsiModifier.SEALED, PsiModifier.NON_SEALED))
    .put(PsiModifier.PACKAGE_LOCAL, ContainerUtil.immutableSet(PsiModifier.PRIVATE, PsiModifier.PUBLIC, PsiModifier.PROTECTED))
    .put(PsiModifier.PRIVATE, ContainerUtil.immutableSet(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PROTECTED))
    .put(PsiModifier.PUBLIC, ContainerUtil.immutableSet(PsiModifier.PACKAGE_LOCAL, PsiModifier.PRIVATE, PsiModifier.PROTECTED))
    .put(PsiModifier.PROTECTED, ContainerUtil.immutableSet(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PRIVATE))
    .put(PsiModifier.STRICTFP, ContainerUtil.immutableSet())
    .put(PsiModifier.STATIC, ContainerUtil.immutableSet())
    .put(PsiModifier.SEALED, ContainerUtil.immutableSet(PsiModifier.FINAL, PsiModifier.NON_SEALED))
    .put(PsiModifier.NON_SEALED, ContainerUtil.immutableSet(PsiModifier.FINAL, PsiModifier.SEALED))
    .put(PsiModifier.VALUE, ContainerUtil.immutableSet())
    .build();
  private static final Map<String, Set<String>> ourClassInitializerIncompatibleModifiers =
    ContainerUtil.<String, Set<String>>immutableMapBuilder()
      .put(PsiModifier.STATIC, ContainerUtil.immutableSet())
      .build();
  private static final Map<String, Set<String>> ourModuleIncompatibleModifiers = ContainerUtil.<String, Set<String>>immutableMapBuilder()
    .put(PsiModifier.OPEN, ContainerUtil.immutableSet())
    .build();
  private static final Map<String, Set<String>> ourRequiresIncompatibleModifiers = ContainerUtil.<String, Set<String>>immutableMapBuilder()
    .put(PsiModifier.STATIC, ContainerUtil.immutableSet())
    .put(PsiModifier.TRANSITIVE, ContainerUtil.immutableSet())
    .build();

  private static String getIncompatibleModifier(@NotNull String modifier,
                                                @NotNull PsiModifierList modifierList,
                                                @NotNull Map<String, Set<String>> incompatibleModifiersHash) {
    // modifier is always incompatible with itself
    int modifierCount = 0;
    for (PsiElement otherModifier = modifierList.getFirstChild(); otherModifier != null; otherModifier = otherModifier.getNextSibling()) {
      if (modifier.equals(otherModifier.getText())) modifierCount++;
    }
    if (modifierCount > 1) return modifier;

    Set<String> incompatibles = incompatibleModifiersHash.get(modifier);
    if (incompatibles == null) return null;
    PsiElement parent = modifierList.getParent();
    boolean level8OrHigher = PsiUtil.isLanguageLevel8OrHigher(modifierList);
    boolean level9OrHigher = PsiUtil.isLanguageLevel9OrHigher(modifierList);
    for (@PsiModifier.ModifierConstant String incompatible : incompatibles) {
      if (level8OrHigher) {
        if (modifier.equals(PsiModifier.STATIC) && incompatible.equals(PsiModifier.ABSTRACT)) {
          continue;
        }
      }
      if (parent instanceof PsiMethod) {
        if (level9OrHigher && modifier.equals(PsiModifier.PRIVATE) && incompatible.equals(PsiModifier.PUBLIC)) {
          continue;
        }

        if (modifier.equals(PsiModifier.STATIC) && incompatible.equals(PsiModifier.FINAL)) {
          PsiClass containingClass = ((PsiMethod)parent).getContainingClass();
          if (containingClass == null || !containingClass.isInterface()) {
            continue;
          }
        }
      }
      if (modifierList.hasModifierProperty(incompatible)) {
        return incompatible;
      }
      if (PsiModifier.ABSTRACT.equals(incompatible) && modifierList.hasExplicitModifier(incompatible)) {
        return incompatible;
      }
    }

    return null;
  }

  /**
   * Checks if the supplied modifier list contains incompatible modifiers (e.g. "public private").
   *
   * @param modifierList a {@link PsiModifierList} to check
   * @return true if the supplied modifier list contains compatible modifiers
   */
  public static boolean isLegalModifierCombination(@NotNull PsiModifierList modifierList) {
    for (PsiElement child = modifierList.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof PsiKeyword && getIncompatibleModifier(child.getText(), modifierList) != null) {
        return false;
      }
    }
    return true;
  }

  private static Map<String, Set<String>> getIncompatibleModifierMap(@NotNull PsiElement modifierListOwner) {
    if (PsiUtilCore.hasErrorElementChild(modifierListOwner)) return null;
    if (modifierListOwner instanceof PsiClass) {
      return ((PsiClass)modifierListOwner).isInterface() ? ourInterfaceIncompatibleModifiers : ourClassIncompatibleModifiers;
    }
    if (modifierListOwner instanceof PsiMethod) return ourMethodIncompatibleModifiers;
    if (modifierListOwner instanceof PsiVariable) return ourFieldIncompatibleModifiers;
    if (modifierListOwner instanceof PsiClassInitializer) return ourClassInitializerIncompatibleModifiers;
    if (modifierListOwner instanceof PsiJavaModule) return ourModuleIncompatibleModifiers;
    if (modifierListOwner instanceof PsiRequiresStatement) return ourRequiresIncompatibleModifiers;
    return null;
  }

  /**
   * @param modifier modifier to check
   * @param modifierList modifier list to check
   * @return true if the modifier can be used in a given modifier list
   */
  public static boolean isAllowed(@NotNull String modifier, @NotNull PsiModifierList modifierList) {
    PsiElement parent = modifierList.getParent();
    if (parent == null) return false;
    Map<String, Set<String>> incompatibleModifierMap = getIncompatibleModifierMap(parent);
    return incompatibleModifierMap == null || incompatibleModifierMap.containsKey(modifier);
  }

  /**
   * @param modifier modifier to check (it does not yet belong to the modifier list supplied)
   * @param modifierList modifier list to check
   * @return a modifier that exists inside modifierList, which is incompatible with the supplied modifier
   */
  public static @Nullable String getIncompatibleModifier(@NotNull String modifier, @NotNull PsiModifierList modifierList) {
    PsiElement parent = modifierList.getParent();
    if (parent == null) return null;
    Map<String, Set<String>> incompatibleModifierMap = getIncompatibleModifierMap(parent);
    return incompatibleModifierMap == null ? null : getIncompatibleModifier(modifier, modifierList, incompatibleModifierMap);
  }

  /**
   * @param place a potential reference expression that refers to a package-private member of another package
   * @return the package-private class which prevents the place expression from being evaluated, null if nothing is found
   */
  public static @Nullable PsiClass getPackageLocalClassInTheMiddle(@NotNull PsiElement place) {
    if (place instanceof PsiReferenceExpression expression) {
      // check for package-private classes in the middle
      while (true) {
        PsiField field = ObjectUtils.tryCast(expression.resolve(), PsiField.class);
        if (field != null) {
          PsiClass aClass = field.getContainingClass();
          if (aClass != null && aClass.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) &&
              !JavaPsiFacade.getInstance(aClass.getProject()).arePackagesTheSame(aClass, place)) {
            return aClass;
          }
        }
        PsiExpression qualifier = expression.getQualifierExpression();
        if (!(qualifier instanceof PsiReferenceExpression)) break;
        expression = (PsiReferenceExpression)qualifier;
      }
    }
    return null;
  }
}
