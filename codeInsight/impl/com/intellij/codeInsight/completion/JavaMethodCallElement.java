/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.simple.PsiMethodInsertHandler;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;

/**
 * @author peter
 */
public class JavaMethodCallElement extends LookupItem<PsiMethod> {

  public JavaMethodCallElement(PsiMethod method) {
    super(method, method.getName());
    PsiType type = method.getReturnType();
    setTailType(type == PsiType.VOID ? TailType.SEMICOLON : TailType.NONE);
    setInsertHandler(new PsiMethodInsertHandler(method));
  }

}
