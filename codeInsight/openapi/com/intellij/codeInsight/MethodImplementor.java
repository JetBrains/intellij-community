/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public interface MethodImplementor {
  ExtensionPointName<MethodImplementor> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.methodImplementor");

  @NotNull
  PsiMethod[] getMethodsToImplement(PsiClass aClass);

  @NotNull
  PsiMethod[] createImplementationPrototypes(final PsiClass inClass, PsiMethod method) throws IncorrectOperationException;

}
