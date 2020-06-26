// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util;

import com.intellij.core.JavaPsiBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;


public final class VisibilityUtil  {
  @NonNls public static final String ESCALATE_VISIBILITY = "EscalateVisible";
  private static final String[] visibilityModifiers = {
    PsiModifier.PRIVATE,
    PsiModifier.PACKAGE_LOCAL,
    PsiModifier.PROTECTED,
    PsiModifier.PUBLIC
  };

  private VisibilityUtil() {
  }

  public static int compare(@PsiModifier.ModifierConstant String v1, @PsiModifier.ModifierConstant String v2) {
    return ArrayUtilRt.find(visibilityModifiers, v2) - ArrayUtilRt.find(visibilityModifiers, v1);
  }

  @PsiModifier.ModifierConstant
  public static String getHighestVisibility(@PsiModifier.ModifierConstant String v1, @PsiModifier.ModifierConstant String v2) {
    return compare(v1, v2) < 0 ? v1 : v2;
  }

  public static void escalateVisibility(PsiMember modifierListOwner, PsiElement place) throws IncorrectOperationException {
    final String visibilityModifier = getVisibilityModifier(modifierListOwner.getModifierList());
    int index;
    for (index = 0; index < visibilityModifiers.length; index++) {
      String modifier = visibilityModifiers[index];
      if(modifier.equals(visibilityModifier)) break;
    }
    for(;index < visibilityModifiers.length && !PsiUtil.isAccessible(modifierListOwner, place, null); index++) {
      @PsiModifier.ModifierConstant String modifier = visibilityModifiers[index];
      PsiUtil.setModifierProperty(modifierListOwner, modifier, true);
    }
  }

  public static void escalateVisibility(PsiModifierList modifierList, PsiElement place) throws IncorrectOperationException {
    final PsiElement parent = modifierList.getParent();
    if (parent instanceof PsiMember) {
      escalateVisibility((PsiMember)parent, place);
    }
  }

  @PsiModifier.ModifierConstant
  public static String getPossibleVisibility(final PsiMember psiMethod, final PsiElement place) {
    Project project = psiMethod.getProject();
    if (PsiUtil.isAccessible(project, psiMethod, place, null)) return getVisibilityModifier(psiMethod.getModifierList());
    if (JavaPsiFacade.getInstance(project).arePackagesTheSame(psiMethod, place)) {
      return PsiModifier.PACKAGE_LOCAL;
    }
    if (InheritanceUtil.isInheritorOrSelf(PsiTreeUtil.getParentOfType(place, PsiClass.class),
                                          psiMethod.getContainingClass(), true)) {
      return PsiModifier.PROTECTED;
    }
    return PsiModifier.PUBLIC;
  }

  @PsiModifier.ModifierConstant
  @NotNull
  public static String getVisibilityModifier(PsiModifierList list) {
    if (list == null) return PsiModifier.PACKAGE_LOCAL;
    for (@PsiModifier.ModifierConstant String modifier : visibilityModifiers) {
      if (list.hasModifierProperty(modifier)) {
        return modifier;
      }
    }
    return PsiModifier.PACKAGE_LOCAL;
  }

  @NotNull
  @NonNls
  public static String getVisibilityString(@PsiModifier.ModifierConstant @NotNull String visibilityModifier) {
    if(PsiModifier.PACKAGE_LOCAL.equals(visibilityModifier)) {
      return "";
    }
    return visibilityModifier;
  }

  @Nls
  @NotNull
  public static String getVisibilityStringToDisplay(@NotNull PsiMember member) {
    if (member.hasModifierProperty(PsiModifier.PUBLIC)) {
      return toPresentableText(PsiModifier.PUBLIC);
    }
    if (member.hasModifierProperty(PsiModifier.PROTECTED)) {
      return toPresentableText(PsiModifier.PROTECTED);
    }
    if (member.hasModifierProperty(PsiModifier.PRIVATE)) {
      return toPresentableText(PsiModifier.PRIVATE);
    }
    return toPresentableText(PsiModifier.PACKAGE_LOCAL);
  }

  @NotNull
  public static String toPresentableText(@PsiModifier.ModifierConstant @NotNull String modifier) {
    return JavaPsiBundle.visibilityPresentation(modifier);
  }

  public static void fixVisibility(PsiElement[] elements, PsiMember member, @PsiModifier.ModifierConstant String newVisibility) {
    if (newVisibility == null) return;
    if (ESCALATE_VISIBILITY.equals(newVisibility)) {
      for (PsiElement element : elements) {
        if (element != null) {
          escalateVisibility(member, element);
        }
      }
    } else {
       setVisibility(member.getModifierList(), newVisibility);
    }
  }

  public static void setVisibility(@NotNull PsiModifierList modifierList, @PsiModifier.ModifierConstant @NotNull String newVisibility) throws IncorrectOperationException {
    modifierList.setModifierProperty(newVisibility, true);
    if (newVisibility.equals(PsiModifier.PRIVATE)) {
      modifierList.setModifierProperty(PsiModifier.DEFAULT, false);
    }
    else if (newVisibility.equals(PsiModifier.PUBLIC) && !modifierList.hasExplicitModifier(PsiModifier.STATIC)) {
      PsiMethod method = ObjectUtils.tryCast(modifierList.getParent(), PsiMethod.class);
      if (method != null) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass != null && containingClass.isInterface()) {
          modifierList.setModifierProperty(PsiModifier.DEFAULT, true);
        }
      }
    }
  }

  public static void fixVisibility(PsiExpression[] expressions, PsiMember member, String newVisibility) {
    if (newVisibility == null) return;
    if (ESCALATE_VISIBILITY.equals(newVisibility)) {
      for (PsiExpression element : expressions) {
        escalateVisibility(member, element);
      }
    }
    else {
      setVisibility(member.getModifierList(), newVisibility);
    }
  }
}
