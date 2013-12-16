/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.HashSet;

import java.util.Set;

/**
 * User: anna
 * Date: Aug 12, 2010
 */
public class TypesDistinctProver {
  private TypesDistinctProver() {
  }

  public static boolean provablyDistinct(PsiType type1, PsiType type2) {
    return provablyDistinct(type1, type2, 0);
  }

  protected static boolean provablyDistinct(PsiType type1, PsiType type2, int level) {
    if (type1 instanceof PsiWildcardType) {
      if (type2 instanceof PsiWildcardType) {
        return provablyDistinct((PsiWildcardType)type1, (PsiWildcardType)type2, true);
      }

      if (level > 1) return true;
      if (type2 instanceof PsiCapturedWildcardType) {
        return provablyDistinct((PsiWildcardType)type1, ((PsiCapturedWildcardType)type2).getWildcard(), false);
      }

      if (type2 instanceof PsiClassType) {
        final PsiClass psiClass2 = PsiUtil.resolveClassInType(type2);
        if (psiClass2 == null) return false;

        if (((PsiWildcardType)type1).isExtends()) {
          final PsiType extendsBound = ((PsiWildcardType)type1).getExtendsBound();
          if (extendsBound instanceof PsiArrayType &&
              proveArrayTypeDistinct(((PsiWildcardType)type1).getManager().getProject(), (PsiArrayType)extendsBound, type2)) return true;
          final PsiClass boundClass1 = PsiUtil.resolveClassInType(extendsBound);
          if (boundClass1 == null) return false;

          if (CommonClassNames.JAVA_LANG_OBJECT.equals(psiClass2.getQualifiedName())) {
            return !CommonClassNames.JAVA_LANG_OBJECT.equals(boundClass1.getQualifiedName());
          }

          return proveExtendsBoundsDistinct(type1, type2, boundClass1, psiClass2);
        }

        if (((PsiWildcardType)type1).isSuper()) {
          final PsiType superBound = ((PsiWildcardType)type1).getSuperBound();
          if (superBound instanceof PsiArrayType &&
              proveArrayTypeDistinct(((PsiWildcardType)type1).getManager().getProject(), (PsiArrayType)superBound, type2)) return true;

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
        return bound != null && !bound.equals(psiClass2);
      }
      
      if (type2 instanceof PsiArrayType) {
        return proveArrayTypeDistinct(((PsiWildcardType)type1).getManager().getProject(), (PsiArrayType)type2, type1);
      }
    }
    if (type1 instanceof PsiCapturedWildcardType) return provablyDistinct(((PsiCapturedWildcardType)type1).getWildcard(), type2, level);

    if (type2 instanceof PsiWildcardType || type2 instanceof PsiCapturedWildcardType) return provablyDistinct(type2, type1, level);


    final PsiClassType.ClassResolveResult classResolveResult1 = PsiUtil.resolveGenericsClassInType(type1);
    final PsiClassType.ClassResolveResult classResolveResult2 = PsiUtil.resolveGenericsClassInType(type2);
    if (Comparing.equal(TypeConversionUtil.erasure(type1), TypeConversionUtil.erasure(type2))) {
      final PsiSubstitutor substitutor1 = classResolveResult1.getSubstitutor();
      final PsiSubstitutor substitutor2 = classResolveResult2.getSubstitutor();
      for (PsiTypeParameter parameter : substitutor1.getSubstitutionMap().keySet()) {
        final PsiType substitutedType1 = substitutor1.substitute(parameter);
        final PsiType substitutedType2 = substitutor2.substitute(parameter);
        if (substitutedType1 == null && substitutedType2 == null) return false;
        if (substitutedType1 == null || substitutedType2 == null) {
          return true;
        } else {
          if (provablyDistinct(substitutedType1, substitutedType2, level + 1)) return true;
          if (substitutedType1 instanceof PsiWildcardType && !((PsiWildcardType)substitutedType1).isBounded()) return true;
        }
      }
      return false;
    }

    final PsiClass boundClass1 = classResolveResult1.getElement();
    final PsiClass boundClass2 = classResolveResult2.getElement();

    if (boundClass1 instanceof PsiTypeParameter && level < 2) {
      if (!distinguishFromTypeParam((PsiTypeParameter)boundClass1, boundClass2, type1)) return false;
    }

    if (boundClass2 instanceof PsiTypeParameter && level < 2) {
      if (!distinguishFromTypeParam((PsiTypeParameter)boundClass2, boundClass1, type2)) return false;
    }
    return type2 != null && type1 != null && !type1.equals(type2) &&
           (!InheritanceUtil.isInheritorOrSelf(boundClass1, boundClass2, true) ||
            !InheritanceUtil.isInheritorOrSelf(boundClass2, boundClass1, true));
  }

  private static boolean distinguishFromTypeParam(PsiTypeParameter typeParam, PsiClass boundClass, PsiType type1) {
    final PsiClassType[] paramBounds = typeParam.getExtendsListTypes();
    if (paramBounds.length == 0 && type1 instanceof PsiClassType) return false;
    for (PsiClassType classType : paramBounds) {
      final PsiClass paramBound = classType.resolve();
      if (paramBound != null &&
          (InheritanceUtil.isInheritorOrSelf(paramBound, boundClass, true) ||
           InheritanceUtil.isInheritorOrSelf(boundClass, paramBound, true))) {
        return false;
      }
    }
    return true;
  }

  public static boolean provablyDistinct(PsiWildcardType type1, PsiWildcardType type2, boolean rejectInconsistentRaw) {
    if (type1.isSuper() && type2.isSuper()) return false;
    if (type1.isExtends() && type2.isExtends()) {
      final PsiType extendsBound1 = type1.getExtendsBound();
      final PsiType extendsBound2 = type2.getExtendsBound();
      if (extendsBound1 instanceof PsiArrayType && proveArrayTypeDistinct(type1.getManager().getProject(), (PsiArrayType)extendsBound1, extendsBound2) ||
          extendsBound2 instanceof PsiArrayType && proveArrayTypeDistinct(type1.getManager().getProject(), (PsiArrayType)extendsBound2, extendsBound1)) return true;

      final PsiClass boundClass1 = PsiUtil.resolveClassInType(extendsBound1);
      final PsiClass boundClass2 = PsiUtil.resolveClassInType(extendsBound2);
      if (boundClass1 != null && boundClass2 != null) {
        if (rejectInconsistentRaw &&
            extendsBound1 instanceof PsiClassType && extendsBound2 instanceof PsiClassType && 
            (((PsiClassType)extendsBound1).isRaw() ^ ((PsiClassType)extendsBound2).isRaw())) return true;
        return proveExtendsBoundsDistinct(type1, type2, boundClass1, boundClass2);
      }
      return provablyDistinct(extendsBound1, extendsBound2, 1);
    }
    if (type2.isExtends()) return provablyDistinct(type2, type1, rejectInconsistentRaw);
    if (type1.isExtends() && type2.isSuper()) {
      final PsiType extendsBound = type1.getExtendsBound();
      final PsiType superBound = type2.getSuperBound();
      if (extendsBound instanceof PsiArrayType && proveArrayTypeDistinct(type1.getManager().getProject(), (PsiArrayType)extendsBound, superBound) ||
          superBound instanceof PsiArrayType && proveArrayTypeDistinct(type1.getManager().getProject(), (PsiArrayType)superBound, extendsBound)) return true;

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

  public static boolean proveArrayTypeDistinct(Project project,
                                               PsiArrayType type,
                                               PsiType bound) {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final GlobalSearchScope searchScope = GlobalSearchScope.allScope(project);
    final Set<PsiClass> possibleClasses = new HashSet<PsiClass>();
    possibleClasses.add(facade.findClass(CommonClassNames.JAVA_IO_SERIALIZABLE, searchScope));
    possibleClasses.add(facade.findClass(CommonClassNames.JAVA_LANG_CLONEABLE, searchScope));
    possibleClasses.add(facade.findClass(CommonClassNames.JAVA_LANG_OBJECT, searchScope));

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
      return !possibleClasses.contains(((PsiClassType)bound.getDeepComponentType()).resolve());
    }
    else if (bound.getArrayDimensions() == type.getArrayDimensions() + 1 && type.getDeepComponentType() instanceof PsiClassType) {
      return !possibleClasses.contains(((PsiClassType)type.getDeepComponentType()).resolve());
    }
    else if (bound instanceof PsiClassType) {
      return !possibleClasses.contains(((PsiClassType)bound).resolve());
    }
    else if (bound instanceof PsiWildcardType) {
      final PsiType boundBound = ((PsiWildcardType)bound).getBound();
      return boundBound != null && !boundBound.equals(type);
    }
    return true;
  }
}
