// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public final class PsiCapturedWildcardType extends PsiType.Stub {
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
    super(TypeAnnotationProvider.EMPTY);
    myExistential = existential;
    myContext = context;
    myParameter = parameter;
    myUpperBound = PsiType.getJavaLangObject(myContext.getManager(), getResolveScope());
  }

  private static final RecursionGuard<Object> guard = RecursionManager.createGuard("captureGuard");

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
      if (substitutedBoundType instanceof PsiCapturedWildcardType) {
        PsiType capturedWildcard = captureSubstitutor.substitute(typeParameter);
        if (capturedWildcard instanceof PsiCapturedWildcardType) {
          PsiType captureUpperBound = substitutedBoundType;
          while (captureUpperBound instanceof PsiCapturedWildcardType) {
            if (captureUpperBound == capturedWildcard) {
              return null;
            }
            captureUpperBound = ((PsiCapturedWildcardType)captureUpperBound).getUpperBound();
          }
        }
      }

      if (glb == null) {
        glb = substitutedBoundType;
      }
      else {
        glb = getGreatestLowerBound(glb, substitutedBoundType, wildcardType);
      }
    }

    return glb;
  }

  private static PsiType getGreatestLowerBound(PsiType glb, PsiType bound, PsiWildcardType guardObject) {
    return guard.doPreventingRecursion(guardObject, true, () -> GenericsUtil.getGreatestLowerBound(glb, bound));
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

    if (!(myContext instanceof PsiTypeParameter) &&
        !manager.areElementsEquivalent(myParameter, captured.myParameter)) {
      return false;
    }

    if (myParameter != null) {
      final Boolean sameUpperBounds = guard.doPreventingRecursion(myContext, true,
                                                                  () -> Comparing.equal(myUpperBound, captured.myUpperBound));

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
  public String getPresentableText(boolean annotated) {
    return "capture of " + myExistential.getPresentableText(annotated);
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
  public PsiType @NotNull [] getSuperTypes() {
    return myExistential.getSuperTypes();
  }

  public PsiType getLowerBound () {
    return myExistential.isSuper() ? myExistential.getBound() : NULL;
  }

  @NotNull
  public PsiType getUpperBound () {
    return getUpperBound(true);
  }

  @NotNull
  public PsiType getUpperBound(boolean capture) {
    final PsiType bound = myExistential.getBound();
    if (myExistential.isExtends() && myParameter == null) {
      assert bound != null : myExistential.getCanonicalText();
      return bound;
    }
    else {
      return isCapture() && capture ? PsiUtil.captureToplevelWildcards(myUpperBound, myContext) : myUpperBound;
    }
  }

  public void setUpperBound(@NotNull PsiType upperBound) {
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