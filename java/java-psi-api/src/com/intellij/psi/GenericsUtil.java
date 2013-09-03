/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author ven
 */
public class GenericsUtil {

  private static final Logger LOG = Logger.getInstance("#" + GenericsUtil.class.getName());

  private GenericsUtil() {}

  public static PsiType getGreatestLowerBound(@Nullable PsiType type1, @Nullable PsiType type2) {
    if (type1 == null || type2 == null) return null;
    return PsiIntersectionType.createIntersection(type1, type2);
  }

  @Nullable
  public static PsiType getLeastUpperBound(PsiType type1, PsiType type2, PsiManager manager) {
    if (TypeConversionUtil.isPrimitiveAndNotNull(type1) || TypeConversionUtil.isPrimitiveAndNotNull(type2)) return null;
    if (TypeConversionUtil.isNullType(type1)) return type2;
    if (TypeConversionUtil.isNullType(type2)) return type1;
    if (Comparing.equal(type1, type2)) return type1;
    return getLeastUpperBound(type1, type2, new LinkedHashSet<Pair<PsiType, PsiType>>(), manager);
  }

  @NotNull
  private static PsiType getLeastUpperBound(PsiType type1, PsiType type2, Set<Pair<PsiType, PsiType>> compared, PsiManager manager) {
    if (type1 instanceof PsiCapturedWildcardType) {
      return getLeastUpperBound(((PsiCapturedWildcardType)type1).getUpperBound(), type2, compared, manager);
    }
    if (type2 instanceof PsiCapturedWildcardType) {
      return getLeastUpperBound(type1, ((PsiCapturedWildcardType)type2).getUpperBound(), compared, manager);
    }

    if (type1 instanceof PsiWildcardType) {
      return getLeastUpperBound(((PsiWildcardType)type1).getExtendsBound(), type2, compared, manager);
    }
    if (type2 instanceof PsiWildcardType) {
      return getLeastUpperBound(type1, ((PsiWildcardType)type2).getExtendsBound(), compared, manager);
    }

    if (type1 instanceof PsiArrayType && type2 instanceof PsiArrayType) {
      final PsiType componentType1 = ((PsiArrayType)type1).getComponentType();
      final PsiType componentType2 = ((PsiArrayType)type2).getComponentType();
      final PsiType componentType = getLeastUpperBound(componentType1, componentType2, compared, manager);
      if (componentType1 instanceof PsiPrimitiveType && 
          componentType2 instanceof PsiPrimitiveType && 
          componentType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
        final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
        final GlobalSearchScope resolveScope = GlobalSearchScope.allScope(manager.getProject());
        final PsiClassType cloneable = factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_CLONEABLE, resolveScope);
        final PsiClassType serializable = factory.createTypeByFQClassName(CommonClassNames.JAVA_IO_SERIALIZABLE, resolveScope);
        return PsiIntersectionType.createIntersection(componentType, cloneable, serializable);
      }
      return componentType.createArrayType();
    }
    if (type1 instanceof PsiIntersectionType) {
      Set<PsiType> newConjuncts = new LinkedHashSet<PsiType>();
      final PsiType[] conjuncts = ((PsiIntersectionType)type1).getConjuncts();
      for (PsiType type : conjuncts) {
        newConjuncts.add(getLeastUpperBound(type, type2, compared, manager));
      }
      return PsiIntersectionType.createIntersection(newConjuncts.toArray(new PsiType[newConjuncts.size()]));
    }
    if (type2 instanceof PsiIntersectionType) {
      return getLeastUpperBound(type2, type1, compared, manager);
    }
    if (type1 instanceof PsiClassType && type2 instanceof PsiClassType) {
      PsiClassType.ClassResolveResult classResolveResult1 = ((PsiClassType)type1).resolveGenerics();
      PsiClassType.ClassResolveResult classResolveResult2 = ((PsiClassType)type2).resolveGenerics();
      PsiClass aClass = classResolveResult1.getElement();
      PsiClass bClass = classResolveResult2.getElement();
      if (aClass == null || bClass == null) {
        return PsiType.getJavaLangObject(manager, GlobalSearchScope.allScope(manager.getProject()));
      }

      PsiClass[] supers = getLeastUpperClasses(aClass, bClass);
      if (supers.length == 0) {
        return PsiType.getJavaLangObject(manager, aClass.getResolveScope());
      }

      PsiClassType[] conjuncts = new PsiClassType[supers.length];
      for (int i = 0; i < supers.length; i++) {
        PsiClass aSuper = supers[i];
        PsiSubstitutor subst1 = TypeConversionUtil.getSuperClassSubstitutor(aSuper, aClass, classResolveResult1.getSubstitutor());
        PsiSubstitutor subst2 = TypeConversionUtil.getSuperClassSubstitutor(aSuper, bClass, classResolveResult2.getSubstitutor());
        PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
        for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable(aSuper)) {
          PsiType mapping1 = subst1.substitute(parameter);
          PsiType mapping2 = subst2.substitute(parameter);

          if (mapping1 != null && mapping2 != null) {
            substitutor = substitutor.put(parameter, getLeastContainingTypeArgument(mapping1, mapping2, compared, manager, type1.equals(mapping1) && type2.equals(mapping2) ? aSuper : null, parameter));
          }
          else {
            substitutor = substitutor.put(parameter, null);
          }
        }

        conjuncts[i] = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createType(aSuper, substitutor);
      }

      return PsiIntersectionType.createIntersection(conjuncts);
    }
    if (type2 instanceof PsiArrayType && !(type1 instanceof PsiArrayType)) {
      return getLeastUpperBound(type2, type1, compared, manager);
    }
    if (type1 instanceof PsiArrayType) {
      PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
      GlobalSearchScope all = GlobalSearchScope.allScope(manager.getProject());
      PsiClassType serializable = factory.createTypeByFQClassName(CommonClassNames.JAVA_IO_SERIALIZABLE, all);
      PsiClassType cloneable = factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_CLONEABLE, all);
      PsiType arraySupers = PsiIntersectionType.createIntersection(serializable, cloneable);
      return getLeastUpperBound(arraySupers, type2, compared, manager);
    }

    return PsiType.getJavaLangObject(manager, GlobalSearchScope.allScope(manager.getProject()));
  }

  private static PsiType getLeastContainingTypeArgument(PsiType type1,
                                                        PsiType type2,
                                                        Set<Pair<PsiType, PsiType>> compared,
                                                        PsiManager manager,
                                                        PsiClass nestedLayer, 
                                                        PsiTypeParameter parameter) {
    Pair<PsiType, PsiType> types = new Pair<PsiType, PsiType>(type1, type2);
    if (compared.contains(types)) {
      if (nestedLayer != null) {
        PsiSubstitutor subst = PsiSubstitutor.EMPTY;
        for (PsiTypeParameter param : PsiUtil.typeParametersIterable(nestedLayer)) {
          subst = subst.put(param, PsiWildcardType.createUnbounded(manager));
        }
        subst = subst.put(parameter, getLeastContainingTypeArgument(type1, type2, compared, manager, null, null));

        final PsiClassType boundType = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createType(nestedLayer, subst);
        return PsiWildcardType.createExtends(manager, boundType);
      }
      return PsiWildcardType.createUnbounded(manager);
    }
    compared.add(types);

    try {
      if (type1 instanceof PsiWildcardType) {
        PsiWildcardType wild1 = (PsiWildcardType)type1;
        final PsiType bound1 = wild1.getBound();
        if (bound1 == null) return type1;
        if (type2 instanceof PsiWildcardType) {
          PsiWildcardType wild2 = (PsiWildcardType)type2;
          final PsiType bound2 = wild2.getBound();
          if (bound2 == null) return type2;
          if (wild1.isExtends() == wild2.isExtends()) {
            return wild1.isExtends() ?
                   PsiWildcardType.createExtends(manager, getLeastUpperBound(bound1, bound2, compared, manager)) :
                   PsiWildcardType.createSuper(manager, getGreatestLowerBound(bound1, bound2));
          }
          else {
            return bound1.equals(bound2) ? bound1 : PsiWildcardType.createUnbounded(manager);
          }
        }
        else {
          return wild1.isExtends() ? PsiWildcardType.createExtends(manager, getLeastUpperBound(bound1, type2, compared, manager)) :
                 wild1.isSuper() ? PsiWildcardType.createSuper(manager, getGreatestLowerBound(bound1, type2)) :
                 wild1;
        }
      }
      else if (type2 instanceof PsiWildcardType) {
        return getLeastContainingTypeArgument(type2, type1, compared, manager, null, null);
      }
      //Done with wildcards

      if (type1.equals(type2)) return type1;
      return PsiWildcardType.createExtends(manager, getLeastUpperBound(type1, type2, compared, manager));
    }
    finally {
      compared.remove(types);
    }
  }

  @NotNull
  public static PsiClass[] getLeastUpperClasses(PsiClass aClass, PsiClass bClass) {
    if (InheritanceUtil.isInheritorOrSelf(aClass, bClass, true)) return new PsiClass[]{bClass};
    Set<PsiClass> supers = new LinkedHashSet<PsiClass>();
    Set<PsiClass> visited = new HashSet<PsiClass>();
    getLeastUpperClassesInner(aClass, bClass, supers, visited);
    return supers.toArray(new PsiClass[supers.size()]);
  }

  private static void getLeastUpperClassesInner(PsiClass aClass, PsiClass bClass, Set<PsiClass> supers, Set<PsiClass> visited) {
    if (bClass.isInheritor(aClass, true)) {
      addSuper(supers, aClass);
    }
    else {
      final PsiClass[] aSupers = aClass.getSupers();
      for (PsiClass aSuper : aSupers) {
        if (visited.add(aSuper)) {
          getLeastUpperClassesInner(aSuper, bClass, supers, visited);
        }
      }
    }
  }

  private static void addSuper(final Set<PsiClass> supers, final PsiClass classToAdd) {
    for (Iterator<PsiClass> iterator = supers.iterator(); iterator.hasNext();) {
      PsiClass superClass = iterator.next();
      if (InheritanceUtil.isInheritorOrSelf(superClass, classToAdd, true)) return;
      if (classToAdd.isInheritor(superClass, true)) iterator.remove();
    }
    
    supers.add(classToAdd);
  }

  public static boolean isTypeArgumentsApplicable(final PsiTypeParameter[] typeParams,
                                                  final PsiSubstitutor substitutor,
                                                  final PsiElement context) {
    return isTypeArgumentsApplicable(typeParams, substitutor, context, true);
  }

  public static boolean isTypeArgumentsApplicable(final PsiTypeParameter[] typeParams,
                                                  final PsiSubstitutor substitutor,
                                                  final PsiElement context,
                                                  final boolean allowUncheckedConversion) {
    return findTypeParameterWithBoundError(typeParams, substitutor, context, allowUncheckedConversion) == null;
  }

  public static Pair<PsiTypeParameter, PsiType> findTypeParameterWithBoundError(final PsiTypeParameter[] typeParams,
                                                                                final PsiSubstitutor substitutor,
                                                                                final PsiElement context,
                                                                                final boolean allowUncheckedConversion) {
    nextTypeParam:
    for (PsiTypeParameter typeParameter : typeParams) {
      PsiType substituted = substitutor.substitute(typeParameter);
      if (substituted == null) return null;
      substituted = PsiUtil.captureToplevelWildcards(substituted, context);

      PsiClassType[] extendsTypes = typeParameter.getExtendsListTypes();
      for (PsiClassType type : extendsTypes) {
        PsiType extendsType = substitutor.substitute(type);
        if (substituted instanceof PsiWildcardType) {
          if (((PsiWildcardType)substituted).isSuper()) {
            continue;
          }
          final PsiType extendsBound = ((PsiWildcardType)substituted).getExtendsBound();
          if (Comparing.equal(TypeConversionUtil.erasure(extendsType), TypeConversionUtil.erasure(extendsBound))) {
            if (extendsBound instanceof PsiClassType) {
              if (acceptExtendsBound((PsiClassType)extendsBound, 0)) continue;
            } else if (extendsBound instanceof PsiIntersectionType) {
              for (PsiType psiType : ((PsiIntersectionType)extendsBound).getConjuncts()) {
                if (psiType instanceof PsiClassType) {
                  if (acceptExtendsBound((PsiClassType)psiType, 0)) continue nextTypeParam;
                }
              }
            }
          }
        }
        if (extendsType != null && !TypeConversionUtil.isAssignable(extendsType, substituted, allowUncheckedConversion)) {
          return Pair.create(typeParameter, extendsType);
        }
      }
    }
    return null;
  }

  private static boolean acceptExtendsBound(PsiClassType extendsBound, int depth) {
    PsiType[] parameters = extendsBound.getParameters();
    if (parameters.length == 1) {
      PsiType argType = parameters[0];
      if (argType instanceof PsiCapturedWildcardType && depth == 0) {
        argType = ((PsiCapturedWildcardType)argType).getWildcard();
      }
      if (argType instanceof PsiWildcardType) {
        if (!((PsiWildcardType)argType).isBounded()) return true;
        final PsiType bound = ((PsiWildcardType)argType).getExtendsBound();
        if (bound instanceof PsiClassType && TypeConversionUtil.erasure(bound).equals(TypeConversionUtil.erasure(extendsBound))) {
          return acceptExtendsBound((PsiClassType)bound, depth + 1);
        }
      }
    }
    return false;
  }

  public static boolean isFromExternalTypeLanguage(@NotNull PsiType type) {
    String internalCanonicalText = type.getInternalCanonicalText();
    return internalCanonicalText != null && internalCanonicalText.equals(type.getCanonicalText());
  }

  @Nullable
  public static PsiType getVariableTypeByExpressionType(@Nullable PsiType type) {
    return getVariableTypeByExpressionType(type, true);
  }

  @Nullable
  public static PsiType getVariableTypeByExpressionType(@Nullable PsiType type, final boolean openCaptured) {
    if (type == null) return null;
    if (type instanceof PsiCapturedWildcardType) {
      type = ((PsiCapturedWildcardType)type).getWildcard();
    }
    PsiType transformed = type.accept(new PsiTypeVisitor<PsiType>() {
      @Override
      public PsiType visitArrayType(PsiArrayType arrayType) {
        PsiType componentType = arrayType.getComponentType();
        PsiType type = componentType.accept(this);
        if (type == componentType) return arrayType;
        return type.createArrayType();
      }

      @Override
      public PsiType visitType(PsiType type) {
        return type;
      }

      @Override
      public PsiType visitWildcardType(final PsiWildcardType wildcardType) {
        final PsiType bound = wildcardType.getBound();
        PsiManager manager = wildcardType.getManager();
        if (bound != null) {
          final PsiType acceptedBound = bound.accept(this);
          if (acceptedBound instanceof PsiWildcardType) {
            if (((PsiWildcardType)acceptedBound).isExtends() != wildcardType.isExtends()) return PsiWildcardType.createUnbounded(manager);
            return acceptedBound;
          }
          if (wildcardType.isExtends() && acceptedBound.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) return PsiWildcardType.createUnbounded(manager);
          if (acceptedBound.equals(bound)) return wildcardType;
          return wildcardType.isExtends()
                 ? PsiWildcardType.createExtends(manager, acceptedBound)
                 : PsiWildcardType.createSuper(manager, acceptedBound);
        }
        return wildcardType;
      }

      @Override
      public PsiType visitCapturedWildcardType(PsiCapturedWildcardType capturedWildcardType) {
        return openCaptured ? capturedWildcardType.getWildcard().accept(this) : capturedWildcardType;
      }

      @Override
      public PsiType visitClassType(PsiClassType classType) {
        PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
        PsiClass aClass = resolveResult.getElement();
        if (aClass == null) return classType;
        boolean toExtend = false;
        PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
        for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(aClass)) {
          PsiType typeArgument = resolveResult.getSubstitutor().substitute(typeParameter);
          if (typeArgument instanceof PsiCapturedWildcardType) toExtend = true;
          if (typeArgument instanceof PsiWildcardType && ((PsiWildcardType)typeArgument).getBound() instanceof PsiIntersectionType) {
            toExtend = true;
          }
          PsiType toPut;
          if (typeArgument == null) {
            toPut = null;
          }
          else {
            final PsiType accepted = typeArgument.accept(this);
            if (typeArgument instanceof PsiIntersectionType) {
              toPut = PsiWildcardType.createExtends(typeParameter.getManager(), accepted);
            }
            else {
              toPut = accepted;
            }
          }
          LOG.assertTrue(toPut == null || toPut.isValid(), toPut);
          substitutor = substitutor.put(typeParameter, toPut);
        }
        final PsiAnnotation[] applicableAnnotations = classType.getApplicableAnnotations();
        if (substitutor == PsiSubstitutor.EMPTY && !toExtend && applicableAnnotations.length == 0 && !(aClass instanceof PsiTypeParameter)) return classType;
        PsiManager manager = aClass.getManager();
        PsiType result = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory()
          .createType(aClass, substitutor, PsiUtil.getLanguageLevel(aClass), applicableAnnotations);
        if (toExtend) result = PsiWildcardType.createExtends(manager, result);
        return result;
      }
    });

    PsiType componentType = transformed != null ? transformed.getDeepComponentType() : null;
    if (componentType instanceof PsiWildcardType) {
      componentType = ((PsiWildcardType)componentType).getExtendsBound();
      int dims = transformed.getArrayDimensions();
      for (int i = 0; i < dims; i++) componentType = componentType.createArrayType();
      return componentType;
    }

    return transformed;
  }

  public static PsiSubstitutor substituteByParameterName(final PsiClass psiClass, final PsiSubstitutor parentSubstitutor) {

    final Map<PsiTypeParameter, PsiType> substitutionMap = parentSubstitutor.getSubstitutionMap();
    final List<PsiType> result = new ArrayList<PsiType>(substitutionMap.size());
    for (PsiTypeParameter typeParameter : psiClass.getTypeParameters()) {
      final String name = typeParameter.getName();
      final PsiTypeParameter key = ContainerUtil.find(substitutionMap.keySet(), new Condition<PsiTypeParameter>() {
        @Override
        public boolean value(final PsiTypeParameter psiTypeParameter) {
          return name.equals(psiTypeParameter.getName());
        }
      });
      if (key != null) {
        result.add(substitutionMap.get(key));
      }
    }
    return PsiSubstitutor.EMPTY.putAll(psiClass, result.toArray(new PsiType[result.size()]));
  }

  public static PsiType eliminateWildcards(PsiType type) {
    return eliminateWildcards(type, true);
  }

  public static PsiType eliminateWildcards(PsiType type, final boolean eliminateInTypeArguments) {
    if (eliminateInTypeArguments && type instanceof PsiClassType) {
      PsiClassType classType = ((PsiClassType)type);
      JavaResolveResult resolveResult = classType.resolveGenerics();
      PsiClass aClass = (PsiClass)resolveResult.getElement();
      if (aClass != null) {
        PsiManager manager = aClass.getManager();
        PsiTypeParameter[] typeParams = aClass.getTypeParameters();
        Map<PsiTypeParameter, PsiType> map = new HashMap<PsiTypeParameter, PsiType>();
        for (PsiTypeParameter typeParam : typeParams) {
          PsiType substituted = resolveResult.getSubstitutor().substitute(typeParam);
          if (substituted instanceof PsiWildcardType) {
            substituted = ((PsiWildcardType)substituted).getBound();
            if (substituted == null) substituted = TypeConversionUtil.typeParameterErasure(typeParam);
          }
          map.put(typeParam, substituted);
        }

        PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
        PsiSubstitutor substitutor = factory.createSubstitutor(map);
        type = factory.createType(aClass, substitutor);
      }
    }
    else if (type instanceof PsiArrayType) {
      return eliminateWildcards(((PsiArrayType)type).getComponentType(), false).createArrayType();
    }
    else if (type instanceof PsiWildcardType) {
      final PsiType bound = ((PsiWildcardType)type).getBound();
      return bound != null ? bound 
                           : ((PsiWildcardType)type).getExtendsBound();//object
    } else if (type instanceof PsiCapturedWildcardType && !eliminateInTypeArguments) {
      return eliminateWildcards(((PsiCapturedWildcardType)type).getWildcard(), eliminateInTypeArguments);
    }
    return type;
  }
}
