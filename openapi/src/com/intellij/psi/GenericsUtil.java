/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.search.GlobalSearchScope;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author ven
 */
public class GenericsUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.GenericsUtil");

  public static PsiType getGreatestLowerBound(PsiType type1, PsiType type2) {
    return PsiIntersectionType.createIntersection(new PsiType[] {type1, type2});
  }

  public static PsiType getLeastUpperBound(PsiType type1, PsiType type2, PsiManager manager) {
    if (type1 instanceof PsiPrimitiveType || type2 instanceof PsiPrimitiveType) return null;
    return getLeastUpperBound(type1, type2, new LinkedHashSet<Pair<PsiType, PsiType>>(), manager);
  }

  private static PsiType getLeastUpperBound(PsiType type1, PsiType type2, Set<Pair<PsiType, PsiType>> compared, PsiManager manager) {
    if (type1 instanceof PsiCapturedWildcardType) {
      return getLeastUpperBound(((PsiCapturedWildcardType)type1).getUpperBound(), type2, compared, manager);
    } else if (type2 instanceof PsiCapturedWildcardType) {
      return getLeastUpperBound(type1, ((PsiCapturedWildcardType)type2).getUpperBound(), compared, manager);
    }

    if (type1 instanceof PsiArrayType && type2 instanceof PsiArrayType) {
      final PsiType componentType = getLeastUpperBound(((PsiArrayType)type1).getComponentType(),
                                                  ((PsiArrayType)type2).getComponentType(), manager);
      if (componentType != null) {
        return componentType.createArrayType();
      }
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
          return wild1.isExtends() ? PsiWildcardType.createExtends(manager, getLeastUpperBound(wild1.getBound(), wild2.getBound(), compared, manager)) :
                 wild1.isSuper() ? PsiWildcardType.createSuper(manager, getGreatestLowerBound(wild1.getBound(), wild2.getBound())) :
                 wild1;
        } else {
          return wild1.getBound().equals(wild2.getBound()) ? wild1.getBound() : PsiWildcardType.createUnbounded(manager);
        }
      } else {
        return wild1.isExtends() ? PsiWildcardType.createExtends(manager, getLeastUpperBound(wild1.getBound(), type2, compared, manager)) :
                 wild1.isSuper() ? PsiWildcardType.createSuper(manager, getGreatestLowerBound(wild1.getBound(), type2)) :
                 wild1;
      }
    } else if (type2 instanceof PsiWildcardType) {
      return getLeastContainingTypeArgument(type2, type1, compared, manager);
    }
    //Done with wildcards

    if (type1.equals(type2)) return type1;
    return PsiWildcardType.createExtends(manager, getLeastUpperBound(type1, type2, compared, manager));
  }

  private static PsiClass[] getLeastUpperClasses(PsiClass aClass, PsiClass bClass) {
    if (InheritanceUtil.isInheritorOrSelf(aClass, bClass, true)) return new PsiClass[]{bClass};
    Set<PsiClass> supers = new LinkedHashSet<PsiClass>();
    getLeastUpperClassesInner(aClass, bClass, supers);
    return supers.toArray(new PsiClass[supers.size()]);
  }

  private static void getLeastUpperClassesInner(PsiClass aClass, PsiClass bClass, Set<PsiClass> supers) {
    if (bClass.isInheritor(aClass, true)) {
      supers.add(aClass);
    } else {
      final PsiClass[] aSupers = aClass.getSupers();
      for (int i = 0; i < aSupers.length; i++) {
        PsiClass aSuper = aSupers[i];
        getLeastUpperClassesInner(aSuper, bClass, supers);
      }
    }
  }

  public static boolean isTypeArgumentsApplicable(PsiTypeParameter[] typeParams, PsiSubstitutor substitutor) {
    for (int i = 0; i < typeParams.length; i++) {
      PsiTypeParameter typeParameter = typeParams[i];
      PsiType substituted = substitutor.substitute(typeParameter);
      if (substituted == null) return true;

      PsiClassType[] extendsTypes = typeParameter.getExtendsListTypes();
      for (int j = 0; j < extendsTypes.length; j++) {
        PsiType extendsType = substitutor.substitute(extendsTypes[j]);
        if (!extendsType.isAssignableFrom(substituted)) {
          return false;
        }
      }
    }

    return true;
  }
}
