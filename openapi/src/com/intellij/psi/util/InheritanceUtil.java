/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;

public class InheritanceUtil {
  private static final Key HASH_PROVIDER_KEY = Key.create("Hash provider for isInheritor");
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.util.InheritanceUtil");

  /** 
   * @deprecated Use {@link PsiClass#isInheritor(com.intellij.psi.PsiClass, boolean)} instead.
   */
  public static boolean isInheritor(PsiClass candidateClass, PsiClass baseClass, boolean checkDeep) {
    return candidateClass.isInheritor(baseClass, checkDeep);
  }

  /**
   * @return true if aClass is the baseClass or baseClass inheritor
   */
  public static boolean isInheritorOrSelf(PsiClass aClass, PsiClass baseClass, boolean checkDeep) { //TODO: remove this method!!
    if (aClass == null || baseClass == null) return false;
    PsiManager manager = aClass.getManager();
    return manager.areElementsEquivalent(baseClass, aClass) || aClass.isInheritor(baseClass, checkDeep);
  }

  /**
   * @return true if aClass is the baseClass or baseClass inheritor
   */
  public static boolean isCorrectDescendant(PsiClass aClass, PsiClass baseClass, boolean checkDeep) {
    if (aClass == null || baseClass == null) return false;
    PsiManager manager = aClass.getManager();
    return manager.areElementsEquivalent(baseClass, aClass) || aClass.isInheritor(baseClass, checkDeep);
  }
}
