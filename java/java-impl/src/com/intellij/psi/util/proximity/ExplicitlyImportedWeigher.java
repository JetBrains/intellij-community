/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.util.proximity;

import com.intellij.psi.*;
import com.intellij.psi.util.ProximityLocation;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class ExplicitlyImportedWeigher extends ProximityWeigher {

  public Comparable weigh(@NotNull final PsiElement element, final ProximityLocation location) {
    if (element instanceof PsiClass) {
      final String qname = ((PsiClass) element).getQualifiedName();
      if (qname != null) {
        final PsiJavaFile psiJavaFile = PsiTreeUtil.getContextOfType(location.getPosition(), PsiJavaFile.class, false);
        if (psiJavaFile != null) {
          final PsiImportList importList = psiJavaFile.getImportList();
          if (importList != null) {
            for (final PsiImportStatement importStatement : importList.getImportStatements()) {
              if (!importStatement.isOnDemand() && qname.equals(importStatement.getQualifiedName())) {
                return true;
              }
            }
          }
        }
      }
    }
    return false;
  }
}
