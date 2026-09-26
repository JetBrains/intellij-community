// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve.graphInference.constraints;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullabilitySource;
import com.intellij.codeInsight.TypeNullability;
import com.intellij.core.JavaPsiBundle;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiCapturedWildcardType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiIntersectionType;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceBound;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StrictSubtypingConstraint implements ConstraintFormula {
  private PsiType myS;
  private PsiType myT;
  private final boolean myCapture;

  //t < s
  public StrictSubtypingConstraint(PsiType t, PsiType s) {
    this(t, s, true);
  }

  //t < s
  public StrictSubtypingConstraint(PsiType t, PsiType s, boolean capture) {
    myT = t;
    myS = s;
    myCapture = capture;
  }

  @Override
  public void apply(PsiSubstitutor substitutor, boolean cache) {
    myT = substitutor.substitute(myT);
    myS = substitutor.substitute(myS);
  }


  @Override
  public boolean reduce(InferenceSession session, List<? super ConstraintFormula> constraints) {
    final HashSet<InferenceVariable> dependencies = new HashSet<>();
    final boolean reduceResult = doReduce(session, dependencies, constraints);
    if (!reduceResult) {
      session.registerIncompatibleErrorMessage(dependencies,
                                               JavaPsiBundle.message("type.conforms.to.constraint", 
                                                                     session.getPresentableText(myS), session.getPresentableText(myT)));
    }
    return reduceResult;
  }

  private boolean doReduce(InferenceSession session, Set<? super InferenceVariable> dependencies, List<? super ConstraintFormula> constraints) {
    if (!session.collectDependencies(myS, dependencies) && !session.collectDependencies(myT, dependencies)) {
      if (myT == null) return myS == null || myS.equalsToText(CommonClassNames.JAVA_LANG_OBJECT);
      if (myS == null) return true;
      return TypeConversionUtil.isAssignable(myT, myS);
    }

    if (PsiTypes.nullType().equals(myT) || myT == null) return false;
    if (PsiTypes.nullType().equals(myS)) {
      InferenceVariable inferenceVariable = session.getInferenceVariable(myT);
      if (inferenceVariable != null) {
        InferenceVariable.addBound(myT, myS, InferenceBound.LOWER, session);
      }
      return true;
    }
    else if (myS == null || myT.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
      return true;
    }

    if (PsiTypes.voidType().equals(myS) ^ PsiTypes.voidType().equals(myT)) return false;

    InferenceVariable inferenceVariable = session.getInferenceVariable(myS);
    if (inferenceVariable != null) {
      PsiType bound = adjustBoundNullity(myT, myS);
      InferenceVariable.addBound(myS, bound, InferenceBound.UPPER, session);
      recordUsageNullability(session);
      return true;
    }
    inferenceVariable = session.getInferenceVariable(myT);
    if (inferenceVariable != null) {
      PsiType bound = adjustBoundNullity(myS, myT);
      InferenceVariable.addBound(myT, bound, InferenceBound.LOWER, session);
      return true;
    }
    if (myT instanceof PsiArrayType) {
      PsiType sType = myS;
      if (myS instanceof PsiCapturedWildcardType) {
        final PsiType upperBound = ((PsiCapturedWildcardType)myS).getUpperBound();
        if (upperBound instanceof PsiArrayType) {
          sType = upperBound;
        }
      }
      if (!(sType instanceof PsiArrayType)) return false; //todo most specific array supertype
      final PsiType tComponentType = ((PsiArrayType)myT).getComponentType();
      final PsiType sComponentType = ((PsiArrayType)sType).getComponentType();
      if (!(tComponentType instanceof PsiPrimitiveType) && !(sComponentType instanceof PsiPrimitiveType)) {
        constraints.add(new StrictSubtypingConstraint(tComponentType, sComponentType, myCapture));
        return true;
      }
      return sComponentType instanceof PsiPrimitiveType && sComponentType.equals(tComponentType);
    }
    if (myT instanceof PsiClassType) {
      final PsiClassType.ClassResolveResult TResult = ((PsiClassType)myT).resolveGenerics();
      final PsiClass CClass = TResult.getElement();
      if (CClass != null) {
        if (CClass instanceof PsiTypeParameter) {
          if (myS instanceof PsiIntersectionType) {
            if (ArrayUtil.contains(myT, ((PsiIntersectionType)myS).getConjuncts())) {
              return true;
            }
          }
          final PsiType lowerBound = TypeConversionUtil.getInferredLowerBoundForSynthetic((PsiTypeParameter)CClass);
          if (lowerBound != null) {
            constraints.add(new StrictSubtypingConstraint(lowerBound, myS, myCapture));
            return true;
          }
          return false;
        }

        if (myS instanceof PsiArrayType) {
          return myT.isAssignableFrom(myS);
        }

        PsiClassType sType = getSubclassType(CClass, myS, myCapture);

        if (sType == null) return false;
        final PsiClassType.ClassResolveResult SResult = sType.resolveGenerics();
        PsiClass SClass = SResult.getElement();

        if (SClass == null) return false;

        if (((PsiClassType)myT).isRaw() || myCapture && sType.isRaw()) {
          return InheritanceUtil.isInheritorOrSelf(SClass, CClass, true);
        }

        PsiSubstitutor substitutor = SResult.getSubstitutor();
        Map<PsiTypeParameter, PsiType> map = new HashMap<>(); 
        for (PsiTypeParameter typeParameter : SClass.getTypeParameters()) {
          map.put(typeParameter, substitutor.substituteWithBoundsPromotion(typeParameter));
        }
        substitutor = substitutor.putAll(map);

        final PsiSubstitutor tSubstitutor = TResult.getSubstitutor();
        final PsiSubstitutor sSubstitutor = TypeConversionUtil.getClassSubstitutor(CClass, SClass, substitutor);
        if (sSubstitutor != null) {
          for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable(CClass)) {
            final PsiType tSubstituted = tSubstitutor.substitute(parameter);
            final PsiType sSubstituted = sSubstitutor.substitute(parameter);
            if (tSubstituted == null ^ sSubstituted == null) {
              return false;
            }
            constraints.add(new SubtypingConstraint(tSubstituted, sSubstituted));
          }
          return true;
        }
      }
      return false;
    }

    if (myT instanceof PsiIntersectionType) {
      for (PsiType conjunct : ((PsiIntersectionType)myT).getConjuncts()) {
        constraints.add(new StrictSubtypingConstraint(conjunct, myS, myCapture));
      }
      return true;
    }

    if (myT instanceof PsiCapturedWildcardType) {
      PsiType lowerBound = ((PsiCapturedWildcardType)myT).getLowerBound();
      if (lowerBound != PsiTypes.nullType()) {
        constraints.add(new StrictSubtypingConstraint(lowerBound, myS, myCapture));
      }
    }

    return true;
  }

  /**
   * {@code a <: T} with both {@code a} and {@code T} inference variables records the relation on {@code a} alone.
   * A nullness written at the usage of {@code a} is lost with it. The incorporation phase then instantiates
   * {@code T} from the instantiation of {@code a} only, and the usage never reaches {@code T}.
   * <p>
   * Such a nullness holds for every instantiation of {@code a}, because {@code @Nullable a} unions null into the
   * type. {@code T} must accept null too, so record the usage as a separate lower bound of {@code T}. The case this
   * exists for (everything in a {@code @NullMarked} scope):
   * <pre>{@code
   * class Lib {}
   *
   * static <X> void consume(X value) {}
   *
   * static <T extends @Nullable Lib> @Nullable T get(Class<T> cls) { return null; }
   *
   * void test() {
   *   // T is the non-null Lib, but the return type is @Nullable Lib, so X must be @Nullable Lib too
   *   consume(get(Lib.class));
   * }}</pre>
   * A nullness inherited from the bound of the type parameter is not recorded. It does not hold for every
   * instantiation. The plain {@code T} of a {@code T get()} declared as {@code T extends @Nullable Lib} is the
   * non-null {@code Lib} once {@code T} is instantiated with the non-null {@code Lib}.
   */
  private void recordUsageNullability(@NotNull InferenceSession session) {
    if (session.getInferenceVariable(myT) == null) return;
    TypeNullability nullability = myS.getNullability();
    if (nullability.nullability() == Nullability.NULLABLE && !(nullability.source() instanceof NullabilitySource.ExtendsBound)) {
      InferenceVariable.addBound(myT, myS, InferenceBound.LOWER, session);
    }
  }

  private static @NotNull PsiType adjustBoundNullity(@NotNull PsiType bound, @NotNull PsiType other) {
    return bound.getNullability().nullability() == Nullability.NULLABLE &&
           other.getNullability().nullability() == Nullability.NULLABLE
           ? bound.withNullability(TypeNullability.UNKNOWN)
           : bound;
  }

  public static PsiClassType getSubclassType(PsiClass containingClass, PsiType sType, boolean capture) {
    if (sType instanceof PsiIntersectionType) {
      for (PsiType conjunct : ((PsiIntersectionType)sType).getConjuncts()) {
        if (conjunct instanceof PsiClassType) {
          final PsiClassType.ClassResolveResult conjunctResult = ((PsiClassType)conjunct).resolveGenerics();
          if (InheritanceUtil.isInheritorOrSelf(conjunctResult.getElement(), containingClass, true)) {
            return  (PsiClassType)conjunct;
          }
        }
      }
    }
    else if (sType instanceof PsiClassType) {
      return  (PsiClassType)sType;
    }
    else if (sType instanceof PsiCapturedWildcardType) {
      final PsiType upperBound = ((PsiCapturedWildcardType)sType).getUpperBound(capture);
      if (upperBound instanceof PsiClassType) {
        return  (PsiClassType)upperBound;
      }
      else if (upperBound instanceof PsiIntersectionType) {
        return getSubclassType(containingClass, upperBound, capture);
      }
    }
    return null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    StrictSubtypingConstraint that = (StrictSubtypingConstraint)o;
    return ConstraintUtil.typesEqual(myS, that.myS) && ConstraintUtil.typesEqual(myT, that.myT);
  }

  @Override
  public int hashCode() {
    int result = ConstraintUtil.typeHashCode(myT);
    result = 31 * result + ConstraintUtil.typeHashCode(myS);
    return result;
  }

  @Override
  public String toString() {
    return myT.getPresentableText() + " < " + myS.getPresentableText();
  }
}
