/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class GenericsUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.GenericsUtil");

  public static PsiType getGreatestLowerBound(PsiType type1, PsiType type2) {
    return PsiIntersectionType.createIntersection(type1, type2);
  }

  @NotNull
  public static PsiType getLeastUpperBound(PsiType type1, PsiType type2, PsiManager manager) {
    if (TypeConversionUtil.isPrimitiveAndNotNull(type1) || TypeConversionUtil.isPrimitiveAndNotNull(type2)) return null;
    if (TypeConversionUtil.isNullType(type1)) return type2;
    if (TypeConversionUtil.isNullType(type2)) return type1;
    return getLeastUpperBound(type1, type2, new LinkedHashSet<Pair<PsiType, PsiType>>(), manager);
  }

  @NotNull
  private static PsiType getLeastUpperBound(PsiType type1, PsiType type2, Set<Pair<PsiType, PsiType>> compared, PsiManager manager) {
    if (type1 instanceof PsiCapturedWildcardType) {
      return getLeastUpperBound(((PsiCapturedWildcardType)type1).getUpperBound(), type2, compared, manager);
    }
    else if (type2 instanceof PsiCapturedWildcardType) {
      return getLeastUpperBound(type1, ((PsiCapturedWildcardType)type2).getUpperBound(), compared, manager);
    }

    if (type1 instanceof PsiArrayType && type2 instanceof PsiArrayType) {
      final PsiType componentType = getLeastUpperBound(((PsiArrayType)type1).getComponentType(),
                                                       ((PsiArrayType)type2).getComponentType(), manager);
      if (componentType != null) {
        return componentType.createArrayType();
      }
    }
    else if (type1 instanceof PsiIntersectionType) {
      Set<PsiType> newConjuncts = new LinkedHashSet<PsiType>();
      final PsiType[] conjuncts = ((PsiIntersectionType)type1).getConjuncts();
      for (PsiType type : conjuncts) {
        newConjuncts.add(getLeastUpperBound(type, type2, compared, manager));
      }
      return PsiIntersectionType.createIntersection(newConjuncts.toArray(new PsiType[newConjuncts.size()]));
    }
    else if (type2 instanceof PsiIntersectionType) {
      return getLeastUpperBound(type2, type1, compared, manager);
    }
    else if (type1 instanceof PsiClassType && type2 instanceof PsiClassType) {
      PsiClassType.ClassResolveResult classResolveResult1 = ((PsiClassType)type1).resolveGenerics();
      PsiClassType.ClassResolveResult classResolveResult2 = ((PsiClassType)type2).resolveGenerics();
      PsiClass aClass = classResolveResult1.getElement();
      PsiClass bClass = classResolveResult2.getElement();
      if (aClass == null || bClass == null) {
        return manager.getElementFactory().createTypeByFQClassName("java.lang.Object", GlobalSearchScope.allScope(manager.getProject()));
      }

      PsiClass[] supers = getLeastUpperClasses(aClass, bClass);
      if (supers.length == 0) {
        return manager.getElementFactory().createTypeByFQClassName("java.lang.Object", aClass.getResolveScope());
      }

      PsiClassType[] conjuncts = new PsiClassType[supers.length];
      for (int i = 0; i < supers.length; i++) {
        PsiClass aSuper = supers[i];
        PsiSubstitutor subst1 = TypeConversionUtil.getSuperClassSubstitutor(aSuper, aClass,
                                                                            classResolveResult1.getSubstitutor());
        PsiSubstitutor subst2 = TypeConversionUtil.getSuperClassSubstitutor(aSuper, bClass,
                                                                            classResolveResult2.getSubstitutor());
        LOG.assertTrue(subst1 != null && subst2 != null);
        Iterator<PsiTypeParameter> iterator = PsiUtil.typeParametersIterator(aSuper);
        PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
        while (iterator.hasNext()) {
          PsiTypeParameter parameter = iterator.next();
          PsiType mapping1 = subst1.substitute(parameter);
          PsiType mapping2 = subst2.substitute(parameter);

          if (mapping1 != null && mapping2 != null) {
            substitutor = substitutor.put(parameter, getLeastContainingTypeArgument(mapping1, mapping2, compared, manager));
          }
          else {
            substitutor = substitutor.put(parameter, null);
          }
        }

        conjuncts[i] = manager.getElementFactory().createType(aSuper, substitutor);
      }

      return PsiIntersectionType.createIntersection(conjuncts);
    }

    return manager.getElementFactory().createTypeByFQClassName("java.lang.Object", GlobalSearchScope.allScope(manager.getProject()));
  }

  private static PsiType getLeastContainingTypeArgument(PsiType type1,
                                                        PsiType type2,
                                                        Set<Pair<PsiType, PsiType>> compared,
                                                        PsiManager manager) {
    Pair<PsiType, PsiType> p = new Pair<PsiType, PsiType>(type1, type2);
    if (compared.contains(p)) return PsiWildcardType.createUnbounded(manager);
    compared.add(p);

    if (type1 instanceof PsiWildcardType) {
      PsiWildcardType wild1 = (PsiWildcardType)type1;
      if (type2 instanceof PsiWildcardType) {
        PsiWildcardType wild2 = (PsiWildcardType)type2;
        if (wild1.isExtends() == wild2.isExtends()) {
          return wild1.isExtends() ? PsiWildcardType.createExtends(manager,
                                                                   getLeastUpperBound(wild1.getBound(), wild2.getBound(), compared,
                                                                                      manager)) :
                                                                                                wild1.isSuper() ? PsiWildcardType.createSuper(manager, getGreatestLowerBound(wild1.getBound(), wild2.getBound())) :
                                                                                                                wild1;
        }
        else {
          return wild1.getBound().equals(wild2.getBound()) ? wild1.getBound() : PsiWildcardType.createUnbounded(manager);
        }
      }
      else {
        return wild1.isExtends() ? PsiWildcardType.createExtends(manager, getLeastUpperBound(wild1.getBound(), type2, compared, manager)) :
                                 wild1.isSuper() ? PsiWildcardType.createSuper(manager, getGreatestLowerBound(wild1.getBound(), type2)) :
                                                 wild1;
      }
    }
    else if (type2 instanceof PsiWildcardType) {
      return getLeastContainingTypeArgument(type2, type1, compared, manager);
    }
    //Done with wildcards

    if (type1.equals(type2)) return type1;
    return PsiWildcardType.createExtends(manager, getLeastUpperBound(type1, type2, compared, manager));
  }

  public static PsiClass[] getLeastUpperClasses(PsiClass aClass, PsiClass bClass) {
    if (InheritanceUtil.isInheritorOrSelf(aClass, bClass, true)) return new PsiClass[]{bClass};
    Set<PsiClass> supers = new LinkedHashSet<PsiClass>();
    getLeastUpperClassesInner(aClass, bClass, supers);
    return supers.toArray(new PsiClass[supers.size()]);
  }

  private static void getLeastUpperClassesInner(PsiClass aClass, PsiClass bClass, Set<PsiClass> supers) {
    if (bClass.isInheritor(aClass, true)) {
      supers.add(aClass);
    }
    else {
      final PsiClass[] aSupers = aClass.getSupers();
      for (PsiClass aSuper : aSupers) {
        getLeastUpperClassesInner(aSuper, bClass, supers);
      }
    }
  }

  public static boolean isTypeArgumentsApplicable(PsiTypeParameter[] typeParams, PsiSubstitutor substitutor) {
    for (PsiTypeParameter typeParameter : typeParams) {
      PsiType substituted = substitutor.substitute(typeParameter);
      if (substituted == null) return true;

      PsiClassType[] extendsTypes = typeParameter.getExtendsListTypes();
      for (PsiClassType type : extendsTypes) {
        PsiType extendsType = substitutor.substituteAndFullCapture(type);
        if (!extendsType.isAssignableFrom(substituted)) {
          return false;
        }
      }
    }

    return true;
  }

  public static boolean isFromExternalTypeLanguage (PsiType type) {
    return type.getInternalCanonicalText().equals(type.getCanonicalText());
  }

  public static PsiType getVariableTypeByExpressionType(PsiType type) {
    type = type.accept(new PsiTypeVisitor<PsiType>() {
        public PsiType visitArrayType(PsiArrayType arrayType) {
            PsiType componentType = arrayType.getComponentType();
            PsiType type = componentType.accept(this);
            if (type == componentType) return arrayType;
            return type.createArrayType();
        }

        public PsiType visitType(PsiType type) {
            return type;
        }

        public PsiType visitWildcardType(final PsiWildcardType wildcardType) {
          final PsiType bound = wildcardType.getBound();
          PsiManager manager = wildcardType.getManager();
          if (bound != null) {
            final PsiType acceptedBound = bound.accept(this);
            if (acceptedBound instanceof PsiWildcardType) {
              if (((PsiWildcardType)acceptedBound).isExtends() != wildcardType.isExtends()) return PsiWildcardType.createUnbounded(manager);
              return acceptedBound;
            }
            if (acceptedBound.equals(bound)) return wildcardType;
            return wildcardType.isExtends() ? PsiWildcardType.createExtends(manager, acceptedBound) :
                   PsiWildcardType.createSuper(manager, acceptedBound);
          }
          return wildcardType;
        }

        public PsiType visitCapturedWildcardType(PsiCapturedWildcardType capturedWildcardType) {
            return capturedWildcardType.getWildcard().accept(this);
        }

        public PsiType visitClassType(PsiClassType classType) {
          PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
          PsiClass aClass = resolveResult.getElement();
          if (aClass == null) return classType;
          boolean toExtend = false;
          Iterator<PsiTypeParameter> iterator = PsiUtil.typeParametersIterator(aClass);
          PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
          while (iterator.hasNext()) {
            PsiTypeParameter typeParameter = iterator.next();
            PsiType typeArgument = resolveResult.getSubstitutor().substitute(typeParameter);
            if (typeArgument instanceof PsiCapturedWildcardType) toExtend = true;
            substitutor = substitutor.put(typeParameter, typeArgument == null ? null : typeArgument.accept(this));
          }

          PsiManager manager = aClass.getManager();
          PsiType result = manager.getElementFactory().createType(aClass, substitutor);
          if (toExtend) result = PsiWildcardType.createExtends(manager, result);
          return result;
        }
    });

    PsiType componentType = type.getDeepComponentType();
    if (componentType instanceof PsiWildcardType) {
      componentType = ((PsiWildcardType)componentType).getExtendsBound();
      int dims = type.getArrayDimensions();
      for (int i = 0; i < dims; i++) componentType = componentType.createArrayType();
      return componentType;
    }

    return type;
  }
}
