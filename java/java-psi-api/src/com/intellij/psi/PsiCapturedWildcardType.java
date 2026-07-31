// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullabilityAnnotationInfo;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.TypeNullability;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PsiCapturedWildcardType extends PsiType.Stub {
  private final @NotNull PsiWildcardType myExistential;
  private final @NotNull PsiElement myContext;
  private final @Nullable PsiTypeParameter myParameter;
  private @Nullable TypeNullability myNullability;

  private PsiType myUpperBound;

  public static @NotNull PsiCapturedWildcardType create(@NotNull PsiWildcardType existential, @NotNull PsiElement context) {
    return create(existential, context, null);
  }

  public static @NotNull PsiCapturedWildcardType create(@NotNull PsiWildcardType existential,
                                                        @NotNull PsiElement context,
                                                        @Nullable PsiTypeParameter parameter) {
    return new PsiCapturedWildcardType(existential, context, parameter, null);
  }

  private PsiCapturedWildcardType(@NotNull PsiWildcardType existential,
                                  @NotNull PsiElement context,
                                  @Nullable PsiTypeParameter parameter, 
                                  @Nullable TypeNullability nullability) {
    super(TypeAnnotationProvider.EMPTY);
    myExistential = existential;
    myContext = context;
    myParameter = parameter;
    myUpperBound = getJavaLangObject(myContext.getManager(), getResolveScope());
    myNullability = nullability;
  }

  private static final RecursionGuard<Object> guard = RecursionManager.createGuard("captureGuard");

  public static boolean isCapture() {
    return guard.currentStack().isEmpty();
  }

  public static @Nullable PsiType captureUpperBound(@NotNull PsiTypeParameter typeParameter,
                                                    @NotNull PsiWildcardType wildcardType,
                                                    @NotNull PsiSubstitutor captureSubstitutor) {
    final PsiType[] boundTypes = typeParameter.getExtendsListTypes();
    PsiType originalBound = !wildcardType.isSuper() ? wildcardType.getBound() : null;
    PsiType glb = originalBound;
    for (PsiType boundType : boundTypes) {
      final PsiType substitutedBoundType = captureSubstitutor.substituteIgnoringNullability(boundType);
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
  public @NotNull TypeNullability getNullability() {
    if (myNullability == null) {
      TypeNullability nullability = TypeNullability.UNKNOWN;
      if (myExistential.isExtends()) {
        nullability = myExistential.getNullability();
      } else {
        NullableNotNullManager manager = NullableNotNullManager.getInstance(myContext.getProject());
        if (manager != null) {
          NullabilityAnnotationInfo defaultNullability = manager.findDefaultTypeUseNullability(myContext);
          if (defaultNullability != null && defaultNullability.getNullability() == Nullability.NOT_NULL) {
            nullability = getUpperBound().getNullability().inherited();
          }
        }
      }
      myNullability = nullability;
    }
    return myNullability;
  }

  /**
   * {@link #getNullability()} reports the nullability written on the wildcard itself, while capture conversion
   * intersects the wildcard bound with the declared bound of the captured type parameter. The two differ exactly when
   * the declared bound is more specific, so all three captures below read the same {@code ? extends @Nullable Lib} and
   * yet have three different nullabilities (everything in a {@code @NullMarked} scope):
   * <pre>{@code
   * interface Lib {}
   * interface NullableBounded<T extends @Nullable Object> {}
   * interface UnspecBounded<T extends @NullnessUnspecified Object> {}
   * interface NotNullBounded<T extends Object> {}
   *
   * void nullable(NullableBounded<? extends @Nullable Lib> x) {} // capture is nullable
   * void unspec(UnspecBounded<? extends @Nullable Lib> x) {}     // capture is unspecified
   * void notNull(NotNullBounded<? extends @Nullable Lib> x) {}   // capture is not-null
   * }</pre>
   * {@link #getNullability()} answers "nullable" for all three, because it only looks at the wildcard. It also reports
   * that nullability as {@link com.intellij.codeInsight.NullabilitySource.ExtendsBound inherited from a bound} (see
   * {@link PsiWildcardType#getNullability()}), which downstream means "an upper estimate, not a fact about the value" and
   * is therefore ignored by {@link TypeNullability#instantiatedWith}. A caller that needs the nullability of the capture
   * as an actual type -- notably substitution into a type-variable usage -- must use this method instead.
   *
   * @return the nullability of this capture after capture conversion
   */
  public @NotNull TypeNullability getCaptureConvertedNullability() {
    if (!myExistential.isExtends()) return getNullability();
    TypeNullability fromWildcard = getNullability().uninherited();
    return myParameter == null ? fromWildcard : fromWildcard.meet(TypeNullability.ofTypeParameter(myParameter).uninherited());
  }

  @Override
  public @NotNull PsiType withNullability(@NotNull TypeNullability nullability) {
    PsiCapturedWildcardType type = new PsiCapturedWildcardType(myExistential, myContext, myParameter, nullability);
    type.setUpperBound(myUpperBound);
    return type;
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

  @Override
  public @NotNull String getPresentableText(boolean annotated) {
    return "capture of " + myExistential.getPresentableText(annotated);
  }

  @Override
  public @NotNull String getCanonicalText(boolean annotated) {
    return myExistential.getCanonicalText(annotated);
  }

  @Override
  public @NotNull String getInternalCanonicalText() {
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

  @Override
  public @NotNull GlobalSearchScope getResolveScope() {
    return myExistential.getResolveScope();
  }

  @Override
  public PsiType @NotNull [] getSuperTypes() {
    return myExistential.getSuperTypes();
  }

  public PsiType getLowerBound () {
    return myExistential.isSuper() ? myExistential.getBound() : (PsiPrimitiveType)PsiTypes.nullType();
  }

  public @NotNull PsiType getUpperBound () {
    return getUpperBound(true);
  }

  public @NotNull PsiType getUpperBound(boolean capture) {
    final PsiType bound = myExistential.getBound();
    if (myExistential.isExtends() && myParameter == null) {
      assert bound != null : myExistential.getCanonicalText();
      return bound;
    }
    else {
      PsiType type = isCapture() && capture ? PsiUtil.captureToplevelWildcards(myUpperBound, myContext) : myUpperBound;
      if (bound != null) {
        type = type.withNullability(type.getNullability().meet(bound.getNullability()));
      }
      return type;
    }
  }

  public void setUpperBound(@NotNull PsiType upperBound) {
    myUpperBound = upperBound;
  }

  public @NotNull PsiWildcardType getWildcard() {
    return myExistential;
  }

  public @NotNull PsiElement getContext() {
    return myContext;
  }

  public PsiTypeParameter getTypeParameter() {
    return myParameter;
  }
}