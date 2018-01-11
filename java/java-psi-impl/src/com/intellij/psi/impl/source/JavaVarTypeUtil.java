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
package com.intellij.psi.impl.source;

import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class JavaVarTypeUtil {
  public static final RecursionGuard ourVarGuard = RecursionManager.createGuard("var.guard");

  public static PsiType getUpwardProjection(@NotNull PsiType t) {
    return t.accept(new UpwardProjectionTypeVisitor());
  }

  public static PsiType getDownwardProjection(@NotNull PsiType type) {
    return type.accept(new DownwardProjectionTypeVisitor());
  }
  
  private static boolean mentionsRestrictedTypeVariables(PsiType type) {
    return type.accept(new PsiTypeVisitor<Boolean>() {
      @Override
      public Boolean visitType(PsiType type) {
        return false;
      }

      @Override
      public Boolean visitCapturedWildcardType(PsiCapturedWildcardType capturedWildcardType) {
        return true;
      }
    });
  }

  private static class UpwardProjectionTypeVisitor extends PsiTypeVisitorEx<PsiType> {
    private static RecursionGuard upwardGuard = RecursionManager.createGuard("upwardProjectionGuard");
    @Override
    public PsiType visitType(PsiType type) {
      return type;
    }

    @Nullable
    @Override
    public PsiType visitCapturedWildcardType(PsiCapturedWildcardType capturedWildcardType) {
      return capturedWildcardType.getUpperBound().accept(this);
    }

    @Override
    public PsiType visitArrayType(PsiArrayType arrayType) {
      PsiType componentType = arrayType.getComponentType();
      return componentType.accept(this).createArrayType();
    }

    @Nullable
    @Override
    public PsiType visitLambdaExpressionType(PsiLambdaExpressionType lambdaExpressionType) {
      return lambdaExpressionType;
    }

    @Override
    public PsiType visitMethodReferenceType(PsiMethodReferenceType methodReferenceType) {
      return methodReferenceType;
    }

    @Override
    public PsiType visitIntersectionType(PsiIntersectionType intersectionType) {
      return PsiIntersectionType.createIntersection(Arrays.stream(intersectionType.getConjuncts())
                                                      .map(conjunct -> conjunct.accept(this))
                                                      .toArray(PsiType[]::new));
    }

    @Override
    public PsiType visitClassType(PsiClassType classType) {
      PsiClassType.ClassResolveResult result = classType.resolveGenerics();
      PsiClass aClass = result.getElement();
      if (aClass != null) {
        PsiManager manager = aClass.getManager();
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
              PsiType U = upwardGuard.doPreventingRecursion(ai, true, () -> ai.accept(this));
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
        return JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createType(aClass, targetSubstitutor);
      }
      return classType;
    }

    private static PsiWildcardType createDownwardProjection(PsiManager manager, PsiType bound) {
      PsiType downwardProjection = getDownwardProjection(bound);
      return downwardProjection != PsiType.NULL ? PsiWildcardType.createSuper(manager, downwardProjection)
                                                : PsiWildcardType.createUnbounded(manager);
    }

    private static boolean tryUpperBound(PsiClass aClass, PsiTypeParameter parameter, PsiType U) {
      PsiClassType[] extendsListTypes = parameter.getExtendsListTypes();
      if (extendsListTypes.length == 0) return true;
      PsiType bi = PsiIntersectionType.createIntersection(extendsListTypes);
      return PsiPolyExpressionUtil.mentionsTypeParameters(bi, ContainerUtil.newHashSet(aClass.getTypeParameters())) ||
             !U.isAssignableFrom(bi);
    }
  }

  private static class DownwardProjectionTypeVisitor extends PsiTypeVisitor<PsiType> {
    @Override
    public PsiType visitType(PsiType type) {
      return type;
    }

    @Override
    public PsiType visitCapturedWildcardType(PsiCapturedWildcardType capturedWildcardType) {
      return capturedWildcardType.getLowerBound().accept(this);
    }

    @Override
    public PsiType visitArrayType(PsiArrayType arrayType) {
      PsiType projection = arrayType.getComponentType().accept(this);
      if (projection == PsiType.NULL) return PsiType.NULL;
      return projection.createArrayType();
    }

    @Override
    public PsiType visitIntersectionType(PsiIntersectionType intersectionType) {
      PsiType[] conjuncts = Arrays.stream(intersectionType.getConjuncts()).map(conjunct -> conjunct.accept(this)).toArray(PsiType[]::new);
      if (ArrayUtil.find(conjuncts, PsiType.NULL) > -1) return PsiType.NULL;
      return PsiIntersectionType.createIntersection(conjuncts);
    }

    @Override
    public PsiType visitLambdaExpressionType(PsiLambdaExpressionType lambdaExpressionType) {
      return lambdaExpressionType;
    }

    @Override
    public PsiType visitMethodReferenceType(PsiMethodReferenceType methodReferenceType) {
      return methodReferenceType;
    }

    @Override
    public PsiType visitClassType(PsiClassType classType) {
      PsiClassType.ClassResolveResult result = classType.resolveGenerics();
      PsiClass aClass = result.getElement();
      if (aClass != null) {
        PsiSubstitutor substitutor = result.getSubstitutor();
        PsiSubstitutor targetSubstitutor = PsiSubstitutor.EMPTY;
        for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable(aClass)) {
          PsiType ai = substitutor.substitute(parameter);
          if (ai == null) return PsiType.NULL;
          if (!mentionsRestrictedTypeVariables(ai)) {
            targetSubstitutor = targetSubstitutor.put(parameter, ai);
          }
          else if (ai instanceof PsiWildcardType) {
            if (((PsiWildcardType)ai).isExtends()) {
              PsiType extendsBound = ((PsiWildcardType)ai).getExtendsBound();
              PsiType projection = extendsBound.accept(this);
              if (projection == PsiType.NULL) return PsiType.NULL;
              targetSubstitutor = targetSubstitutor.put(parameter, PsiWildcardType.createExtends(parameter.getManager(), projection));
            }
            else if (((PsiWildcardType)ai).isSuper()) {
              PsiType superBound = ((PsiWildcardType)ai).getSuperBound();
              targetSubstitutor = targetSubstitutor.put(parameter, getUpwardProjection(superBound));
            }
            else {
              return PsiType.NULL;
            }
          }
          else {
            return PsiType.NULL;
          }
        }
        return JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createType(aClass, targetSubstitutor);
      }
      return PsiType.NULL;
    }
  }
}
