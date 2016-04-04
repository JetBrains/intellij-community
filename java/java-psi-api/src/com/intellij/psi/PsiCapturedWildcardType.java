/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public class PsiCapturedWildcardType extends PsiType.Stub {
  @NotNull private final PsiWildcardType myExistential;
  @NotNull private final PsiElement myContext;
  @Nullable private final PsiTypeParameter myParameter;

  private PsiType myUpperBound;

  @NotNull
  public static PsiCapturedWildcardType create(@NotNull PsiWildcardType existential, @NotNull PsiElement context) {
    return create(existential, context, null);
  }

  @NotNull
  public static PsiCapturedWildcardType create(@NotNull PsiWildcardType existential,
                                               @NotNull PsiElement context,
                                               @Nullable PsiTypeParameter parameter) {
    return new PsiCapturedWildcardType(existential, context, parameter);
  }

  private PsiCapturedWildcardType(@NotNull PsiWildcardType existential,
                                  @NotNull PsiElement context,
                                  @Nullable PsiTypeParameter parameter) {
    super(PsiAnnotation.EMPTY_ARRAY);
    myExistential = existential;
    myContext = context;
    myParameter = parameter;
    myUpperBound = PsiType.getJavaLangObject(myContext.getManager(), getResolveScope());
  }

  public static RecursionGuard guard = RecursionManager.createGuard("captureGuard");

  public static boolean isCapture() {
    return guard.currentStack().isEmpty();
  }

  @Nullable
  public static PsiType captureUpperBound(@NotNull PsiTypeParameter typeParameter,
                                          @NotNull PsiWildcardType wildcardType,
                                          @NotNull PsiSubstitutor captureSubstitutor) {
    final PsiType[] boundTypes = typeParameter.getExtendsListTypes();
    PsiType originalBound = !wildcardType.isSuper() ? wildcardType.getBound() : null;
    PsiType glb = originalBound;
    for (PsiType boundType : boundTypes) {
      final PsiType substitutedBoundType = captureSubstitutor.substitute(boundType);
      //glb for array types is not specified yet
      if (originalBound instanceof PsiArrayType &&
          substitutedBoundType instanceof PsiArrayType &&
          !originalBound.isAssignableFrom(substitutedBoundType) &&
          !substitutedBoundType.isAssignableFrom(originalBound)) {
        continue;
      }

      if (glb == null) {
        glb = substitutedBoundType;
      }
      else {
        glb = GenericsUtil.getGreatestLowerBound(glb, substitutedBoundType);
      }
    }

    return glb;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof PsiCapturedWildcardType)) {
      return false;
    }

    final PsiCapturedWildcardType captured = (PsiCapturedWildcardType)o;
    final PsiManager manager = myContext.getManager();
    if (!manager.areElementsEquivalent(myContext, captured.myContext)) {
      return false;
    }

    if ((myExistential.isSuper() || captured.myExistential.isSuper()) && !myExistential.equals(captured.myExistential)) {
      return false;
    }

    if ((myContext instanceof PsiReferenceExpression || myContext instanceof PsiMethodCallExpression) && 
        !manager.areElementsEquivalent(myParameter, captured.myParameter)) {
      return false;
    }

    if (myParameter != null) {
      final Boolean sameUpperBounds = guard.doPreventingRecursion(myContext, true, new Computable<Boolean>() {
        @Override
        public Boolean compute() {
          return Comparing.equal(myUpperBound, captured.myUpperBound);
        }
      });

      if (sameUpperBounds == null || sameUpperBounds) {
        return true;
      }
    }
    return myExistential.equals(captured.myExistential);
  }

  @Override
  public int hashCode() {
    return myUpperBound.hashCode() + 31 * myContext.hashCode();
  }

  @NotNull
  @Override
  public String getPresentableText() {
    return "capture of " + myExistential.getPresentableText();
  }

  @NotNull
  @Override
  public String getCanonicalText(boolean annotated) {
    return myExistential.getCanonicalText(annotated);
  }

  @NotNull
  @Override
  public String getInternalCanonicalText() {
    return "capture<" + myExistential.getInternalCanonicalText() + '>';
  }

  @Override
  public boolean isValid() {
    return myExistential.isValid() && myContext.isValid();
  }

  @Override
  public boolean equalsToText(@NotNull String text) {
    return false;
  }

  @Override
  public <A> A accept(@NotNull PsiTypeVisitor<A> visitor) {
    return visitor.visitCapturedWildcardType(this);
  }

  @NotNull
  @Override
  public GlobalSearchScope getResolveScope() {
    return myExistential.getResolveScope();
  }

  @Override
  @NotNull
  public PsiType[] getSuperTypes() {
    return myExistential.getSuperTypes();
  }

  public PsiType getLowerBound () {
    return myExistential.isSuper() ? myExistential.getBound() : NULL;
  }

  public PsiType getUpperBound () {
    final PsiType bound = myExistential.getBound();
    if (myExistential.isExtends() && myParameter == null) {
      return bound;
    }
    else {
      return myUpperBound;
    }
  }

  public void setUpperBound(PsiType upperBound) {
    myUpperBound = upperBound;
  }

  @NotNull
  public PsiWildcardType getWildcard() {
    return myExistential;
  }

  @NotNull
  public PsiElement getContext() {
    return myContext;
  }

  public PsiTypeParameter getTypeParameter() {
    return myParameter;
  }
}
