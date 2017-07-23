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
package com.intellij.psi;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author ven
 */
public class GenericsUtil {

  private static final Logger LOG = Logger.getInstance(GenericsUtil.class);

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
    return getLeastUpperBound(type1, type2, new LinkedHashSet<>(), manager);
  }

  @NotNull
  private static PsiType getLeastUpperBound(PsiType type1, PsiType type2, Set<Couple<PsiType>> compared, PsiManager manager) {
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
      Set<PsiType> newConjuncts = new LinkedHashSet<>();
      final PsiType[] conjuncts = ((PsiIntersectionType)type1).getConjuncts();
      for (PsiType type : conjuncts) {
        newConjuncts.add(getLeastUpperBound(type, type2, compared, manager));
      }
      return PsiIntersectionType.createIntersection(newConjuncts.toArray(PsiType.createArray(newConjuncts.size())));
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

      final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
      PsiClassType[] conjuncts = new PsiClassType[supers.length];
      for (int i = 0; i < supers.length; i++) {
        PsiClass aSuper = supers[i];
        PsiSubstitutor subst1 = TypeConversionUtil.getSuperClassSubstitutor(aSuper, aClass, classResolveResult1.getSubstitutor());
        PsiSubstitutor subst2 = TypeConversionUtil.getSuperClassSubstitutor(aSuper, bClass, classResolveResult2.getSubstitutor());
        PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;

        final Couple<PsiType> types = Couple.of(elementFactory.createType(aSuper, subst1), elementFactory.createType(aSuper, subst2));

        for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable(aSuper)) {
          PsiType mapping1 = subst1.substitute(parameter);
          PsiType mapping2 = subst2.substitute(parameter);

          if (mapping1 != null && mapping2 != null) {
            if (compared.contains(types)) {
              substitutor = substitutor.put(parameter, PsiWildcardType.createUnbounded(manager));
            }
            else {
              compared.add(types);
              try {
                substitutor = substitutor.put(parameter, getLeastContainingTypeArgument(mapping1, mapping2, compared, manager));
              }
              finally {
                compared.remove(types);
              }
            }
          }
          else {
            substitutor = substitutor.put(parameter, null);
          }
        }

        conjuncts[i] = elementFactory.createType(aSuper, substitutor);
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
                                                        Set<Couple<PsiType>> compared,
                                                        PsiManager manager) {
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
      return getLeastContainingTypeArgument(type2, type1, compared, manager);
    }
    //Done with wildcards

    if (type1.equals(type2)) return type1;
    return PsiWildcardType.createExtends(manager, getLeastUpperBound(type1, type2, compared, manager));
  }

  @NotNull
  public static PsiClass[] getLeastUpperClasses(PsiClass aClass, PsiClass bClass) {
    if (InheritanceUtil.isInheritorOrSelf(aClass, bClass, true)) return new PsiClass[]{bClass};
    Set<PsiClass> supers = new LinkedHashSet<>();
    Set<PsiClass> visited = new HashSet<>();
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
    for (PsiTypeParameter typeParameter : typeParams) {
      PsiType boundError = findTypeParameterBoundError(typeParameter, typeParameter.getExtendsListTypes(),
                                                       substitutor, context, allowUncheckedConversion);
      if (boundError != null) {
        return Pair.create(typeParameter, boundError);
      }
    }
    return null;
  }

  public static PsiType findTypeParameterBoundError(PsiTypeParameter typeParameter,
                                                    PsiType[] extendsTypes,
                                                    PsiSubstitutor substitutor,
                                                    PsiElement context,
                                                    boolean allowUncheckedConversion) {
    PsiType substituted = substitutor.substitute(typeParameter);
    if (substituted == null) return null;
    if (context != null) {
      substituted = PsiUtil.captureToplevelWildcards(substituted, context);
    }

    if (substituted instanceof PsiWildcardType) {
      if (((PsiWildcardType)substituted).isSuper()) {
        return null;
      }
    }

    for (PsiType type : extendsTypes) {
      PsiType extendsType = substitutor.substitute(type);
      if (extendsType != null && 
          !TypeConversionUtil.isAssignable(extendsType, substituted, allowUncheckedConversion)) {
        return extendsType;
      }
    }
    return null;
  }

  public static boolean isFromExternalTypeLanguage(@NotNull PsiType type) {
    return type.getInternalCanonicalText().equals(type.getCanonicalText());
  }

  @Contract("null -> null")
  public static PsiType getVariableTypeByExpressionType(@Nullable PsiType type) {
    return getVariableTypeByExpressionType(type, true);
  }

  @Contract("null, _ -> null")
  public static PsiType getVariableTypeByExpressionType(@Nullable PsiType type, final boolean openCaptured) {
    if (type == null) return null;
    if (type instanceof PsiCapturedWildcardType) {
      type = ((PsiCapturedWildcardType)type).getUpperBound();
    }
    PsiType transformed = type.accept(new PsiTypeVisitor<PsiType>() {
      @Override
      public PsiType visitArrayType(PsiArrayType arrayType) {
        PsiType componentType = arrayType.getComponentType();
        PsiType type = componentType.accept(this);
        if (type == componentType) return arrayType;
        if (type instanceof PsiWildcardType) {
          type = ((PsiWildcardType)type).getBound();
        }
        return type != null ? type.createArrayType() : arrayType;
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

          if (wildcardType.isSuper() && bound instanceof PsiIntersectionType) {
            return PsiWildcardType.createUnbounded(manager);
          }

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
            if (typeArgument instanceof PsiIntersectionType && !(accepted instanceof PsiWildcardType)) {
              toPut = PsiWildcardType.createExtends(typeParameter.getManager(), accepted);
            }
            else {
              toPut = accepted;
            }
          }
          LOG.assertTrue(toPut == null || toPut.isValid(), toPut);
          substitutor = substitutor.put(typeParameter, toPut);
        }
        PsiAnnotation[] applicableAnnotations = classType.getApplicableAnnotations();
        if (substitutor == PsiSubstitutor.EMPTY && !toExtend && applicableAnnotations.length == 0 && !(aClass instanceof PsiTypeParameter)) {
          return classType;
        }
        PsiManager manager = aClass.getManager();
        PsiType result = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory()
          .createType(aClass, substitutor, PsiUtil.getLanguageLevel(aClass))
          .annotate(TypeAnnotationProvider.Static.create(applicableAnnotations));
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
    final List<PsiType> result = new ArrayList<>(substitutionMap.size());
    for (PsiTypeParameter typeParameter : psiClass.getTypeParameters()) {
      final String name = typeParameter.getName();
      final PsiTypeParameter key = ContainerUtil.find(substitutionMap.keySet(), psiTypeParameter -> name.equals(psiTypeParameter.getName()));
      if (key != null) {
        result.add(substitutionMap.get(key));
      }
    }
    return PsiSubstitutor.EMPTY.putAll(psiClass, result.toArray(PsiType.createArray(result.size())));
  }

  public static PsiType eliminateWildcards(PsiType type) {
    return eliminateWildcards(type, true);
  }

  public static PsiType eliminateWildcards(PsiType type, final boolean eliminateInTypeArguments) {
    return eliminateWildcards(type, eliminateInTypeArguments, !eliminateInTypeArguments);
  }

  public static PsiType eliminateWildcards(PsiType type,
                                           final boolean eliminateInTypeArguments,
                                           boolean eliminateCapturedWildcards) {
    if (eliminateInTypeArguments && type instanceof PsiClassType) {
      PsiClassType classType = (PsiClassType)type;
      JavaResolveResult resolveResult = classType.resolveGenerics();
      PsiClass aClass = (PsiClass)resolveResult.getElement();
      if (aClass != null) {
        PsiManager manager = aClass.getManager();
        PsiTypeParameter[] typeParams = aClass.getTypeParameters();
        Map<PsiTypeParameter, PsiType> map = new HashMap<>();
        for (PsiTypeParameter typeParam : typeParams) {
          PsiType substituted = resolveResult.getSubstitutor().substitute(typeParam);
          if (substituted instanceof PsiWildcardType) {
            substituted = ((PsiWildcardType)substituted).getBound();
            if (substituted instanceof PsiCapturedWildcardType) {
              substituted = ((PsiCapturedWildcardType)substituted).getWildcard().getBound();
            }
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
      return eliminateWildcards(bound != null ? bound : ((PsiWildcardType)type).getExtendsBound(), false);//object
    }
    else if (type instanceof PsiCapturedWildcardType && eliminateCapturedWildcards) {
      return eliminateWildcards(((PsiCapturedWildcardType)type).getUpperBound(), false);
    }
    return type;
  }

  public static boolean checkNotInBounds(PsiType type, PsiType bound, PsiReferenceParameterList referenceParameterList) {
    //4.10.2
    //Given a generic type declaration C<F1,...,Fn> (n > 0), the direct supertypes of the parameterized type C<R1,...,Rn> where at least one of the Ri is a wildcard
    //type argument, are the direct supertypes of the parameterized type C<X1,...,Xn> which is the result of applying capture conversion to C<R1,...,Rn>.
    PsiType capturedType = PsiUtil.captureToplevelWildcards(type, referenceParameterList);
    //allow unchecked conversions in method calls but not in type declaration
    return checkNotInBounds(capturedType, bound, PsiTreeUtil.getParentOfType(referenceParameterList, PsiCallExpression.class) != null);
  }

  public static boolean checkNotInBounds(PsiType type, PsiType bound, boolean uncheckedConversionByDefault) {
    if (type instanceof PsiClassType) {
      return checkNotAssignable(bound, type, uncheckedConversionByDefault);
    }
    if (type instanceof PsiWildcardType) {
      if (((PsiWildcardType)type).isExtends()) {
        return checkExtendsWildcardCaptureFailure((PsiWildcardType)type, bound);
      }
      else if (((PsiWildcardType)type).isSuper()) {
        final PsiType superBound = ((PsiWildcardType)type).getSuperBound();
        if (PsiUtil.resolveClassInType(superBound) instanceof PsiTypeParameter) return TypesDistinctProver.provablyDistinct(type, bound);
        return checkNotAssignable(bound, superBound, false);
      }
    }
    else if (type instanceof PsiArrayType) {
      return checkNotAssignable(bound, type, true);
    }
    else if (type instanceof PsiIntersectionType) {
      for (PsiType psiType : ((PsiIntersectionType)type).getConjuncts()) {
        if (!checkNotInBounds(psiType, bound, uncheckedConversionByDefault)) {
          return false; 
        }
      }
      return true;
    }
    return false;
  }

  //JLS 5.1.10
  private static boolean checkExtendsWildcardCaptureFailure(PsiWildcardType type, PsiType bound) {
    LOG.assertTrue(type.isExtends());
    final PsiType extendsBound = type.getExtendsBound();
    PsiType boundBound = bound;
    if (bound instanceof PsiWildcardType) {
      if (((PsiWildcardType)bound).isBounded()) {
        boundBound = ((PsiWildcardType)bound).isSuper()
                     ? ((PsiWildcardType)bound).getSuperBound()
                     : ((PsiWildcardType)bound).getExtendsBound();
      }
      else {
        return false;
      }
    }

    final PsiClass extendsBoundClass = PsiUtil.resolveClassInClassTypeOnly(extendsBound);
    final PsiClass boundBoundClass = PsiUtil.resolveClassInClassTypeOnly(boundBound);
    if (boundBoundClass != null && extendsBoundClass != null && !boundBoundClass.isInterface() && !extendsBoundClass.isInterface()) {
      return !InheritanceUtil.isInheritorOrSelf(boundBoundClass, extendsBoundClass, true) &&
             !InheritanceUtil.isInheritorOrSelf(extendsBoundClass, boundBoundClass, true);
    }

    return !TypeConversionUtil.areTypesConvertible(boundBound, extendsBound) &&
           !TypeConversionUtil.areTypesConvertible(extendsBound, boundBound);
  }

  private static boolean checkNotAssignable(final PsiType bound,
                                            final PsiType type,
                                            final boolean allowUncheckedConversion) {
    if (bound instanceof PsiWildcardType) {
      if (((PsiWildcardType)bound).isBounded()) {
        final PsiType boundBound = ((PsiWildcardType)bound).isExtends()
                                   ? ((PsiWildcardType)bound).getExtendsBound()
                                   : ((PsiWildcardType)bound).getSuperBound();
        return !TypeConversionUtil.isAssignable(boundBound, type, allowUncheckedConversion);
      }
      else {
        return true;
      }
    }
    else {
      return !TypeConversionUtil.isAssignable(bound, type, allowUncheckedConversion);
    }
  }
}
