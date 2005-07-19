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

import com.intellij.psi.infos.CandidateInfo;

public interface PsiResolveHelper {
  /**
   * Resolves a constructor.
   * The resolved constructor is not neccessary accessible from the point of the call,
   * but accessible constructors have a priority.
   *
   * @param type              the class containing the constructor
   * @param argumentList      list of arguments of the call or new expression
   * @param place             place where constructor is invoked (used for checking access)
   */
  JavaResolveResult resolveConstructor(PsiClassType type, PsiExpressionList argumentList, PsiElement place);

  JavaResolveResult[] multiResolveConstructor(PsiClassType type, PsiExpressionList argumentList, PsiElement place);

  CandidateInfo[] getReferencedMethodCandidates(PsiCallExpression call, boolean dummyImplicitConstructor);

  PsiClass resolveReferencedClass(String referenceText, PsiElement context);
  PsiVariable resolveReferencedVariable(String referenceText, PsiElement context);

  boolean isAccessible(PsiMember member, PsiModifierList modifierList,
                       PsiElement place, PsiClass accessObjectClass, final PsiElement currentFileResolveScope);

  boolean isAccessible(PsiMember member, PsiElement place, PsiClass accessObjectClass);

  /**
   * @return PsiType.NULL iff no type could be inferred
   *         null         iff the type inferred is raw
   *         inferred type otherwise
   */
  PsiType inferTypeForMethodTypeParameter(final PsiTypeParameter typeParameter,
                                          final PsiParameter[] parameters,
                                          PsiExpression[] arguments,
                                          PsiSubstitutor partialSubstitutor,
                                          PsiElement parent,
                                          final boolean forCompletion);

  PsiType getSubstitutionForTypeParameter(PsiTypeParameter typeParam,
                                                        PsiType param,
                                                        PsiType arg,
                                                        boolean isContraVariantPosition);
}
