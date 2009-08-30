/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.util.proximity;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.ProximityLocation;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class KnownPackageWeigher extends ProximityWeigher {

  public Comparable weigh(@NotNull final PsiElement element, final ProximityLocation location) {
    if (element instanceof PsiClass) {
      @NonNls final String qname = ((PsiClass)element).getQualifiedName();
      if (qname != null) {
        if (qname.startsWith("java.")) return 2;
        if (qname.startsWith("javax.")) return 1;
      }
    }
    return 0;
  }
}