/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.psi.scope.PsiScopeProcessor;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 09.06.2003
 * Time: 16:02:15
 * To change this template use Options | File Templates.
 */
public interface PsiJavaReference extends PsiPolyVariantReference {
  void processVariants(PsiScopeProcessor processor);

  JavaResolveResult advancedResolve(boolean incompleteCode);
  JavaResolveResult[] multiResolve(boolean incompleteCode);

}
