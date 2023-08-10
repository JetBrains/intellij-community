// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source;

import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public final class JavaVarTypeUtil {
  public static PsiType getUpwardProjection(@NotNull PsiType t) {
    return t.accept(new UpwardProjectionTypeVisitor());
  }

  public static PsiType getDownwardProjection(@NotNull PsiType type) {
    return type.accept(new DownwardProjectionTypeVisitor());
  }

  private static boolean mentionsRestrictedTypeVariables(PsiType type) {
    return type.accept(new PsiTypeVisitor<Boolean>() {
      @Override
      public Boolean visitType(@NotNull PsiType type) {
        return false;
      }

      @Override
      public Boolean visitCapturedWildcardType(@NotNull PsiCapturedWildcardType capturedWildcardType) {
        return true;
      }
    });
  }

  private static class UpwardProjectionTypeVisitor extends PsiTypeVisitorEx<PsiType> {
    @Override
    public PsiType visitType(@NotNull PsiType type) {
      return type;
    }

    @Nullable
    @Override
    public PsiType visitCapturedWildcardType(@NotNull PsiCapturedWildcardType capturedWildcardType) {
      return capturedWildcardType.getUpperBound().accept(this);
    }

    @Override
    public PsiType visitArrayType(@NotNull PsiArrayType arrayType) {
      PsiType componentType = arrayType.getComponentType();
      return componentType.accept(this).createArrayType();
    }

    @Override
    public PsiType visitDisjunctionType(@NotNull PsiDisjunctionType disjunctionType) {
      return disjunctionType.getLeastUpperBound();
    }

    @Nullable
    @Override
    public PsiType visitLambdaExpressionType(@NotNull PsiLambdaExpressionType lambdaExpressionType) {
      return lambdaExpressionType;
    }

    @Override
    public PsiType visitMethodReferenceType(@NotNull PsiMethodReferenceType methodReferenceType) {
      return methodReferenceType;
    }

    @Override
    public PsiType visitIntersectionType(@NotNull PsiIntersectionType intersectionType) {
      return PsiIntersectionType.createIntersection(Arrays.stream(intersectionType.getConjuncts())
                                                      .map(conjunct -> conjunct.accept(this))
                                                      .toArray(PsiType[]::new));
    }

    @Override
    public PsiType visitClassType(@NotNull PsiClassType classType) {
      PsiClassType.ClassResolveResult result = classType.resolveGenerics();
      PsiClass aClass = result.getElement();
      if (aClass != null) {
        PsiManager manager = aClass.getManager();
        if (aClass instanceof PsiTypeParameter) {
          PsiTypeParameter typeParameter = (PsiTypeParameter)aClass;
          if (TypeConversionUtil.isFreshVariable(typeParameter)) {
            PsiType upperBound = TypeConversionUtil.getInferredUpperBoundForSynthetic(typeParameter);
            return ObjectUtils.notNull(PsiSubstitutor.EMPTY.put(typeParameter, PsiWildcardType.createUnbounded(manager)).substitute(upperBound), 
                                       classType);
          }
        }
        PsiSubstitutor targetSubstitutor = PsiSubstitutor.EMPTY;
        PsiSubstitutor substitutor = result.getSubstitutor();
        for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable(aClass)) {
          PsiType ai = substitutor.substitute(parameter);
          targetSubstitutor = targetSubstitutor.put(parameter, ai);
          if (ai != null && mentionsRestrictedTypeVariables(ai)) {
            if (ai instanceof PsiWildcardType) {

              if (((PsiWildcardType)ai).isExtends()) {
                targetSubstitutor = targetSubstitutor.put(parameter,
                                                          PsiWildcardType.createExtends(manager, ((PsiWildcardType)ai).getExtendsBound().accept(this)));
              }

              if (((PsiWildcardType)ai).isSuper()) {
                targetSubstitutor = targetSubstitutor.put(parameter, createDownwardProjection(manager, ((PsiWildcardType)ai).getSuperBound()));
              }

            }
            else {
              PsiType U = RecursionManager.doPreventingRecursion(ai, true, () -> ai.accept(this));
              if (U == null) {
                targetSubstitutor = targetSubstitutor.put(parameter, PsiWildcardType.createUnbounded(manager));
              }
              else if (!U.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) && tryUpperBound(aClass, parameter, U)) {
                targetSubstitutor = targetSubstitutor.put(parameter, PsiWildcardType.createExtends(manager, U));
              }
              else {
                targetSubstitutor = targetSubstitutor.put(parameter, createDownwardProjection(manager, ai));
              }
            }
          }
        }
        return JavaPsiFacade.getElementFactory(aClass.getProject()).createType(aClass, targetSubstitutor);
      }
      return classType;
    }

    private static PsiWildcardType createDownwardProjection(PsiManager manager, PsiType bound) {
      PsiType downwardProjection = getDownwardProjection(bound);
      return downwardProjection != PsiTypes.nullType() ? PsiWildcardType.createSuper(manager, downwardProjection)
                                                       : PsiWildcardType.createUnbounded(manager);
    }

    private static boolean tryUpperBound(PsiClass aClass, PsiTypeParameter parameter, PsiType U) {
      PsiClassType[] extendsListTypes = parameter.getExtendsListTypes();
      if (extendsListTypes.length == 0) return true;
      PsiType bi = PsiIntersectionType.createIntersection(extendsListTypes);
      return PsiTypesUtil.mentionsTypeParameters(bi, ContainerUtil.newHashSet(aClass.getTypeParameters())) ||
             !U.isAssignableFrom(bi);
    }
  }

  private static class DownwardProjectionTypeVisitor extends PsiTypeVisitor<PsiType> {
    @Override
    public PsiType visitType(@NotNull PsiType type) {
      return type;
    }

    @Override
    public PsiType visitCapturedWildcardType(@NotNull PsiCapturedWildcardType capturedWildcardType) {
      return capturedWildcardType.getLowerBound().accept(this);
    }

    @Override
    public PsiType visitArrayType(@NotNull PsiArrayType arrayType) {
      PsiType projection = arrayType.getComponentType().accept(this);
      if (projection == PsiTypes.nullType()) return PsiTypes.nullType();
      return projection.createArrayType();
    }

    @Override
    public PsiType visitIntersectionType(@NotNull PsiIntersectionType intersectionType) {
      PsiType[] conjuncts = Arrays.stream(intersectionType.getConjuncts()).map(conjunct -> conjunct.accept(this)).toArray(PsiType[]::new);
      if (ArrayUtil.find(conjuncts, PsiTypes.nullType()) > -1) return PsiTypes.nullType();
      return PsiIntersectionType.createIntersection(conjuncts);
    }

    @Override
    public PsiType visitLambdaExpressionType(@NotNull PsiLambdaExpressionType lambdaExpressionType) {
      return lambdaExpressionType;
    }

    @Override
    public PsiType visitMethodReferenceType(@NotNull PsiMethodReferenceType methodReferenceType) {
      return methodReferenceType;
    }

    @Override
    public PsiType visitClassType(@NotNull PsiClassType classType) {
      PsiClassType.ClassResolveResult result = classType.resolveGenerics();
      PsiClass aClass = result.getElement();
      if (aClass != null) {
        PsiSubstitutor substitutor = result.getSubstitutor();
        PsiSubstitutor targetSubstitutor = PsiSubstitutor.EMPTY;
        for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable(aClass)) {
          PsiType ai = substitutor.substitute(parameter);
          if (ai == null) return PsiTypes.nullType();
          if (!mentionsRestrictedTypeVariables(ai)) {
            targetSubstitutor = targetSubstitutor.put(parameter, ai);
          }
          else if (ai instanceof PsiWildcardType) {
            if (((PsiWildcardType)ai).isExtends()) {
              PsiType extendsBound = ((PsiWildcardType)ai).getExtendsBound();
              PsiType projection = extendsBound.accept(this);
              if (projection == PsiTypes.nullType()) return PsiTypes.nullType();
              targetSubstitutor = targetSubstitutor.put(parameter, PsiWildcardType.createExtends(parameter.getManager(), projection));
            }
            else if (((PsiWildcardType)ai).isSuper()) {
              PsiType superBound = ((PsiWildcardType)ai).getSuperBound();
              targetSubstitutor = targetSubstitutor.put(parameter, getUpwardProjection(superBound));
            }
            else {
              return PsiTypes.nullType();
            }
          }
          else {
            return PsiTypes.nullType();
          }
        }
        return JavaPsiFacade.getElementFactory(aClass.getProject()).createType(aClass, targetSubstitutor);
      }
      return PsiTypes.nullType();
    }
  }
}
