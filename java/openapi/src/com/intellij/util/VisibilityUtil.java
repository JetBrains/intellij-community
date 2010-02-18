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

/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 07.06.2002
 * Time: 18:48:01
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.util;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;


public class VisibilityUtil  {
  @NonNls public static final String ESCALATE_VISIBILITY = "EscalateVisible";
  private static final String[] visibilityModifiers = {
    PsiModifier.PRIVATE,
    PsiModifier.PACKAGE_LOCAL,
    PsiModifier.PROTECTED,
    PsiModifier.PUBLIC
  };

  private VisibilityUtil() {
  }

  public static int compare(@Modifier String v1, @Modifier String v2) {
    return ArrayUtil.find(visibilityModifiers, v2) - ArrayUtil.find(visibilityModifiers, v1);
  }

  @Modifier
  public static String getHighestVisibility(@Modifier String v1, @Modifier String v2) {
    if(v1.equals(v2)) return v1;

    if(PsiModifier.PRIVATE.equals(v1)) return v2;
    if(PsiModifier.PUBLIC.equals(v1)) return PsiModifier.PUBLIC;
    if(PsiModifier.PRIVATE.equals(v2)) return v1;

    return PsiModifier.PUBLIC;
  }

  public static void escalateVisibility(PsiMember modifierListOwner, PsiElement place) throws IncorrectOperationException {
    final String visibilityModifier = getVisibilityModifier(modifierListOwner.getModifierList());
    int index;
    for (index = 0; index < visibilityModifiers.length; index++) {
      String modifier = visibilityModifiers[index];
      if(modifier.equals(visibilityModifier)) break;
    }
    for(;index < visibilityModifiers.length && !PsiUtil.isAccessible(modifierListOwner, place, null); index++) {
      @Modifier String modifier = visibilityModifiers[index];
      PsiUtil.setModifierProperty(modifierListOwner, modifier, true);
    }
  }


  @Modifier
  public static String getPossibleVisibility(final PsiMember psiMethod, final PsiElement place) {
    if (PsiUtil.isAccessible(psiMethod, place, null)) return getVisibilityModifier(psiMethod.getModifierList());
    if (JavaPsiFacade.getInstance(psiMethod.getProject()).arePackagesTheSame(psiMethod, place)) {
      return PsiModifier.PACKAGE_LOCAL;
    }
    if (InheritanceUtil.isInheritorOrSelf(PsiTreeUtil.getParentOfType(place, PsiClass.class),
                                          psiMethod.getContainingClass(), true)) {
      return PsiModifier.PROTECTED;
    }
    return PsiModifier.PUBLIC;
  }

  @Modifier
  public static String getVisibilityModifier(PsiModifierList list) {
    if (list == null) return PsiModifier.PACKAGE_LOCAL;
    for (@Modifier String modifier : visibilityModifiers) {
      if (list.hasModifierProperty(modifier)) {
        return modifier;
      }
    }
    return PsiModifier.PACKAGE_LOCAL;
  }

  public static String getVisibilityString(@Modifier String visibilityModifier) {
    if(PsiModifier.PACKAGE_LOCAL.equals(visibilityModifier)) {
      return "";
    }
    return visibilityModifier;
  }

  @Nls
  public static String getVisibilityStringToDisplay(PsiMember member) {
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

  public static String toPresentableText(@Modifier String modifier) {
    return PsiBundle.visibilityPresentation(modifier);
  }

  public static void fixVisibility(UsageInfo[] usageInfos, PsiMember member, final String newVisibility) {
    if (newVisibility == null) return;
    if (ESCALATE_VISIBILITY.equals(newVisibility)) {
      for (UsageInfo info : usageInfos) {
        final PsiElement element = info.getElement();
        if (element != null) {
          escalateVisibility(member, element);
        }
      }
    } else {
       setVisibility(member.getModifierList(), newVisibility);
    }
  }

  public static void setVisibility(PsiModifierList modifierList, @Modifier String newVisibility) throws IncorrectOperationException {
    modifierList.setModifierProperty(PsiModifier.PRIVATE, false);
    modifierList.setModifierProperty(PsiModifier.PUBLIC, false);
    modifierList.setModifierProperty(PsiModifier.PROTECTED, false);
    modifierList.setModifierProperty(newVisibility, true);
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
