/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
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
  ResolveResult resolveConstructor(PsiClassType type, PsiExpressionList argumentList, PsiElement place);

  ResolveResult[] multiResolveConstructor(PsiClassType type, PsiExpressionList argumentList, PsiElement place);

  CandidateInfo[] getReferencedMethodCandidates(PsiCallExpression call, boolean dummyImplicitConstructor);

  PsiClass resolveReferencedClass(String referenceText, PsiElement context);
  PsiVariable resolveReferencedVariable(String referenceText, PsiElement context);

  boolean isAccessible(PsiMember member, PsiModifierList modifierList,
                       PsiElement place, PsiClass accessObjectClass);

  boolean isAccessible(PsiMember member, PsiElement place, PsiClass accessObjectClass);

  /**
   * @return PsiType.NULL iff no type could be inferred
   *         null         iff the type inferred is raw
   *         inferred type otherwise
   */
  public PsiType inferTypeForMethodTypeParameter(final PsiTypeParameter typeParameter, final PsiParameter[] parameters,
                                                 PsiExpression[] arguments, PsiSubstitutor partialSubstitutor, PsiElement parent);

  public PsiType getSubstitutionForTypeParameter(PsiTypeParameter typeParam,
                                                        PsiType param,
                                                        PsiType arg,
                                                        boolean isContraVariantPosition);
}
