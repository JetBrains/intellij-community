/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.util;

import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.util.containers.HashSet;

import java.util.Set;

public class TypesDistinctProver {
  public static final Set<String> ARRAY_SUPER_CLASSES = new HashSet<>();
  static {
    ARRAY_SUPER_CLASSES.add(CommonClassNames.JAVA_IO_SERIALIZABLE);
    ARRAY_SUPER_CLASSES.add(CommonClassNames.JAVA_LANG_CLONEABLE);
    ARRAY_SUPER_CLASSES.add(CommonClassNames.JAVA_LANG_OBJECT);
  }

  private TypesDistinctProver() {
  }

  public static boolean provablyDistinct(PsiType type1, PsiType type2) {
    return provablyDistinct(type1, type2, 0);
  }

  protected static boolean provablyDistinct(PsiType type1, PsiType type2, int level) {
    if (type1 instanceof PsiWildcardType) {
      if (type2 instanceof PsiWildcardType) {
        return provablyDistinct((PsiWildcardType)type1, (PsiWildcardType)type2, true, level);
      }

      if (level > 1) return true;
      if (type2 instanceof PsiCapturedWildcardType) {
        return provablyDistinct((PsiWildcardType)type1, ((PsiCapturedWildcardType)type2).getWildcard(), false, level);
      }

      if (type2 instanceof PsiClassType) {
        final PsiClass psiClass2 = PsiUtil.resolveClassInType(type2);
        if (psiClass2 == null) return false;

        if (((PsiWildcardType)type1).isExtends()) {
          final PsiType extendsBound = ((PsiWildcardType)type1).getExtendsBound();
          if (extendsBound instanceof PsiArrayType &&
              proveArrayTypeDistinct((PsiArrayType)extendsBound, type2)) return true;
          final PsiClass boundClass1 = PsiUtil.resolveClassInType(extendsBound);
          if (boundClass1 == null) return false;

          if (CommonClassNames.JAVA_LANG_OBJECT.equals(psiClass2.getQualifiedName()) && !(boundClass1 instanceof PsiTypeParameter)) {
            return !CommonClassNames.JAVA_LANG_OBJECT.equals(boundClass1.getQualifiedName());
          }

          return proveExtendsBoundsDistinct(type1, type2, boundClass1, psiClass2);
        }

        if (((PsiWildcardType)type1).isSuper()) {
          final PsiType superBound = ((PsiWildcardType)type1).getSuperBound();
          if (superBound instanceof PsiArrayType &&
              proveArrayTypeDistinct((PsiArrayType)superBound, type2)) return true;

          final PsiClass boundClass1 = PsiUtil.resolveClassInType(superBound);
          if (boundClass1 == null) return false;
          if (boundClass1 instanceof PsiTypeParameter) {
            final PsiClassType[] extendsListTypes = boundClass1.getExtendsListTypes();
            for (PsiClassType classType : extendsListTypes) {
              final PsiClass psiClass = classType.resolve();
              if (InheritanceUtil.isInheritorOrSelf(psiClass, psiClass2, true) || InheritanceUtil.isInheritorOrSelf(psiClass2, psiClass, true)) return false;
            }
            return extendsListTypes.length > 0;
          }

          return !InheritanceUtil.isInheritorOrSelf(boundClass1, psiClass2, true);
        }

        final PsiType bound = ((PsiWildcardType)type1).getBound();
        return bound != null && !bound.equals(type2);
      }
      
      if (type2 instanceof PsiArrayType) {
        return proveArrayTypeDistinct((PsiArrayType)type2, type1);
      }
    } else {

      if (type2 instanceof PsiWildcardType) return provablyDistinct(type2, type1, level);

      if (type1 instanceof PsiCapturedWildcardType) return provablyDistinct(((PsiCapturedWildcardType)type1).getWildcard(), type2, level);
      if (type2 instanceof PsiCapturedWildcardType) return provablyDistinct(type2, type1, level);
    }


    final PsiClassType.ClassResolveResult classResolveResult1 = PsiUtil.resolveGenericsClassInType(type1);
    final PsiClassType.ClassResolveResult classResolveResult2 = PsiUtil.resolveGenericsClassInType(type2);

    final PsiClass boundClass1 = classResolveResult1.getElement();
    final PsiClass boundClass2 = classResolveResult2.getElement();

    if (boundClass1 instanceof PsiTypeParameter && level < 2) {
      if (!distinguishFromTypeParam((PsiTypeParameter)boundClass1, boundClass2, type1, type2)) return false;
    }

    if (boundClass2 instanceof PsiTypeParameter && level < 2) {
      if (!distinguishFromTypeParam((PsiTypeParameter)boundClass2, boundClass1, type2, type1)) return false;
    }

    if (Comparing.equal(TypeConversionUtil.erasure(type1), TypeConversionUtil.erasure(type2))) {
      final PsiSubstitutor substitutor1 = classResolveResult1.getSubstitutor();
      final PsiSubstitutor substitutor2 = classResolveResult2.getSubstitutor();
      for (PsiTypeParameter parameter : substitutor1.getSubstitutionMap().keySet()) {
        final PsiType substitutedType1 = substitutor1.substitute(parameter);
        final PsiType substitutedType2 = substitutor2.substitute(parameter);
        if (substitutedType1 == null && substitutedType2 == null){
          continue;
        }

        if (substitutedType1 == null) {
          if (type2 instanceof PsiClassType && ((PsiClassType)type2).hasParameters()) return true;
        }
        else if (substitutedType2 == null) {
          if (type1 instanceof PsiClassType && ((PsiClassType)type1).hasParameters()) return true;
        } else {
          if (provablyDistinct(substitutedType1, substitutedType2, level + 1)) return true;
        }
      }
      if (level < 2) return false;
    }

    if (boundClass1 == null || boundClass2 == null) {
      return type1 != null && type2 != null && !type1.equals(type2);
    }

    return type2 != null && type1 != null && !type1.equals(type2) &&
           !(level == 0 && boundClass1.isInterface() && boundClass2.isInterface()) &&
           (!InheritanceUtil.isInheritorOrSelf(boundClass1, boundClass2, true) ||
            !InheritanceUtil.isInheritorOrSelf(boundClass2, boundClass1, true));
  }

  private static boolean distinguishFromTypeParam(PsiTypeParameter typeParam,
                                                  PsiClass boundClass,
                                                  PsiType type1,
                                                  PsiType type2) {
    final PsiClassType[] paramBounds = typeParam.getExtendsListTypes();
    if (paramBounds.length == 0 && type1 instanceof PsiClassType) return false;
    for (PsiClassType classType : paramBounds) {
      final PsiClass paramBound = classType.resolve();
      if (paramBound != null &&
          (InheritanceUtil.isInheritorOrSelf(paramBound, boundClass, true) ||
           InheritanceUtil.isInheritorOrSelf(boundClass, paramBound, true))) {
        return false;
      }
      if (type2 instanceof PsiArrayType && TypeConversionUtil.isAssignable(classType, type2)) {
        return false;
      }
    }
    return true;
  }

  public static boolean provablyDistinct(PsiWildcardType type1, PsiWildcardType type2, boolean rejectInconsistentRaw, int level) {
    if (type1.isSuper() && type2.isSuper()) return false;
    if (type1.isExtends() && type2.isExtends()) {
      final PsiType extendsBound1 = type1.getExtendsBound();
      final PsiType extendsBound2 = type2.getExtendsBound();
      if (extendsBound1 instanceof PsiArrayType && proveArrayTypeDistinct((PsiArrayType)extendsBound1, extendsBound2) ||
          extendsBound2 instanceof PsiArrayType && proveArrayTypeDistinct((PsiArrayType)extendsBound2, extendsBound1)) return true;

      final PsiClass boundClass1 = PsiUtil.resolveClassInType(extendsBound1);
      final PsiClass boundClass2 = PsiUtil.resolveClassInType(extendsBound2);
      if (boundClass1 != null && boundClass2 != null) {
        if (rejectInconsistentRaw && level > 0 &&
            extendsBound1 instanceof PsiClassType && extendsBound2 instanceof PsiClassType && 
            (((PsiClassType)extendsBound1).isRaw() ^ ((PsiClassType)extendsBound2).isRaw())) return true;
        return proveExtendsBoundsDistinct(type1, type2, boundClass1, boundClass2);
      }
      return provablyDistinct(extendsBound1, extendsBound2, 1);
    }
    if (type2.isExtends()) return provablyDistinct(type2, type1, rejectInconsistentRaw, level);
    if (type1.isExtends() && !type2.isBounded() && level > 1) return PsiUtil.resolveClassInType(type1.getExtendsBound()) instanceof PsiTypeParameter;
    if (type1.isExtends() && type2.isSuper()) {
      final PsiType extendsBound = type1.getExtendsBound();
      final PsiType superBound = type2.getSuperBound();
      if (extendsBound instanceof PsiArrayType && proveArrayTypeDistinct((PsiArrayType)extendsBound, superBound) ||
          superBound instanceof PsiArrayType && proveArrayTypeDistinct((PsiArrayType)superBound, extendsBound)) return true;

      final PsiClass extendsBoundClass = PsiUtil.resolveClassInType(extendsBound);
      final PsiClass superBoundClass = PsiUtil.resolveClassInType(superBound);
      if (extendsBoundClass != null && superBoundClass != null) {
        if (extendsBoundClass instanceof PsiTypeParameter) {
          return try2ProveTypeParameterDistinct(type2, extendsBoundClass);
        }
        if (superBoundClass instanceof PsiTypeParameter) return false;
        return !InheritanceUtil.isInheritorOrSelf(superBoundClass, extendsBoundClass, true);
      }
      return provablyDistinct(extendsBound, superBound);
    }

    if (!type1.isBounded() || !type2.isBounded()) {
      return false;
    }
    return !type1.equals(type2);
  }

  public static boolean proveExtendsBoundsDistinct(PsiType type1,
                                                    PsiType type2,
                                                    PsiClass boundClass1,
                                                    PsiClass boundClass2) {
    if (boundClass1 == null || boundClass2 == null) {
      return false;
    }
    if (boundClass1.isInterface() && boundClass2.isInterface()) return false;
    if (boundClass1.isInterface()) {
      return !(boundClass2.hasModifierProperty(PsiModifier.FINAL) ? InheritanceUtil.isInheritorOrSelf(boundClass2, boundClass1, true) : true);
    }
    if (boundClass2.isInterface()) {
      return !(boundClass1.hasModifierProperty(PsiModifier.FINAL) ? InheritanceUtil.isInheritorOrSelf(boundClass1, boundClass2, true) : true);
    }

    if (boundClass1 instanceof PsiTypeParameter) {
      return try2ProveTypeParameterDistinct(type2, boundClass1);
    }

    if (boundClass2 instanceof PsiTypeParameter) {
      return try2ProveTypeParameterDistinct(type1, boundClass2);
    }

    return !InheritanceUtil.isInheritorOrSelf(boundClass1, boundClass2, true) && !InheritanceUtil.isInheritorOrSelf(boundClass2, boundClass1, true);
  }

  public static boolean try2ProveTypeParameterDistinct(PsiType type, PsiClass typeParameter) {
    final PsiClassType[] types = typeParameter.getExtendsListTypes();
    if (types.length == 0) return false;
    return provablyDistinct(PsiWildcardType.createExtends(typeParameter.getManager(), types[0]), type);
  }

  public static boolean proveArrayTypeDistinct(PsiArrayType type, PsiType bound) {
    if (type.getArrayDimensions() == bound.getArrayDimensions()) {
      final PsiType componentType = type.getComponentType();
      final PsiType boundComponentType = ((PsiArrayType)bound).getComponentType();
      if (boundComponentType instanceof PsiClassType && componentType instanceof PsiClassType) {
        return proveExtendsBoundsDistinct(boundComponentType, componentType, ((PsiClassType)boundComponentType).resolve(), ((PsiClassType)componentType).resolve());
      }
      else {
        return !bound.equals(type);
      }
    }
    else if (bound.getArrayDimensions() + 1 == type.getArrayDimensions() && bound.getDeepComponentType() instanceof PsiClassType) {
      return !isSuperClassOfArrayType(((PsiClassType)bound.getDeepComponentType()).resolve());
    }
    else if (bound.getArrayDimensions() == type.getArrayDimensions() + 1 && type.getDeepComponentType() instanceof PsiClassType) {
      return !isSuperClassOfArrayType(((PsiClassType)type.getDeepComponentType()).resolve());
    }
    else if (bound instanceof PsiClassType) {
      return !isSuperClassOfArrayType(((PsiClassType)bound).resolve());
    }
    else if (bound instanceof PsiWildcardType) {
      final PsiType boundBound = ((PsiWildcardType)bound).getBound();
      if (boundBound != null && !boundBound.equals(type)) {
        if (boundBound instanceof PsiArrayType && !((PsiWildcardType)bound).isSuper()) {
          return proveArrayTypeDistinct(type, boundBound);
        }
        final PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(boundBound);
        if (psiClass == null) {
          return true;
        }
        if (psiClass instanceof PsiTypeParameter) {
          return try2ProveTypeParameterDistinct(type, psiClass);
        }
        return !(((PsiWildcardType)bound).isExtends() && isSuperClassOfArrayType(psiClass));
      }
      return false;
    }
    else if (bound instanceof PsiIntersectionType) {
      for (PsiType conjunctBound : ((PsiIntersectionType)bound).getConjuncts()) {
        if (!proveArrayTypeDistinct(type, conjunctBound)) return false;
      }
    }
    else if (bound instanceof PsiCapturedWildcardType) {
      return proveArrayTypeDistinct(type, ((PsiCapturedWildcardType)bound).getWildcard());
    }
    return true;
  }

  private static boolean isSuperClassOfArrayType(PsiClass psiClass) {
    if (psiClass != null) {
      final String qualifiedName = psiClass.getQualifiedName();
      return qualifiedName != null && ARRAY_SUPER_CLASSES.contains(qualifiedName);
    }
    return false;
  }
}
