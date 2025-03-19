// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.core.JavaPsiBundle;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightRecordCanonicalConstructor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Java access modifiers: public, protected, private, package-local
 */
public enum AccessModifier {
  PUBLIC(PsiModifier.PUBLIC), PROTECTED(PsiModifier.PROTECTED), PACKAGE_LOCAL(PsiModifier.PACKAGE_LOCAL), PRIVATE(PsiModifier.PRIVATE);
  public static final @Unmodifiable List<AccessModifier> ALL_MODIFIERS = Arrays.asList(values());

  private static final @Unmodifiable List<AccessModifier> PUBLIC_PACKAGE = Arrays.asList(PUBLIC, PACKAGE_LOCAL);
  private static final @Unmodifiable List<AccessModifier> PUBLIC_PRIVATE = Arrays.asList(PUBLIC, PRIVATE);

  @PsiModifier.ModifierConstant private final @NotNull String myModifier;

  AccessModifier(@PsiModifier.ModifierConstant @NotNull String modifier) {
    myModifier = modifier;
  }

  /**
   * @return a {@link PsiModifier} string constant which corresponds to this access modifier.
   */
  @PsiModifier.ModifierConstant
  public @NotNull String toPsiModifier() {
    return myModifier;
  }

  /**
   * Checks whether given modifier owner has this access modifier (probably implicit)
   * @param owner element to check (e.g. class member)
   * @return true if it has current modifier
   */
  public boolean hasModifier(@NotNull PsiModifierListOwner owner) {
    return owner.hasModifierProperty(toPsiModifier());
  }

  /**
   * Returns an {@link AccessModifier} which corresponds to the given keyword;
   * null if supplied keyword is null or don't correspond to access modifier
   * @param keyword keyword to convert to access modifier
   * @return a corresponding access modifier
   */
  @Contract(value = "null -> null", pure = true)
  public static @Nullable AccessModifier fromKeyword(@Nullable PsiKeyword keyword) {
    return keyword == null ? null : fromPsiModifier(keyword.getText());
  }

  /**
   * Returns an {@link AccessModifier} which corresponds to the given String constant declared in
   * {@link PsiModifier} class.
   * @param modifier a modifier string
   * @return an access modifier or null if supplied string doesn't correspond to any access modifier.
   */
  @Contract(value = "null -> null", pure = true)
  public static @Nullable AccessModifier fromPsiModifier(@Nullable String modifier) {
    if (modifier == null) return null;
    switch (modifier) {
      case PsiModifier.PUBLIC:
        return PUBLIC;
      case PsiModifier.PROTECTED:
        return PROTECTED;
      case PsiModifier.PACKAGE_LOCAL:
        return PACKAGE_LOCAL;
      case PsiModifier.PRIVATE:
        return PRIVATE;
      default:
        return null;
    }
  }

  public static AccessModifier fromModifierList(@NotNull PsiModifierList modifierList) {
    if (modifierList.hasModifierProperty(PsiModifier.PRIVATE)) {
      return PRIVATE;
    }
    if (modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
      return PACKAGE_LOCAL;
    }
    if (modifierList.hasModifierProperty(PsiModifier.PROTECTED)) {
      return PROTECTED;
    }
    return PUBLIC;
  }

  public boolean isWeaker(@NotNull AccessModifier other) {
    return compareTo(other) < 0;
  }

  @Override
  public @Nls String toString() {
    return JavaPsiBundle.visibilityPresentation(toPsiModifier());
  }

  public static @NotNull @Unmodifiable List<AccessModifier> getAvailableModifiers(@NotNull PsiMember member) {
    PsiClass containingClass = member.getContainingClass();
    if (member instanceof PsiField) {
      if (member instanceof PsiEnumConstant || containingClass == null || containingClass.isInterface()) return Collections.emptyList();
      return ALL_MODIFIERS;
    }
    if (member instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)member;
      if (containingClass == null || containingClass.isEnum() && method.isConstructor()) return Collections.emptyList();
      if (JavaPsiRecordUtil.getRecordComponentForAccessor(method) != null) {
        return Collections.singletonList(PUBLIC);
      }
      if (JavaPsiRecordUtil.isCompactConstructor(method) ||
          JavaPsiRecordUtil.isExplicitCanonicalConstructor(method) ||
          method instanceof LightRecordCanonicalConstructor) {
        PsiModifierList list = containingClass.getModifierList();
        if (list != null) {
          AccessModifier classModifier = fromModifierList(list);
          return ContainerUtil.filter(ALL_MODIFIERS, m -> !classModifier.isWeaker(m));
        }
        return Collections.singletonList(PUBLIC);
      }
      if (containingClass.isInterface()) {
        if (method.getBody() != null && PsiUtil.isLanguageLevel9OrHigher(member)) {
          return PUBLIC_PRIVATE;
        }
        return Collections.singletonList(PUBLIC);
      }
      AccessModifier minAccess = getMinAccess(method);
      if (minAccess != PRIVATE) {
        return ContainerUtil.filter(ALL_MODIFIERS, mod -> mod.compareTo(minAccess) <= 0);
      }
      return ALL_MODIFIERS;
    }
    if (member instanceof PsiClass) {
      if (PsiUtil.isLocalOrAnonymousClass((PsiClass)member)) return Collections.emptyList();
      if (containingClass == null) return PUBLIC_PACKAGE;
      return ALL_MODIFIERS;
    }
    return Collections.emptyList();
  }

  private static @NotNull AccessModifier getMinAccess(PsiMethod method) {
    if (method.isConstructor() || method.hasModifierProperty(PsiModifier.STATIC)) return PRIVATE;
    HierarchicalMethodSignature signature = method.getHierarchicalMethodSignature();
    AccessModifier lowest = PRIVATE;
    for (HierarchicalMethodSignature superSignature : signature.getSuperSignatures()) {
      PsiMethod superMethod = superSignature.getMethod();
      AccessModifier current = fromModifierList(superMethod.getModifierList());
      if (!current.isWeaker(lowest)) continue;
      if (method.hasModifierProperty(PsiModifier.ABSTRACT) && !MethodSignatureUtil.isSuperMethod(superMethod, method)) continue;
      if (!PsiUtil.isAccessible(method.getProject(), superMethod, method, null)) continue;
      lowest = current;
      if (lowest == PUBLIC) {
        break;
      }
    }
    return lowest;
  }
}
