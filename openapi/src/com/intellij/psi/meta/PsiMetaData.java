/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.meta;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.scope.PsiScopeProcessor;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 05.05.2003
 * Time: 2:17:49
 * To change this template use Options | File Templates.
 */
public interface PsiMetaData{
  PsiElement getDeclaration();
  boolean processDeclarations(PsiElement context, PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastElement, PsiElement place);
  String getName(PsiElement context);
  String getName();

  void init(PsiElement element);
  /**
   * @return objects this meta data depends on.
   * @see com.intellij.psi.util.CachedValue
   */
  Object[] getDependences();
}
