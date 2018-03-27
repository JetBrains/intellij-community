// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.impl.source.resolve.ParameterTypeInferencePolicy;
import com.intellij.psi.infos.CandidateInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Service for resolving references to declarations.
 *
 * @see JavaPsiFacade#getResolveHelper()
 */
public interface PsiResolveHelper {
  RecursionGuard ourGuard = RecursionManager.createGuard("typeArgInference");
  RecursionGuard ourGraphGuard = RecursionManager.createGuard("graphTypeArgInference");

  class SERVICE {
    private SERVICE() { }
    public static PsiResolveHelper getInstance(Project project) {
      return ServiceManager.getService(project, PsiResolveHelper.class);
    }
  }

  /**
   * Resolves a constructor.
   * The resolved constructor is not necessarily accessible from the point of the call,
   * but accessible constructors have a priority.
   *
   * @param type              the class containing the constructor
   * @param argumentList      list of arguments of the call or new expression
   * @param place             place where constructor is invoked (used for checking access)
   * @return the result of the resolve, or {@link JavaResolveResult#EMPTY} if the resolve failed.
   */
  @NotNull
  JavaResolveResult resolveConstructor(PsiClassType type, @NotNull PsiExpressionList argumentList, PsiElement place);

  /**
   * Resolves a constructor and returns all variants for the resolve.
   * The resolved constructors are not necessarily accessible from the point of the call,
   * but accessible constructors have a priority.
   *
   * @param type              the class containing the constructor
   * @param argumentList      list of arguments of the call or new expression
   * @param place             place where constructor is invoked (used for checking access)
   * @return the result of the resolve, or {@link JavaResolveResult#EMPTY} if the resolve failed.
   */
  @NotNull
  JavaResolveResult[] multiResolveConstructor(@NotNull PsiClassType type, @NotNull PsiExpressionList argumentList, @NotNull PsiElement place);

  /**
   * Resolves a call expression and returns an array of possible resolve results.
   *
   * @param call the call expression to resolve.
   * @param dummyImplicitConstructor if true, implicit empty constructor which does not actually exist
   * can be returned as a candidate for the resolve.
   * @return the array of resolve results.
   */
  @NotNull
  CandidateInfo[] getReferencedMethodCandidates(@NotNull PsiCallExpression call, boolean dummyImplicitConstructor);

  /**
   * Resolves a call expression and returns an array of possible resolve results.
   *
   * @param call the call expression to resolve.
   * @param dummyImplicitConstructor if true, implicit empty constructor which does not actually exist
   * can be returned as a candidate for the resolve.
   * @param checkVarargs true if varargs method should lead to 2 candidates in the result array
   * @return the array of resolve results.
   */
  @NotNull
  CandidateInfo[] getReferencedMethodCandidates(@NotNull PsiCallExpression call, boolean dummyImplicitConstructor, boolean checkVarargs);

  /**
   * Resolves a reference to a class, given the text of the reference and the context
   * in which it was encountered.
   *
   * @param referenceText the text of the reference.
   * @param context       the context in which the reference is found.
   * @return the resolve result, or null if the resolve was not successful.
   */
  @Nullable
  PsiClass resolveReferencedClass(@NotNull String referenceText, PsiElement context);

  /**
   * Resolves a reference to a variable, given the text of the reference and the context
   * in which it was encountered.
   *
   * @param referenceText the text of the reference.
   * @param context       the context in which the reference is found.
   * @return the resolve result, or null if the resolve was not successful.
   */
  @Nullable
  PsiVariable resolveReferencedVariable(@NotNull String referenceText, PsiElement context);

   /**
   * Resolves a reference to a variable, given the text of the reference and the context
   * in which it was encountered.
   *
   * @param referenceText the text of the reference.
   * @param context       the context in which the reference is found.
   * @return the resolve result, or null if the resolve was not successful or resolved variable is not accessible in a given context.
   */
  @Nullable
  PsiVariable resolveAccessibleReferencedVariable(@NotNull String referenceText, PsiElement context);

  /**
   * Returns {@code true} if a member is accessible from a given place according to JLS 6.6 "Access Control".
   */
  boolean isAccessible(@NotNull PsiMember member,
                       @Nullable PsiModifierList modifierList,
                       @NotNull PsiElement place,
                       @Nullable PsiClass accessObjectClass,
                       @Nullable PsiElement currentFileResolveScope);

  /**
   * Returns {@code true} if a member is accessible from a given place according to JLS 6.6 "Access Control".
   */
  boolean isAccessible(@NotNull PsiMember member, @NotNull PsiElement place, @Nullable PsiClass accessObjectClass);

  /**
   * Returns {@code true} if a package is accessible from a given place according to JLS 6.6 "Access Control".
   */
  boolean isAccessible(@NotNull PsiPackage pkg, @NotNull PsiElement place);

  /**
   * Returns {@link PsiType#NULL} iff no type could be inferred, {@code null} iff the type inferred is raw, the inferred type otherwise.
   */
  PsiType inferTypeForMethodTypeParameter(@NotNull PsiTypeParameter typeParameter,
                                          @NotNull PsiParameter[] parameters,
                                          @NotNull PsiExpression[] arguments,
                                          @NotNull PsiSubstitutor partialSubstitutor,
                                          @Nullable PsiElement parent,
                                          @NotNull ParameterTypeInferencePolicy policy);

  @NotNull
  PsiSubstitutor inferTypeArguments(@NotNull PsiTypeParameter[] typeParameters,
                                    @NotNull PsiParameter[] parameters,
                                    @NotNull PsiExpression[] arguments,
                                    @NotNull PsiSubstitutor partialSubstitutor,
                                    @NotNull PsiElement parent,
                                    @NotNull ParameterTypeInferencePolicy policy);
  @NotNull
  PsiSubstitutor inferTypeArguments(@NotNull PsiTypeParameter[] typeParameters,
                                    @NotNull PsiParameter[] parameters,
                                    @NotNull PsiExpression[] arguments,
                                    @NotNull PsiSubstitutor partialSubstitutor,
                                    @NotNull PsiElement parent,
                                    @NotNull ParameterTypeInferencePolicy policy,
                                    @NotNull LanguageLevel languageLevel);

  @NotNull
  PsiSubstitutor inferTypeArguments(@NotNull PsiTypeParameter[] typeParameters,
                                    @NotNull PsiType[] leftTypes,
                                    @NotNull PsiType[] rightTypes,
                                    @NotNull LanguageLevel languageLevel);

  @NotNull
  default PsiSubstitutor inferTypeArguments(@NotNull PsiTypeParameter[] typeParameters,
                                            @NotNull PsiType[] leftTypes,
                                            @NotNull PsiType[] rightTypes,
                                            @NotNull PsiSubstitutor partialSubstitutor,
                                            @NotNull LanguageLevel languageLevel) {
    return inferTypeArguments(typeParameters, leftTypes, rightTypes, languageLevel);
  }

  PsiType getSubstitutionForTypeParameter(PsiTypeParameter typeParam,
                                          PsiType param,
                                          PsiType arg,
                                          boolean isContraVariantPosition,
                                          LanguageLevel languageLevel);

  @NotNull
  LanguageLevel getEffectiveLanguageLevel(@Nullable VirtualFile virtualFile);
}