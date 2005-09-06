/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.pom.java.PomMethod;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface PsiMethod extends PsiMember, PsiNamedElement, PsiModifierListOwner, PsiDocCommentOwner, PsiTypeParameterListOwner {
  PsiMethod[] EMPTY_ARRAY = new PsiMethod[0];

  /**
   * Returns the return type of the method.
   * @return the method return type, or null if the method is a constructor.
   */
  @Nullable PsiType getReturnType();

  PsiTypeElement getReturnTypeElement();

  @NotNull PsiParameterList getParameterList();

  PsiReferenceList getThrowsList();

  PsiCodeBlock getBody();

  boolean isConstructor();

  boolean isVarArgs();

  MethodSignature getSignature(PsiSubstitutor substitutor);

  PsiIdentifier getNameIdentifier();

  @NotNull PsiMethod[] findSuperMethods();

  PsiMethod[] findSuperMethods(boolean checkAccess);

  PsiMethod[] findSuperMethods(PsiClass parentClass);

  List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(boolean checkAccess);

  PsiMethod findDeepestSuperMethod();

  PomMethod getPom();

  @NotNull PsiModifierList getModifierList();
}
