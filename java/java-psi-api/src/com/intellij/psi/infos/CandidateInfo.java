/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.psi.infos;

import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * @author ik,dsl
 */
public class CandidateInfo implements JavaResolveResult {
  public static final CandidateInfo[] EMPTY_ARRAY = new CandidateInfo[0];
  public static final JavaResolveResult[] RESOLVE_RESULT_FOR_PACKAGE_PREFIX_PACKAGE =
    {new CandidateInfo(PsiUtilCore.NULL_PSI_ELEMENT, PsiSubstitutor.EMPTY, Boolean.TRUE, false, null, null, null, true)};

  private final PsiElement myPlace;
  private final PsiClass myAccessClass;
  private final PsiElement myCandidate;
  private final boolean myStaticsProblem;
  protected final PsiSubstitutor mySubstitutor;
  private final PsiElement myCurrentFileResolveContext;
  private final boolean myPackagePrefixPackageReference;
  private Boolean myAccessible; // benign data race

  private CandidateInfo(@NotNull PsiElement candidate,
                        @NotNull PsiSubstitutor substitutor,
                        Boolean accessible,
                        boolean staticsProblem,
                        PsiElement currFileContext,
                        PsiElement place,
                        PsiClass accessClass,
                        boolean packagePrefixPackageReference) {
    myCandidate = candidate;
    myAccessible = accessible;
    myStaticsProblem = staticsProblem;
    mySubstitutor = substitutor;
    myCurrentFileResolveContext = currFileContext;
    myAccessClass = accessClass;
    myPlace = place;
    myPackagePrefixPackageReference = packagePrefixPackageReference;
  }

  public CandidateInfo(@NotNull PsiElement candidate,
                       @NotNull PsiSubstitutor substitutor,
                       boolean accessProblem,
                       boolean staticsProblem,
                       PsiElement currFileContext) {
    this(candidate, substitutor, !accessProblem, staticsProblem, currFileContext, null, null, false);
  }

  public CandidateInfo(@NotNull PsiElement candidate, @NotNull PsiSubstitutor substitutor, boolean accessProblem, boolean staticsProblem) {
    this(candidate, substitutor, accessProblem, staticsProblem, null);
  }

  public CandidateInfo(@NotNull PsiElement candidate,
                       @NotNull PsiSubstitutor substitutor,
                       PsiElement place,
                       PsiClass accessClass,
                       boolean staticsProblem,
                       PsiElement currFileContext) {
    this(candidate, substitutor, null, staticsProblem, currFileContext, place, accessClass, false);
  }

  public CandidateInfo(@NotNull PsiElement candidate, @NotNull PsiSubstitutor substitutor, PsiElement place, boolean staticsProblem) {
    this(candidate, substitutor, place, null, staticsProblem, null);
  }

  public CandidateInfo(@NotNull PsiElement candidate, @NotNull PsiSubstitutor substitutor) {
    this(candidate, substitutor, null, null, false, null);
  }

  public CandidateInfo(@NotNull CandidateInfo ci, @NotNull PsiSubstitutor newSubstitutor) {
    this(ci.myCandidate, newSubstitutor, ci.myAccessible, ci.myStaticsProblem, ci.myCurrentFileResolveContext, ci.myPlace, null, false);
  }

  @Override
  public boolean isValidResult() {
    return isAccessible() && isStaticsScopeCorrect();
  }

  @Override
  public boolean isPackagePrefixPackageReference() {
    return myPackagePrefixPackageReference;
  }

  @NotNull
  @Override
  public PsiElement getElement() {
    return myCandidate;
  }

  @NotNull
  @Override
  public PsiSubstitutor getSubstitutor() {
    return mySubstitutor;
  }

  @Override
  public boolean isAccessible() {
    Boolean accessible = myAccessible;

    if (accessible == null) {
      accessible = true;

      if (myPlace != null) {
        PsiResolveHelper helper = JavaPsiFacade.getInstance(myPlace.getProject()).getResolveHelper();

        if (myCandidate instanceof PsiMember) {
          PsiMember member = (PsiMember)myCandidate;
          accessible = helper.isAccessible(member, member.getModifierList(), myPlace, myAccessClass, myCurrentFileResolveContext);

          if (accessible &&
              member.hasModifierProperty(PsiModifier.PRIVATE) &&
              myPlace instanceof PsiReferenceExpression &&
              JavaVersionService.getInstance().isAtLeast(myPlace, JavaSdkVersion.JDK_1_7)) {
            accessible = !isAccessedThroughTypeParameterBound();
          }
        }
      }

      myAccessible = accessible;
    }

    return accessible;
  }

  private boolean isAccessedThroughTypeParameterBound() {
    final PsiExpression qualifierExpression = ((PsiReferenceExpression)myPlace).getQualifierExpression();
    if (qualifierExpression instanceof PsiMethodCallExpression) {
      final JavaResolveResult resolveResult = ((PsiMethodCallExpression)qualifierExpression).resolveMethodGenerics();
      final PsiElement element = resolveResult.getElement();
      if (element instanceof PsiMethod) {
        final PsiType returnType = ((PsiMethod)element).getReturnType();
        final PsiType substitutedReturnType = resolveResult.getSubstitutor().substitute(returnType);
        if (substitutedReturnType instanceof PsiCapturedWildcardType || substitutedReturnType instanceof PsiWildcardType) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public boolean isStaticsScopeCorrect() {
    return !myStaticsProblem;
  }

  @Override
  public PsiElement getCurrentFileResolveScope() {
    return myCurrentFileResolveContext;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final CandidateInfo that = (CandidateInfo)o;

    if (myPackagePrefixPackageReference != that.myPackagePrefixPackageReference) return false;
    if (myStaticsProblem != that.myStaticsProblem) return false;
    if (!Objects.equals(myAccessClass, that.myAccessClass)) return false;
    if (isAccessible() != that.isAccessible()) return false;
    if (!myCandidate.equals(that.myCandidate)) return false;
    if (!Objects.equals(myCurrentFileResolveContext, that.myCurrentFileResolveContext)) return false;
    if (!Objects.equals(myPlace, that.myPlace)) return false;
    return mySubstitutor.equals(that.mySubstitutor);
  }

  @Override
  public int hashCode() {
    int result = Objects.hashCode(myPlace);
    result = 31 * result + Objects.hashCode(myAccessClass);
    result = 31 * result + myCandidate.hashCode();
    result = 31 * result + (isAccessible() ? 1 : 0);
    result = 31 * result + (myStaticsProblem ? 1 : 0);
    result = 31 * result + mySubstitutor.hashCode();
    result = 31 * result + Objects.hashCode(myCurrentFileResolveContext);
    result = 31 * result + (myPackagePrefixPackageReference ? 1 : 0);
    return result;
  }
}