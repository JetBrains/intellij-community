// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.lang.jvm.types.JvmPrimitiveTypeKind;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ObjectIntHashMap;
import com.intellij.util.containers.ObjectIntMap;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.psi.CommonClassNames.*;

public final class TypeConversionUtil {
  private static final Logger LOG = Logger.getInstance(TypeConversionUtil.class);

  private static final boolean[][] IS_ASSIGNABLE_BIT_SET = {
    {true, true, false, true, true, true, true}, // byte
    {false, true, false, true, true, true, true}, // short
    {false, false, true, true, true, true, true}, // char
    {false, false, false, true, true, true, true}, // int
    {false, false, false, false, true, true, true}, // long
    {false, false, false, false, false, true, true}, // float
    {false, false, false, false, false, false, true}, // double
  };

  private static final ObjectIntMap<PsiType> TYPE_TO_RANK_MAP = new ObjectIntHashMap<>();

  @MagicConstant(intValues = {BYTE_RANK, SHORT_RANK, CHAR_RANK, INT_RANK, FLOAT_RANK, DOUBLE_RANK, BOOL_RANK, STRING_RANK, UNKNOWN_RANK})
  @interface TypeRank {
  }
  public static final int BYTE_RANK = 1;
  public static final int SHORT_RANK = 2;
  public static final int CHAR_RANK = 3;
  public static final int INT_RANK = 4;
  public static final int LONG_RANK = 5;
  public static final int FLOAT_RANK = 6;
  public static final int DOUBLE_RANK = 7;
  private static final int BOOL_RANK = 10;
  private static final int STRING_RANK = 100;
  private static final int UNKNOWN_RANK = 1000;
  @TypeRank
  private static final int MAX_NUMERIC_RANK = DOUBLE_RANK;

  /**
   * This is extracted to a separate field as temporary work around a deadlock during class initialization (IDEA-309438).
   * Deadlock won't happen if PsiType class is initialized before initializing its inheritor PsiEllipsisType.
   */
  private static final PsiPrimitiveType NULL_TYPE_ACCESS = (PsiPrimitiveType)PsiTypes.nullType();
  
  public static final PsiType NULL_TYPE = new PsiEllipsisType(NULL_TYPE_ACCESS) {
    @Override
    public boolean isValid() {
      return true;
    }

    @NotNull
    @Override
    public String getPresentableText(boolean annotated) {
      return "FAKE TYPE";
    }
  };
  private static final Key<PsiElement> ORIGINAL_CONTEXT = Key.create("ORIGINAL_CONTEXT");
  private static final Key<PsiType> LOWER_BOUND = Key.create("LowBound");
  private static final Key<PsiType> UPPER_BOUND = Key.create("UpperBound");

  static {
    TYPE_TO_RANK_MAP.put(PsiTypes.byteType(), BYTE_RANK);
    TYPE_TO_RANK_MAP.put(PsiTypes.shortType(), SHORT_RANK);
    TYPE_TO_RANK_MAP.put(PsiTypes.charType(), CHAR_RANK);
    TYPE_TO_RANK_MAP.put(PsiTypes.intType(), INT_RANK);
    TYPE_TO_RANK_MAP.put(PsiTypes.longType(), LONG_RANK);
    TYPE_TO_RANK_MAP.put(PsiTypes.floatType(), FLOAT_RANK);
    TYPE_TO_RANK_MAP.put(PsiTypes.doubleType(), DOUBLE_RANK);
    TYPE_TO_RANK_MAP.put(PsiTypes.booleanType(), BOOL_RANK);
  }

  private TypeConversionUtil() { }

  /**
   * @return true if fromType can be casted to toType
   */
  public static boolean areTypesConvertible(@NotNull PsiType fromType, @NotNull PsiType toType) {
    return areTypesConvertible(fromType, toType, null);
  }

  /**
   * @return true if fromType can be casted to toType
   */
  public static boolean areTypesConvertible(@NotNull PsiType fromType, @NotNull PsiType toType, @Nullable LanguageLevel languageLevel) {
    if (fromType == toType) return true;
    final boolean fromIsPrimitive = isPrimitiveAndNotNull(fromType);
    final boolean toIsPrimitive = isPrimitiveAndNotNull(toType);
    if (fromIsPrimitive || toIsPrimitive) {
      if (isVoidType(fromType) || isVoidType(toType)) return false;
      final int fromTypeRank = getTypeRank(fromType);
      final int toTypeRank = getTypeRank(toType);
      if (!toIsPrimitive) {
        if (fromTypeRank == toTypeRank) return true;
        if (toType instanceof PsiIntersectionType) {
          for (PsiType type : ((PsiIntersectionType)toType).getConjuncts()) {
            if (!areTypesConvertible(fromType, type)) return false;
          }
          return true;
        }
        // JLS 5.5: A value of a primitive type can be cast to a reference type by boxing conversion(see 5.1.7)
        if (!(toType instanceof PsiClassType)) return false;
        PsiClass toClass = ((PsiClassType)toType).resolve();
        if (toClass == null || toClass instanceof PsiTypeParameter) return false;
        PsiClassType boxedType = ((PsiPrimitiveType)fromType).getBoxedType(toClass.getManager(), toType.getResolveScope());
        return boxedType != null && areTypesConvertible(boxedType, toType);
      }
      if (!fromIsPrimitive) {
        // 5.5. Casting Contexts
        if ((fromTypeRank == SHORT_RANK || fromTypeRank == BYTE_RANK) && toTypeRank == CHAR_RANK) return false;

        fromType = uncapture(fromType); //starting from javac 9+
        if (fromType instanceof PsiClassType) {
          if (languageLevel == null) {
            languageLevel = ((PsiClassType)fromType).getLanguageLevel();
          }

          if (languageLevel.isAtLeast(LanguageLevel.JDK_1_7)) {
            final PsiClassType classType = (PsiClassType)fromType;
            final PsiClass psiClass = classType.resolve();
            if (psiClass == null) return false;
            final PsiClassType boxedType = ((PsiPrimitiveType)toType).getBoxedType(psiClass.getManager(), psiClass.getResolveScope());
            if (boxedType != null && isNarrowingReferenceConversionAllowed(fromType, boxedType)) {
              return true;
            }
          }
        }
        return fromTypeRank == toTypeRank ||
               fromTypeRank < toTypeRank && toTypeRank <= MAX_NUMERIC_RANK;
      }
      return fromTypeRank == toTypeRank ||
             fromTypeRank <= MAX_NUMERIC_RANK && toTypeRank <= MAX_NUMERIC_RANK;
    }

    //type can be casted via widening reference conversion
    if (isAssignable(toType, fromType)) return true;

    if (isNullType(fromType) || isNullType(toType)) return true;

    // or narrowing reference conversion
    return isNarrowingReferenceConversionAllowed(fromType, toType);
  }

  /**
   * see JLS 5.1.5, JLS3 5.1.6
   */
  private static boolean isNarrowingReferenceConversionAllowed(@NotNull PsiType fromType, @NotNull PsiType toType) {
    toType = uncapture(toType);
    fromType = uncapture(fromType);

    if (toType instanceof PsiPrimitiveType || fromType instanceof PsiPrimitiveType) return fromType.equals(toType);
    //Done with primitives
    if (toType instanceof PsiDiamondType || fromType instanceof PsiDiamondType) return false;
    if (toType instanceof PsiArrayType && !(fromType instanceof PsiArrayType)) {
      if (fromType instanceof PsiClassType) {
        final PsiClass resolved = ((PsiClassType)fromType).resolve();
        if (resolved instanceof PsiTypeParameter) {
          for (final PsiClassType boundType : resolved.getExtendsListTypes()) {
            if (!isNarrowingReferenceConversionAllowed(boundType, toType)) return false;
          }
          return true;
        }
      }
      return isAssignable(fromType, toType);
    }
    if (fromType instanceof PsiArrayType) {
      if (toType instanceof PsiClassType) {
        final PsiClass resolved = ((PsiClassType)toType).resolve();
        if (resolved instanceof PsiTypeParameter) {
          for (final PsiClassType boundType : resolved.getExtendsListTypes()) {
            if (!areTypesConvertible(fromType, boundType)) return false;
          }
          return true;
        }
      }
      return toType instanceof PsiArrayType
             && isNarrowingReferenceConversionAllowed(((PsiArrayType)fromType).getComponentType(),
                                                      ((PsiArrayType)toType).getComponentType());
    }
    //Done with array types

    if (fromType instanceof PsiIntersectionType) {
      final PsiType[] conjuncts = ((PsiIntersectionType)fromType).getConjuncts();
      for (PsiType conjunct : conjuncts) {
        if (isNarrowingReferenceConversionAllowed(conjunct, toType)) return true;
      }
      return false;
    }
    if (toType instanceof PsiIntersectionType) {
      if (fromType instanceof PsiClassType && ((PsiClassType)fromType).getLanguageLevel().isAtLeast(LanguageLevel.JDK_1_8)) {
        for (PsiType conjunct : ((PsiIntersectionType)toType).getConjuncts()) {
          if (!isNarrowingReferenceConversionAllowed(fromType, conjunct)) return false;
        }
        return true;
      }
      return false;
    }

    if (fromType instanceof PsiDisjunctionType) {
      return isNarrowingReferenceConversionAllowed(((PsiDisjunctionType)fromType).getLeastUpperBound(), toType);
    }
    if (toType instanceof PsiDisjunctionType) {
      return false;
    }

    if (fromType instanceof PsiWildcardType) {
      final PsiWildcardType fromWildcard = (PsiWildcardType)fromType;
      final PsiType bound = fromWildcard.getBound();
      if (bound == null) return true;
      if (fromWildcard.isSuper()) {
        return isAssignable(toType, bound);
      }
      return isNarrowingReferenceConversionAllowed(bound, toType);
    }
    if (toType instanceof PsiWildcardType) {
      final PsiWildcardType toWildcard = (PsiWildcardType)toType;
      if (toWildcard.isSuper()) return false;
      final PsiType bound = toWildcard.getBound();
      return bound == null || isNarrowingReferenceConversionAllowed(fromType, bound);
    }

    if (isAssignable(fromType, toType)) return true;

    if (!(fromType instanceof PsiClassType) || !(toType instanceof PsiClassType)) return false;
    PsiClassType fromClassType = (PsiClassType)fromType;
    PsiClassType toClassType = (PsiClassType)toType;

    PsiClassType.ClassResolveResult fromResult = fromClassType.resolveGenerics();
    final PsiClass fromClass = fromResult.getElement();
    if (fromClass == null) return false;
    if (fromClass instanceof PsiTypeParameter) {
      return isNarrowingReferenceConversionAllowed(obtainSafeSuperType((PsiTypeParameter)fromClass), toType);
    }

    PsiClassType.ClassResolveResult toResult = toClassType.resolveGenerics();
    final PsiClass toClass = toResult.getElement();
    if (toClass == null) return false;
    if (toClass instanceof PsiTypeParameter) {
      return isNarrowingReferenceConversionAllowed(fromType, obtainSafeSuperType((PsiTypeParameter)toClass));
    }
    //Done with type parameters

    PsiManager manager = fromClass.getManager();
    final LanguageLevel languageLevel = toClassType.getLanguageLevel();
    //  jep-397
    if (languageLevel.isAtLeast(LanguageLevel.JDK_17)) {
      if (fromClass.isInterface() || toClass.isInterface()) {
        if (fromClass.hasModifierProperty(PsiModifier.SEALED)) {
          if (!canConvertSealedTo(fromClass, toClass)) return false;
        }
        else if (toClass.hasModifierProperty(PsiModifier.SEALED)) {
          if (!canConvertSealedTo(toClass, fromClass)) return false;
        }
      }
    }
    if (!fromClass.isInterface()) {
      if (toClass.isInterface()) {
        return (!fromClass.hasModifierProperty(PsiModifier.FINAL) || fromClass.isInheritor(toClass, true)) &&
               checkSuperTypesWithDifferentTypeArguments(toResult, fromClass, manager, fromResult.getSubstitutor(), null, languageLevel);
      }
      else {
        if (manager.areElementsEquivalent(fromClass, toClass)) {
          return areSameParameterTypes(fromClassType, toClassType);
        }

        if (toClass.isInheritor(fromClass, true)) {
          return checkSuperTypesWithDifferentTypeArguments(fromResult, toClass, manager, toResult.getSubstitutor(), null, languageLevel);
        }
        if (fromClass.isInheritor(toClass, true)) {
          return checkSuperTypesWithDifferentTypeArguments(toResult, fromClass, manager, fromResult.getSubstitutor(), null, languageLevel);
        }

        return false;
      }
    }
    else if (!toClass.isInterface()) {
      if (!toClass.hasModifierProperty(PsiModifier.FINAL)) {
        return checkSuperTypesWithDifferentTypeArguments(fromResult, toClass, manager, toResult.getSubstitutor(), null, languageLevel);
      }
      else {
        PsiSubstitutor toSubstitutor = getMaybeSuperClassSubstitutor(fromClass, toClass, toResult.getSubstitutor());
        return toSubstitutor != null && areSameArgumentTypes(fromClass, fromResult.getSubstitutor(), toSubstitutor);
      }
    }
    else if (languageLevel.compareTo(LanguageLevel.JDK_1_5) < 0) {
      //In jls2 check for method in both interfaces with the same signature but different return types.
      Collection<HierarchicalMethodSignature> fromClassMethodSignatures = fromClass.getVisibleSignatures();
      Collection<HierarchicalMethodSignature> toClassMethodSignatures = toClass.getVisibleSignatures();

      for (HierarchicalMethodSignature fromMethodSignature : fromClassMethodSignatures) {
        for (HierarchicalMethodSignature toMethodSignature : toClassMethodSignatures) {
          if (fromMethodSignature.equals(toMethodSignature)) {
            final PsiType fromClassReturnType = fromMethodSignature.getMethod().getReturnType();
            final PsiType toClassReturnType = toMethodSignature.getMethod().getReturnType();
            if (fromClassReturnType != null
                && toClassReturnType != null
                && !fromClassReturnType.equals(toClassReturnType)) {
              return false;
            }
          }
        }
      }
      return true;
    }
    else {
      //In jls3 check for super interface with distinct type arguments
      PsiClassType.ClassResolveResult baseResult;
      PsiClass derived;
      PsiSubstitutor derivedSubstitutor;
      if (toClass.isInheritor(fromClass, true)) {
        baseResult = fromResult;
        derived = toClass;
        derivedSubstitutor = toResult.getSubstitutor();
      }
      else {
        baseResult = toResult;
        derived = fromClass;
        derivedSubstitutor = fromResult.getSubstitutor();
      }
      return checkSuperTypesWithDifferentTypeArguments(baseResult, derived, manager, derivedSubstitutor, null, languageLevel);
    }
  }

  /**
   * Check if sealed class can be narrowed down to a given class.
   * Check performed only if sealed class or class that it should be narrowed down to is an interface.
   * 
   * Sealed class can be narrowed down to an interface in one of the following cases:
   * <ul>
   *  <li>sealed class implements interface
   *  <li>sealed class have at least one non-sealed subclass
   *  <li>at least one of final/sealed subclasses of sealed parent implement interface
   * </ul>
   *
   * <p>Note that sealed subclasses are checked recursively, e.g. in hierarchy:</p>
   *
   * <code>
   * <p>sealed class Parent {}
   * <p>sealed class A extends Parent {}
   * <p>final class C extends A {}
   * </code>
   * <p>all classes would be checked.</p>
   * <br>
   * <p>See JEP-397 for more details.</p>
   */
  public static boolean canConvertSealedTo(@NotNull PsiClass sealedClass, @NotNull PsiClass psiClass) {
    LOG.assertTrue(sealedClass.isInterface() || psiClass.isInterface());
    return canConvertSealedTo(sealedClass, psiClass, new HashSet<>());
  }
  
  private static boolean canConvertSealedTo(@NotNull PsiClass sealedClass,
                                            @NotNull PsiClass psiClass,
                                            @NotNull Set<PsiClass> visited) {
    if (visited.contains(sealedClass)) return true;
    visited.add(sealedClass);
    PsiReferenceList permitsList = sealedClass.getPermitsList();
    List<PsiClass> sealedSubClasses = new SmartList<>();
    boolean hasClassInheritors;
    if (permitsList == null) {
      Set<PsiClass> subClasses = findDirectSubClassesInFile(sealedClass);
      hasClassInheritors = subClasses.stream().anyMatch(subClass -> subClassExtendsClass(subClass, psiClass, sealedSubClasses));
    }
    else {
      hasClassInheritors = Arrays.stream(permitsList.getReferencedTypes())
        .map(t -> t.resolve())
        .anyMatch(subClass -> subClassExtendsClass(subClass, psiClass, sealedSubClasses));
    }
    return hasClassInheritors || sealedSubClasses.stream().anyMatch(subClass -> canConvertSealedTo(subClass, psiClass, visited));
  }

  private static @NotNull Set<PsiClass> findDirectSubClassesInFile(@NotNull PsiClass sealedClass) {
    Set<PsiClass> subClasses = new HashSet<>();

    if (sealedClass.isEnum()) {
      for (PsiField field : sealedClass.getFields()) {
        if (field instanceof PsiEnumConstant) {
          ContainerUtil.addIfNotNull(subClasses, ((PsiEnumConstant)field).getInitializingClass());
        }
      }
      return subClasses;
    }

    sealedClass.getContainingFile().accept(new JavaElementVisitor() {
      @Override
      public void visitJavaFile(@NotNull PsiJavaFile file) {
        for (PsiClass psiClass : file.getClasses()) {
          visitClass(psiClass);
        }
      }

      @Override
      public void visitClass(@NotNull PsiClass psiClass) {
        for (PsiClass inner : psiClass.getInnerClasses()) {
          visitClass(inner);
        }
        if (psiClass.isInheritor(sealedClass, false)) {
          subClasses.add(psiClass);
        }
      }
    });
    return subClasses;
  }

  private static boolean subClassExtendsClass(@Nullable PsiClass subClass,
                                              @NotNull PsiClass psiClass,
                                              @NotNull List<PsiClass> sealedClasses) {
    if (subClass == null) return false;
    if (subClass.hasModifierProperty(PsiModifier.SEALED)) {
      sealedClasses.add(subClass);
      return false;
    }
    //DON'T use `hasModifierProperty(PsiModifier.NON_SEALED)`, because compiled classes don't have this modifier
    return !subClass.hasModifierProperty(PsiModifier.FINAL) ||
           InheritanceUtil.isInheritorOrSelf(subClass, psiClass, true);
  }

  @NotNull
  private static PsiClassType obtainSafeSuperType(@NotNull PsiTypeParameter typeParameter) {
    final PsiClassType superType = typeParameter.getSuperTypes()[0];
    final PsiClassType.ClassResolveResult result = superType.resolveGenerics();
    final PsiClass superClass = result.getElement();
    if (superClass != null) {
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(typeParameter.getProject());
      if (superClass instanceof PsiTypeParameter && InheritanceUtil.getCircularClass(superClass) != null) {
        return factory.createTypeByFQClassName(JAVA_LANG_OBJECT, typeParameter.getResolveScope());
      }
      final PsiSubstitutor substitutor = result.getSubstitutor().put(typeParameter, null);
      return factory.createType(superClass, substitutor);
    }
    return superType;
  }

  private static boolean checkSuperTypesWithDifferentTypeArguments(@NotNull PsiClassType.ClassResolveResult baseResult,
                                                                   @NotNull PsiClass derived,
                                                                   @NotNull PsiManager manager,
                                                                   @NotNull PsiSubstitutor derivedSubstitutor,
                                                                   Set<? super PsiClass> visited,
                                                                   @NotNull LanguageLevel languageLevel) {
    if (visited != null && visited.contains(derived)) return true;

    if (languageLevel.compareTo(LanguageLevel.JDK_1_5) < 0) return true;
    PsiClass base = baseResult.getElement();
    PsiClass[] supers = derived.getSupers();
    if (manager.areElementsEquivalent(base, derived)) {
      derivedSubstitutor = getSuperClassSubstitutor(derived, derived, derivedSubstitutor);
      return areSameArgumentTypes(derived, baseResult.getSubstitutor(), derivedSubstitutor, 1);
    }
    PsiSubstitutor baseSubstitutor = getMaybeSuperClassSubstitutor(derived, base, baseResult.getSubstitutor());
    if (baseSubstitutor != null) {
      derivedSubstitutor = getSuperClassSubstitutor(derived, derived, derivedSubstitutor);
      if (!areSameArgumentTypes(derived, baseSubstitutor, derivedSubstitutor)) return false;
    }

    if (visited == null) {
      visited = new HashSet<>();
    }
    visited.add(derived);
    for (PsiClass aSuper : supers) {
      PsiSubstitutor s = getSuperClassSubstitutor(aSuper, derived, derivedSubstitutor);
      if (!checkSuperTypesWithDifferentTypeArguments(baseResult, aSuper, manager, s, visited, languageLevel)) return false;
    }

    return true;
  }

  private static boolean areSameParameterTypes(@NotNull PsiClassType type1, @NotNull PsiClassType type2) {
    PsiClassType.ClassResolveResult resolveResult1 = type1.resolveGenerics();
    PsiClassType.ClassResolveResult resolveResult2 = type2.resolveGenerics();
    final PsiClass aClass = resolveResult1.getElement();
    final PsiClass bClass = resolveResult2.getElement();
    return aClass != null &&
           bClass != null &&
           aClass.getManager().areElementsEquivalent(aClass, bClass) &&
           areSameArgumentTypes(aClass, resolveResult1.getSubstitutor(), resolveResult2.getSubstitutor(), 1);
  }

  private static boolean areSameArgumentTypes(@NotNull PsiClass aClass,
                                              @NotNull PsiSubstitutor substitutor1,
                                              @NotNull PsiSubstitutor substitutor2) {
    return areSameArgumentTypes(aClass, substitutor1, substitutor2, 0);
  }

  private static boolean areSameArgumentTypes(@NotNull PsiClass aClass,
                                              @NotNull PsiSubstitutor substitutor1,
                                              @NotNull PsiSubstitutor substitutor2,
                                              int level) {
    for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(aClass)) {
      PsiType typeArg1 = substitutor1.substitute(typeParameter);
      PsiType typeArg2 = substitutor2.substitute(typeParameter);
      if (typeArg1 == null || typeArg2 == null) return true;
      if (TypesDistinctProver.provablyDistinct(typeArg1, typeArg2, level)) return false;
    }

    return true;
  }

  @Contract(value = "null -> false", pure = true)
  public static boolean isPrimitiveAndNotNull(PsiType type) {
    return type instanceof PsiPrimitiveType && !isNullType(type);
  }

  @Contract(value = "null -> false", pure = true)
  public static boolean isEnumType(PsiType type) {
    type = uncapture(type);
    if (type instanceof PsiClassType) {
      final PsiClass psiClass = ((PsiClassType)type).resolve();
      if (psiClass instanceof PsiTypeParameter) {
        return InheritanceUtil.isInheritor(psiClass, true, JAVA_LANG_ENUM);
      }
      return psiClass != null && psiClass.isEnum();
    }
    return false;
  }

  @Contract(value = "null -> false", pure = true)
  public static boolean isNullType(PsiType type) {
    return PsiTypes.nullType().equals(type);
  }

  @Contract(value = "null -> false", pure = true)
  public static boolean isFloatOrDoubleType(@Nullable PsiType type) {
    return isFloatType(type) || isDoubleType(type);
  }

  @Contract(value = "null -> false", pure = true)
  public static boolean isDoubleType(@Nullable PsiType type) {
    type = uncapture(type);
    return PsiTypes.doubleType().equals(type) || PsiTypes.doubleType().equals(PsiPrimitiveType.getUnboxedType(type));
  }

  @Contract(value = "null -> false", pure = true)
  public static boolean isFloatType(@Nullable PsiType type) {
    type = uncapture(type);
    return PsiTypes.floatType().equals(type) || PsiTypes.floatType().equals(PsiPrimitiveType.getUnboxedType(type));
  }

  @Contract(value = "null -> false", pure = true)
  public static boolean isLongType(@Nullable PsiType type) {
    type = uncapture(type);
    return PsiTypes.longType().equals(type) || PsiTypes.longType().equals(PsiPrimitiveType.getUnboxedType(type));
  }

  @Contract(value = "null -> false", pure = true)
  public static boolean isVoidType(@Nullable PsiType type) {
    return PsiTypes.voidType().equals(type);
  }

  @Contract(value = "null -> false", pure = true)
  public static boolean isBooleanType(@Nullable PsiType type) {
    type = uncapture(type);
    return PsiTypes.booleanType().equals(type) || PsiTypes.booleanType().equals(PsiPrimitiveType.getUnboxedType(type));
  }

  @Contract(value = "null -> null", pure = true)
  private static PsiType uncapture(PsiType type) {
    while (type instanceof PsiCapturedWildcardType) {
      type = ((PsiCapturedWildcardType)type).getUpperBound();
    }
    return type;
  }

  @Contract(pure = true)
  public static boolean isNumericType(@TypeRank int typeRank) {
    return typeRank <= MAX_NUMERIC_RANK;
  }
  @Contract(pure = true)
  public static boolean isNumericType(PsiType type) {
    return type != null && isNumericType(getTypeRank(type));
  }
  @Contract(pure = true)
  public static boolean isIntegralNumberType(PsiType type) {
    return type != null && getTypeRank(type) <= LONG_RANK;
  }

  /**
   * @return 1..MAX_NUMERIC_TYPE if type is primitive numeric type,
   *         BOOL_TYPE for boolean,
   *         STRING_TYPE for String,
   *         Integer.MAX_VALUE for others
   */
  @TypeRank
  @Contract(pure = true)
  public static int getTypeRank(@NotNull PsiType type) {
    type = uncapture(type);
    PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(type);
    if (unboxedType != null) {
      type = unboxedType;
    }

    int rank = TYPE_TO_RANK_MAP.get(type);
    if (rank != -1) {
      return rank;
    }
    if (type.equalsToText(JAVA_LANG_STRING)) {
      return STRING_RANK;
    }
    return UNKNOWN_RANK;
  }

  /**
   * @param tokenType JavaTokenType enumeration
   * @param strict    true if operator result type should be convertible to the left operand
   * @return true if lOperand operator rOperand expression is syntactically correct
   */
  @Contract(pure = true)
  public static boolean isBinaryOperatorApplicable(@NotNull IElementType tokenType,
                                                   PsiExpression lOperand,
                                                   PsiExpression rOperand,
                                                   boolean strict) {
    if (lOperand == null || rOperand == null) return true;
    final PsiType ltype = lOperand.getType();
    final PsiType rtype = rOperand.getType();
    return isBinaryOperatorApplicable(tokenType, ltype, rtype, strict);
  }

  @Contract(pure = true)
  public static boolean isBinaryOperatorApplicable(@NotNull IElementType tokenType, final PsiType ltype, final PsiType rtype, final boolean strict) {
    if (ltype == null || rtype == null) return true;
    int resultTypeRank = BOOL_RANK;
    boolean isApplicable = false;
    final int ltypeRank = getTypeRank(ltype);
    final int rtypeRank = getTypeRank(rtype);

    if (tokenType == JavaTokenType.LT || tokenType == JavaTokenType.LE || tokenType == JavaTokenType.GT || tokenType == JavaTokenType.GE) {
      if (isPrimitiveAndNotNullOrWrapper(ltype) && isPrimitiveAndNotNullOrWrapper(rtype)) {
        isApplicable = ltypeRank <= MAX_NUMERIC_RANK && rtypeRank <= MAX_NUMERIC_RANK;
      }
    }
    else if (tokenType == JavaTokenType.EQEQ || tokenType == JavaTokenType.NE) {
      if (isPrimitiveAndNotNullOrWrapper(ltype) && isPrimitiveAndNotNullOrWrapper(rtype) &&
          (isPrimitiveAndNotNull(ltype) || isPrimitiveAndNotNull(rtype))) {
        isApplicable = ltypeRank <= MAX_NUMERIC_RANK && rtypeRank <= MAX_NUMERIC_RANK
                       || ltypeRank == BOOL_RANK && rtypeRank == BOOL_RANK;
      }
      else {
        if (isPrimitiveAndNotNull(ltype)) {
          if (rtype instanceof PsiClassType) {
            final LanguageLevel languageLevel = ((PsiClassType)rtype).getLanguageLevel();
            return languageLevel.isAtLeast(LanguageLevel.JDK_1_5) &&
                   !languageLevel.isAtLeast(LanguageLevel.JDK_1_8) &&
                   areTypesConvertible(ltype, rtype);
          }
          return false;
        }
        if (isPrimitiveAndNotNull(rtype)) {
          if (ltype instanceof PsiClassType) {
            final LanguageLevel level = ((PsiClassType)ltype).getLanguageLevel();
            return level.isAtLeast(LanguageLevel.JDK_1_7) && !level.isAtLeast(LanguageLevel.JDK_1_8) && areTypesConvertible(rtype, ltype);
          }
          return false;
        }
        isApplicable = areTypesConvertible(ltype, rtype) || areTypesConvertible(rtype, ltype);
      }
    }
    else if (tokenType == JavaTokenType.PLUS) {
      if (ltype.equalsToText(JAVA_LANG_STRING)) {
        isApplicable = !isVoidType(rtype);
        resultTypeRank = STRING_RANK;
      }
      else if (rtype.equalsToText(JAVA_LANG_STRING)) {
        if (isVoidType(ltype)) {
          return false;
        }
        return !strict || ltype.isAssignableFrom(rtype);
      }
      else if (isPrimitiveAndNotNullOrWrapper(ltype) && isPrimitiveAndNotNullOrWrapper(rtype)) {
        resultTypeRank = Math.max(ltypeRank, rtypeRank);
        isApplicable = ltypeRank <= MAX_NUMERIC_RANK && rtypeRank <= MAX_NUMERIC_RANK;
      }
    }
    else if (tokenType == JavaTokenType.ASTERISK || tokenType == JavaTokenType.DIV || tokenType == JavaTokenType.PERC ||
             tokenType == JavaTokenType.MINUS) {
      if (isPrimitiveAndNotNullOrWrapper(ltype) && isPrimitiveAndNotNullOrWrapper(rtype)) {
        resultTypeRank = Math.max(ltypeRank, rtypeRank);
        isApplicable = ltypeRank <= MAX_NUMERIC_RANK && rtypeRank <= MAX_NUMERIC_RANK;
      }
    }
    else if (tokenType == JavaTokenType.LTLT || tokenType == JavaTokenType.GTGT || tokenType == JavaTokenType.GTGTGT) {
      if (isPrimitiveAndNotNullOrWrapper(ltype) && isPrimitiveAndNotNullOrWrapper(rtype)) {
        isApplicable = ltypeRank <= LONG_RANK && rtypeRank <= LONG_RANK;
        resultTypeRank = INT_RANK;
      }
    }
    else if (tokenType == JavaTokenType.AND || tokenType == JavaTokenType.OR || tokenType == JavaTokenType.XOR) {
      if (isPrimitiveAndNotNullOrWrapper(ltype) && isPrimitiveAndNotNullOrWrapper(rtype)) {
        isApplicable = ltypeRank <= LONG_RANK && rtypeRank <= LONG_RANK
                       || isBooleanType(ltype) && isBooleanType(rtype);
        resultTypeRank = ltypeRank <= LONG_RANK ? INT_RANK : BOOL_RANK;
      }
    }
    else if (tokenType == JavaTokenType.ANDAND || tokenType == JavaTokenType.OROR) {
      if (isPrimitiveAndNotNullOrWrapper(ltype) && isPrimitiveAndNotNullOrWrapper(rtype)) {
        isApplicable = isBooleanType(ltype) && isBooleanType(rtype);
      }
    }

    if (isApplicable && strict && resultTypeRank > MAX_NUMERIC_RANK) {
      isApplicable = ltypeRank == resultTypeRank || ltype.equalsToText(JAVA_LANG_OBJECT);
    }
    return isApplicable;
  }

  @Contract(pure = true)
  public static boolean isPrimitiveAndNotNullOrWrapper(PsiType type) {
    type = uncapture(type);
    if (type instanceof PsiClassType) {
      return PsiPrimitiveType.getUnboxedType(type) != null;
    }

    return isPrimitiveAndNotNull(type);
  }

  @Contract(pure = true)
  public static boolean isUnaryOperatorApplicable(@NotNull PsiJavaToken token, PsiExpression operand) {
    if (operand == null) return false;
    PsiType type = operand.getType();
    return type != null && isUnaryOperatorApplicable(token, type);
  }

  @Contract(pure = true)
  public static boolean isUnaryOperatorApplicable(@NotNull PsiJavaToken token, @NotNull PsiType type) {
    IElementType i = token.getTokenType();
    int typeRank = getTypeRank(type);
    if (i == JavaTokenType.MINUSMINUS || i == JavaTokenType.PLUSPLUS) {
      return typeRank <= MAX_NUMERIC_RANK;
    }
    if (i == JavaTokenType.MINUS || i == JavaTokenType.PLUS) {
      return typeRank <= MAX_NUMERIC_RANK;
    }
    if (i == JavaTokenType.TILDE) {
      return typeRank <= LONG_RANK;
    }
    if (i == JavaTokenType.EXCL) {
      return typeRank == BOOL_RANK;
    }
    LOG.error("unknown token: " + token);
    return true;
  }

  /**
   * @return true if expression can be the left part of assignment operator
   */
  @Contract(pure = true)
  public static boolean isLValue(PsiExpression element) {
    if (element instanceof PsiReferenceExpression) {
      final PsiReferenceExpression expression = (PsiReferenceExpression)element;
      final PsiElement resolved = expression.resolve();
      return resolved instanceof PsiVariable;
    }
    if (element instanceof PsiParenthesizedExpression) {
      return isLValue(((PsiParenthesizedExpression)element).getExpression());
    }
    if (element instanceof PsiArrayAccessExpression) {
      final PsiArrayAccessExpression arrayAccessExpression = (PsiArrayAccessExpression)element;
      final PsiExpression arrayExpression = arrayAccessExpression.getArrayExpression();
      final PsiType type = arrayExpression.getType();
      if (!(type instanceof PsiArrayType)) return false;
      final PsiExpression indexExpression = arrayAccessExpression.getIndexExpression();
      if (indexExpression == null) return false;
      final PsiType indexType = indexExpression.getType();
      if (indexType == null) return false;
      return getTypeRank(indexType) <= INT_RANK;
    }
    return false;
  }


  /**
   * JLS 5.2
   */
  @Contract(pure = true)
  public static boolean areTypesAssignmentCompatible(PsiType lType, PsiExpression rExpr) {
    if (lType == null || rExpr == null) return true;
    PsiType rType = rExpr.getType();
    if (rType == null) return false;
    if (isAssignable(lType, rType)) return true;
    if (lType instanceof PsiClassType) {
        lType = PsiPrimitiveType.getUnboxedType(lType);
        if (lType == null) return false;
    }

    final int rTypeRank = getTypeRank(rType);
    if (lType instanceof PsiPrimitiveType
        && rType instanceof PsiPrimitiveType
        && rTypeRank >= BYTE_RANK && rTypeRank <= INT_RANK) {
      final Object rValue = JavaPsiFacade.getInstance(rExpr.getProject()).getConstantEvaluationHelper().computeConstantExpression(rExpr);
      final long value;
      if (rValue instanceof Number) {
        value = ((Number)rValue).longValue();
      }
      else if (rValue instanceof Character) {
        value = (Character)rValue;
      }
      else {
        return false;
      }

      if (PsiTypes.byteType().equals(lType)) {
        return -128 <= value && value <= 127;
      }
      else if (PsiTypes.shortType().equals(lType)) {
        return -32768 <= value && value <= 32767;
      }
      else if (PsiTypes.charType().equals(lType)) {
        return 0 <= value && value <= 0xFFFF;
      }
    }
    return false;
  }

  /**
   * Checks whether values of one type can be assigned to another
   *
   * @param left  type to assign to
   * @param right type of value
   * @return true if value of type {@code right} can be assigned to an l-value of
   *         type {@code left}
   */
  @Contract(pure = true)
  public static boolean isAssignable(@NotNull PsiType left, @NotNull PsiType right) {
    return isAssignable(left, right, true);
  }

  @Contract(pure = true)
  public static boolean isAssignable(@NotNull PsiType left, @NotNull PsiType right, boolean allowUncheckedConversion) {
    return isAssignable(left, right, allowUncheckedConversion, true);
  }

  private static boolean isAssignable(@NotNull PsiType left,
                                      @NotNull PsiType right,
                                      boolean allowUncheckedConversion,
                                      boolean capture) {
    if (left == right) return true;
    if (left instanceof PsiClassType && left.equalsToText(JAVA_LANG_OBJECT)) {
      if (right instanceof PsiMethodReferenceType || right instanceof PsiLambdaExpressionType) return false;
    }
    if (left.equals(right)) return true;

    if (isNullType(right)) {
      return !(left instanceof PsiPrimitiveType) || isNullType(left);
    }

    if (right instanceof PsiMethodReferenceType) {
      final PsiMethodReferenceExpression methodReferenceExpression = ((PsiMethodReferenceType)right).getExpression();
      if (left instanceof PsiLambdaExpressionType) {
        final PsiType rType = methodReferenceExpression.getFunctionalInterfaceType();
        final PsiType lType = ((PsiLambdaExpressionType)left).getExpression().getFunctionalInterfaceType();
        return Comparing.equal(rType, lType);
      }
      if (left instanceof PsiMethodReferenceType) {
        final PsiType rType = methodReferenceExpression.getFunctionalInterfaceType();
        final PsiType lType = ((PsiMethodReferenceType)left).getExpression().getFunctionalInterfaceType();
        return Comparing.equal(rType, lType);
      }
      return !(left instanceof PsiArrayType) && methodReferenceExpression.isAcceptable(left);
    }
    if (right instanceof PsiLambdaExpressionType) {
      final PsiLambdaExpression rLambdaExpression = ((PsiLambdaExpressionType)right).getExpression();
      if (left instanceof PsiLambdaExpressionType) {
        final PsiLambdaExpression lLambdaExpression = ((PsiLambdaExpressionType)left).getExpression();
        final PsiType rType = rLambdaExpression.getFunctionalInterfaceType();
        final PsiType lType = lLambdaExpression.getFunctionalInterfaceType();
        return Comparing.equal(rType, lType);
      }
      return !(left instanceof PsiArrayType) && rLambdaExpression.isAcceptable(left);
    }

    if (left instanceof PsiIntersectionType) {
      PsiType[] conjuncts = ((PsiIntersectionType)left).getConjuncts();
      for (PsiType conjunct : conjuncts) {
        if (!isAssignable(conjunct, right, allowUncheckedConversion, capture)) return false;
      }
      return true;
    }
    if (right instanceof PsiIntersectionType) {
      PsiType[] conjuncts = ((PsiIntersectionType)right).getConjuncts();
      for (PsiType conjunct : conjuncts) {
        if (isAssignable(left, conjunct, allowUncheckedConversion, capture)) return true;
      }
      return false;
    }

    if (right instanceof PsiCapturedWildcardType) {
      return isAssignable(left, ((PsiCapturedWildcardType)right).getUpperBound(capture), allowUncheckedConversion, capture);
    }

    if (left instanceof PsiCapturedWildcardType) {
      return left.equals(right) || isAssignable(((PsiCapturedWildcardType)left).getLowerBound(), right, allowUncheckedConversion, capture);
    }

    if (left instanceof PsiWildcardType) {
      return isAssignableToWildcard((PsiWildcardType)left, right);
    }
    if (right instanceof PsiWildcardType) {
      return isAssignableFromWildcard(left, (PsiWildcardType)right);
    }
    if (right instanceof PsiArrayType) {
      if (!(left instanceof PsiArrayType)) {
        if (left instanceof PsiPrimitiveType || PsiUtil.resolveClassInType(left) == null) return false;
        PsiClass lClass = PsiUtil.resolveClassInType(left);
        if (lClass == null) return false;
        if (lClass.isInterface()) {
          final String qualifiedName = lClass.getQualifiedName();
          return "java.io.Serializable".equals(qualifiedName) || "java.lang.Cloneable".equals(qualifiedName);
        }
        else {
          return left.equalsToText(JAVA_LANG_OBJECT);
        }
      }
      PsiType lCompType = ((PsiArrayType)left).getComponentType();
      PsiType rCompType = ((PsiArrayType)right).getComponentType();
      if (lCompType instanceof PsiPrimitiveType) {
        return lCompType.equals(rCompType);
      }
      return !(rCompType instanceof PsiPrimitiveType) && isAssignable(lCompType, rCompType, allowUncheckedConversion, capture);
    }

    if (left instanceof PsiDisjunctionType) {
      for (PsiType type : ((PsiDisjunctionType)left).getDisjunctions()) {
        if (isAssignable(type, right, allowUncheckedConversion, capture)) return true;
      }
      return false;
    }
    if (right instanceof PsiDisjunctionType) {
      return isAssignable(left, ((PsiDisjunctionType)right).getLeastUpperBound(), allowUncheckedConversion, capture);
    }

    if (left instanceof PsiArrayType) {
      if (right instanceof PsiClassType) {
        PsiClass aClass = ((PsiClassType)right).resolve();
        if (aClass instanceof PsiTypeParameter) {
          PsiType upperBound = getInferredUpperBoundForSynthetic((PsiTypeParameter)aClass);
          return upperBound != null && isAssignable(left, upperBound, allowUncheckedConversion, capture);
        }
      }
      return false;
    }
    if (right instanceof PsiPrimitiveType) {
      if (isVoidType(right)) return false;
      if (!(left instanceof PsiPrimitiveType)) {
        return left instanceof PsiClassType && isBoxable((PsiClassType)left, (PsiPrimitiveType)right);
      }
      int l = TYPE_TO_RANK_MAP.get(left);
      int r = TYPE_TO_RANK_MAP.get(right);
      int leftTypeIndex = (l==-1?0:l) - 1;
      int rightTypeIndex = (r==-1?0:r) - 1;
      return leftTypeIndex >= 0 &&
             rightTypeIndex >= 0 &&
             rightTypeIndex < IS_ASSIGNABLE_BIT_SET.length &&
             leftTypeIndex < IS_ASSIGNABLE_BIT_SET.length &&
             IS_ASSIGNABLE_BIT_SET[rightTypeIndex][leftTypeIndex];
    }
    if (!(right instanceof PsiClassType)) {
      return false; // must be TypeCook's PsiTypeVariable
    }
    if (left instanceof PsiPrimitiveType) {
      return isUnboxable((PsiPrimitiveType)left, (PsiClassType)right, new HashSet<>());
    }
    final PsiClassType.ClassResolveResult leftResult = PsiUtil.resolveGenericsClassInType(left);
    final PsiClassType.ClassResolveResult rightResult = PsiUtil.resolveGenericsClassInType(right);
    PsiClass leftResultElement = leftResult.getElement();
    PsiClass rightResultElement = rightResult.getElement();
    if (leftResultElement == null || rightResultElement == null) {
      if (leftResultElement == null && rightResultElement != null &&
              left instanceof PsiClassType && left.equalsToText(JAVA_LANG_OBJECT)) {
        return true;
      }
      if (leftResultElement != rightResultElement) return false;
      // let's suppose 2 unknown classes, which could be the same to be the same
      String lText = left.getPresentableText();
      String rText = right.getPresentableText();
      if (lText.equals(rText)) return true;
      if (lText.length() > rText.length() && lText.endsWith(rText) &&
          lText.charAt(lText.length() - rText.length() - 1) == '.') {
        return true;
      }
      return rText.length() > lText.length()
             && rText.endsWith(lText)
             && rText.charAt(rText.length() - lText.length() - 1) == '.';
    }
    return isClassAssignable(leftResult, rightResult, allowUncheckedConversion, left.getResolveScope(), capture);
  }

  private static boolean isAssignableFromWildcard(@NotNull PsiType left, @NotNull PsiWildcardType rightWildcardType) {
    if (rightWildcardType.isSuper()) {
      final PsiClass aClass = PsiUtil.resolveClassInType(rightWildcardType.getSuperBound());
      if (aClass instanceof PsiTypeParameter) {
        final PsiClassType[] types = aClass.getExtendsListTypes();
        for (PsiClassType type : types) {
          if (isAssignable(left, type)) return true;
        }
      }
    }
    return isAssignable(left, rightWildcardType.getExtendsBound());
  }

  private static boolean isAssignableToWildcard(@NotNull PsiWildcardType wildcardType, @NotNull PsiType right) {
    if (wildcardType.isSuper()) {
      return isAssignable(wildcardType.getSuperBound(), right);
    }
    return isAssignable(wildcardType.getExtendsBound(), right);
  }

  private static boolean isUnboxable(@NotNull PsiPrimitiveType left, @NotNull PsiClassType right, @NotNull Set<? super PsiClassType> types) {
    if (!right.getLanguageLevel().isAtLeast(LanguageLevel.JDK_1_5)) return false;
    final PsiClass psiClass = right.resolve();
    if (psiClass == null) return false;

    if (psiClass instanceof PsiTypeParameter) {
      for (PsiClassType bound : psiClass.getExtendsListTypes()) {
        if (types.add(bound) && isUnboxable(left, bound, types)) {
          return true;
        }
      }
      return false;
    }

    final PsiPrimitiveType rightUnboxedType = PsiPrimitiveType.getUnboxedType(right);
    return rightUnboxedType != null && isAssignable(left, rightUnboxedType);
  }

  public static boolean boxingConversionApplicable(final PsiType left, final PsiType right) {
    if (left instanceof PsiPrimitiveType && !PsiTypes.nullType().equals(left)) {
      return right instanceof PsiClassType && isAssignable(left, right);
    }

    if (left instanceof PsiIntersectionType) {
      for (PsiType lConjunct : ((PsiIntersectionType)left).getConjuncts()) {
        if (!boxingConversionApplicable(lConjunct, right)) return false;
      }
      return true;
    }

    return left instanceof PsiClassType
              && right instanceof PsiPrimitiveType
              && !PsiTypes.nullType().equals(right)
              && isAssignable(left, right);
  }

  private static final Key<CachedValue<Set<String>>> POSSIBLE_BOXED_HOLDER_TYPES = Key.create("Types that may be possibly assigned from primitive ones");

  private static boolean isBoxable(@NotNull PsiClassType left, @NotNull PsiPrimitiveType right) {
    if (!left.getLanguageLevel().isAtLeast(LanguageLevel.JDK_1_5)) return false;
    final PsiClass psiClass = left.resolve();
    if (psiClass == null) return false;

    final String qname = psiClass.getQualifiedName();
    if (qname == null || !(psiClass instanceof PsiTypeParameter || getAllBoxedTypeSupers(psiClass).contains(qname))) {
      return false;
    }

    final PsiClassType rightBoxed = right.getBoxedType(psiClass.getManager(), left.getResolveScope());
    return rightBoxed != null && isAssignable(left, rightBoxed);
  }

  @NotNull
  private static Set<String> getAllBoxedTypeSupers(@NotNull PsiClass psiClass) {
    PsiManager manager = psiClass.getManager();
    final Project project = psiClass.getProject();
    CachedValue<Set<String>> boxedHolderTypes = project.getUserData(POSSIBLE_BOXED_HOLDER_TYPES);
    if (boxedHolderTypes == null) {
      project.putUserData(POSSIBLE_BOXED_HOLDER_TYPES, boxedHolderTypes = CachedValuesManager.getManager(manager.getProject()).createCachedValue(
        () -> {
          final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
          final Set<String> set = new HashSet<>();
          for (final String qname : JvmPrimitiveTypeKind.getBoxedFqns()) {
            final PsiClass boxedClass = facade.findClass(qname, GlobalSearchScope.allScope(project));
            InheritanceUtil.processSupers(boxedClass, true, psiClass1 -> {
              ContainerUtil.addIfNotNull(set, psiClass1.getQualifiedName());
              return true;
            });
          }
          return CachedValueProvider.Result.create(set, ProjectRootModificationTracker.getInstance(project));
        }, false));
    }

    return boxedHolderTypes.getValue();
  }

  private static boolean isClassAssignable(@NotNull PsiClassType.ClassResolveResult leftResult,
                                           @NotNull PsiClassType.ClassResolveResult rightResult,
                                           boolean allowUncheckedConversion,
                                           GlobalSearchScope resolveScope,
                                           boolean capture) {
    final PsiClass leftClass = leftResult.getElement();
    final PsiClass rightClass = rightResult.getElement();
    if (leftClass == null || rightClass == null) return false;

    PsiSubstitutor superSubstitutor = JavaClassSupers.getInstance().getSuperClassSubstitutor(leftClass, rightClass, resolveScope,
                                                                                             rightResult.getSubstitutor());
    return superSubstitutor != null && typeParametersAgree(leftResult, rightResult, allowUncheckedConversion, superSubstitutor, capture);
  }

  private static boolean typeParametersAgree(@NotNull PsiClassType.ClassResolveResult leftResult,
                                             @NotNull PsiClassType.ClassResolveResult rightResult,
                                             boolean allowUncheckedConversion, PsiSubstitutor superSubstitutor,
                                             boolean capture) {
    PsiSubstitutor rightSubstitutor = rightResult.getSubstitutor();
    PsiClass leftClass = leftResult.getElement();
    PsiClass rightClass = rightResult.getElement();

    Iterator<PsiTypeParameter> li = PsiUtil.typeParametersIterator(leftClass);

    if (!li.hasNext()) return true;
    PsiSubstitutor leftSubstitutor = leftResult.getSubstitutor();

    if (!leftClass.getManager().areElementsEquivalent(leftClass, rightClass)) {
      rightSubstitutor = superSubstitutor;
      rightClass = leftClass;
    }
    else if (!PsiUtil.typeParametersIterator(rightClass).hasNext()) return true;

    Iterator<PsiTypeParameter> ri = PsiUtil.typeParametersIterator(rightClass);
    while (li.hasNext()) {
      if (!ri.hasNext()) return false;
      PsiTypeParameter lp = li.next();
      PsiTypeParameter rp = ri.next();
      final PsiType typeLeft = leftSubstitutor.substitute(lp);
      if (typeLeft == null) continue;
      final PsiType typeRight = PsiCapturedWildcardType.isCapture() && capture
                                ? rightSubstitutor.substituteWithBoundsPromotion(rp)
                                : rightSubstitutor.substitute(rp);
      if (typeRight == null) {
        // compatibility feature: allow to assign raw types to generic ones
        return allowUncheckedConversion;
      }
      if (!typesAgree(typeLeft, typeRight, allowUncheckedConversion)) {
        return false;
      }
    }
    return true;
  }

  private static final RecursionGuard<PsiType> ourGuard = RecursionManager.createGuard("isAssignable");

  public static boolean typesAgree(@NotNull final PsiType typeLeft,
                                   @NotNull final PsiType typeRight,
                                   final boolean allowUncheckedConversion) {
    if (typeLeft instanceof PsiWildcardType) {
      final PsiWildcardType leftWildcard = (PsiWildcardType)typeLeft;
      final PsiType leftBound = leftWildcard.getBound();
      if (leftBound == null) return true;
      if (leftBound.equalsToText(JAVA_LANG_OBJECT)) {
        if (!leftWildcard.isSuper()) return true;
        if (typeRight.equalsToText(JAVA_LANG_OBJECT)) return true;
      }

      if (typeRight instanceof PsiWildcardType) {
        final PsiWildcardType rightWildcard = (PsiWildcardType)typeRight;
        PsiType bound = rightWildcard.getBound();
        if (leftWildcard.isExtends() && bound != null) {
          return rightWildcard.isExtends() && isAssignable(leftBound, bound, allowUncheckedConversion, false);
        }
        else { //isSuper
          if (rightWildcard.isSuper() && bound != null) {
            NotNullComputable<Boolean> checkAssignable = () -> isAssignable(bound, leftBound, allowUncheckedConversion, false);
            final Boolean assignable = ourGuard.doPreventingRecursion(rightWildcard, true, checkAssignable);
            return assignable != null && assignable;
          }
          return false;
        }
      }
      else {
        if (leftWildcard.isExtends()) {
          return isAssignable(leftBound, typeRight, false, false);
        }
        else { // isSuper
          NotNullComputable<Boolean> checkAssignable = () -> isAssignable(typeRight, leftBound, false, false);
          final Boolean assignable = ourGuard.doPreventingRecursion(leftWildcard, true, checkAssignable);
          return assignable == null || assignable.booleanValue();
        }
      }
    }
    else {
      return typeLeft.equals(typeRight);
    }
  }

  @Nullable
  public static PsiSubstitutor getClassSubstitutor(@NotNull PsiClass superClassCandidate,
                                                   @NotNull PsiClass derivedClassCandidate,
                                                   @NotNull PsiSubstitutor derivedSubstitutor) {
    if (superClassCandidate.getManager().areElementsEquivalent(superClassCandidate, derivedClassCandidate)) {
      PsiTypeParameter[] baseParams = superClassCandidate.getTypeParameters();
      PsiTypeParameter[] derivedParams = derivedClassCandidate.getTypeParameters();
      if (baseParams.length > 0 && derivedParams.length == 0) {
        return JavaPsiFacade.getElementFactory(superClassCandidate.getProject()).createRawSubstitutor(superClassCandidate);
      }
      return derivedSubstitutor;
    }
    return getMaybeSuperClassSubstitutor(superClassCandidate, derivedClassCandidate, derivedSubstitutor);
  }

  /**
   * Calculates substitutor that binds type parameters in {@code superClass} with
   * values that they have in {@code derivedClass}, given that type parameters in
   * {@code derivedClass} are bound by {@code derivedSubstitutor}.
   * {@code superClass} must be a super class/interface of {@code derivedClass} (as in
   * {@code InheritanceUtil.isInheritorOrSelf(derivedClass, superClass, true)}
   *
   * @return substitutor (never returns {@code null})
   * @see PsiClass#isInheritor(PsiClass, boolean)
   * @see InheritanceUtil#isInheritorOrSelf(PsiClass, PsiClass, boolean)
   */
  @NotNull
  public static PsiSubstitutor getSuperClassSubstitutor(@NotNull PsiClass superClass,
                                                        @NotNull PsiClass derivedClass,
                                                        @NotNull PsiSubstitutor derivedSubstitutor) {
    if (!superClass.hasTypeParameters() && superClass.getContainingClass() == null) return PsiSubstitutor.EMPTY; //optimization and protection against EJB queer hierarchy

    PsiSubstitutor substitutor = getMaybeSuperClassSubstitutor(superClass, derivedClass, derivedSubstitutor);

    if (substitutor == null) {
      JavaClassSupers.getInstance().reportHierarchyInconsistency(superClass, derivedClass);
      return PsiSubstitutor.EMPTY;
    }
    return substitutor;
  }

  // the same as getSuperClassSubstitutor() but can return null, which means that classes were not inheritors
  @Nullable
  public static PsiSubstitutor getMaybeSuperClassSubstitutor(@NotNull PsiClass superClass,
                                                             @NotNull PsiClass derivedClass,
                                                             @NotNull PsiSubstitutor derivedSubstitutor) {
    return JavaClassSupers.getInstance().getSuperClassSubstitutor(superClass, derivedClass, derivedClass.getResolveScope(), derivedSubstitutor);
  }

  @NotNull
  public static PsiSubstitutor getSuperClassSubstitutor(@NotNull PsiClass superClass, @NotNull PsiClassType classType) {
      final PsiClassType.ClassResolveResult classResolveResult = classType.resolveGenerics();
      return getSuperClassSubstitutor(superClass, classResolveResult.getElement(), classResolveResult.getSubstitutor());
  }

  /**
   * see JLS 5.6.2
   */
  @NotNull
  public static PsiType binaryNumericPromotion(PsiType type1, PsiType type2) {
    type1 = uncapture(type1);
    type2 = uncapture(type2);
    if (isDoubleType(type1)) return unbox(type1);
    if (isDoubleType(type2)) return unbox(type2);
    if (isFloatType(type1)) return unbox(type1);
    if (isFloatType(type2)) return unbox(type2);
    if (isLongType(type1)) return unbox(type1);
    if (isLongType(type2)) return unbox(type2);

    return PsiTypes.intType();
  }

  @NotNull
  private static PsiType unbox(@NotNull PsiType type) {
    if (type instanceof PsiPrimitiveType) return type;
    if (type instanceof PsiClassType) {
      type = PsiPrimitiveType.getUnboxedType(type);
      LOG.assertTrue(type != null);
      return type;
    }
    LOG.error("Invalid type for unboxing "+type);
    return type;
  }

  private static final Set<String> INTEGER_NUMBER_TYPES = new HashSet<>(5);

  static {
    INTEGER_NUMBER_TYPES.add(PsiTypes.byteType().getName());
    INTEGER_NUMBER_TYPES.add(PsiTypes.charType().getName());
    INTEGER_NUMBER_TYPES.add(PsiTypes.longType().getName());
    INTEGER_NUMBER_TYPES.add(PsiTypes.intType().getName());
    INTEGER_NUMBER_TYPES.add(PsiTypes.shortType().getName());
  }

  private static final Set<String> PRIMITIVE_TYPES = new HashSet<>(9);

  static {
    PRIMITIVE_TYPES.add(PsiTypes.voidType().getName());
    PRIMITIVE_TYPES.add(PsiTypes.byteType().getName());
    PRIMITIVE_TYPES.add(PsiTypes.charType().getName());
    PRIMITIVE_TYPES.add(PsiTypes.doubleType().getName());
    PRIMITIVE_TYPES.add(PsiTypes.floatType().getName());
    PRIMITIVE_TYPES.add(PsiTypes.longType().getName());
    PRIMITIVE_TYPES.add(PsiTypes.intType().getName());
    PRIMITIVE_TYPES.add(PsiTypes.shortType().getName());
    PRIMITIVE_TYPES.add(PsiTypes.booleanType().getName());
  }

  private static final Set<String> PRIMITIVE_WRAPPER_FQNS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
    JAVA_LANG_BYTE, JAVA_LANG_CHARACTER, JAVA_LANG_DOUBLE, JAVA_LANG_FLOAT, JAVA_LANG_LONG, JAVA_LANG_INTEGER, JAVA_LANG_SHORT,
    JAVA_LANG_BOOLEAN)));

  private static final Set<String> PRIMITIVE_WRAPPER_SIMPLE_NAMES =
    ContainerUtil.map2Set(PRIMITIVE_WRAPPER_FQNS, StringUtil::getShortName);

  public static boolean isIntegerNumber(@NotNull String typeName) {
    return INTEGER_NUMBER_TYPES.contains(typeName);
  }

  public static boolean isPrimitive(@NotNull String typeName) {
    return PRIMITIVE_TYPES.contains(typeName);
  }

  public static boolean isPrimitiveWrapper(@NotNull String fullyQualifiedName) {
    return PRIMITIVE_WRAPPER_FQNS.contains(fullyQualifiedName);
  }
  @Contract("null -> false")
  public static boolean isAssignableFromPrimitiveWrapper(@Nullable PsiType type) {
    if (type == null) return false;
    if (isPrimitiveWrapper(type)) return true;
    for (PsiType component : type instanceof PsiIntersectionType ? ((PsiIntersectionType)type).getConjuncts() : new PsiType[]{type}) {
      if (!(component instanceof PsiClassType)) return false;
      if (component.equalsToText(JAVA_LANG_OBJECT)) continue;
      if (component.equalsToText(JAVA_LANG_NUMBER)) continue;
      if (component.equalsToText(JAVA_IO_SERIALIZABLE)) continue;
      if (component.equalsToText("java.lang.constant.Constable")) continue;
      if (component.equalsToText("java.lang.constant.ConstantDesc")) continue;
      if (PsiTypesUtil.classNameEquals(component, JAVA_LANG_COMPARABLE)) continue;
      return false;
    }
    return true;
  }

  @Contract("null -> false")
  public static boolean isPrimitiveWrapper(@Nullable PsiType type) {
    if (!(type instanceof PsiClassType)) return false;
    String name = ((PsiClassType)type).getClassName();
    return PRIMITIVE_WRAPPER_SIMPLE_NAMES.contains(name) && isPrimitiveWrapper(type.getCanonicalText());
  }

  @Contract("null -> false")
  public static boolean isComposite(@Nullable PsiType type) {
    return type instanceof PsiDisjunctionType || type instanceof PsiIntersectionType;
  }

  public static PsiType typeParameterErasure(@NotNull PsiTypeParameter typeParameter) {
    return typeParameterErasure(typeParameter, PsiSubstitutor.EMPTY);
  }

  private static PsiType typeParameterErasure(@NotNull PsiTypeParameter typeParameter, @NotNull PsiSubstitutor beforeSubstitutor) {
    final PsiClassType[] extendsList = typeParameter.getExtendsList().getReferencedTypes();
    if (extendsList.length > 0) {
      final PsiClass psiClass = extendsList[0].resolve();
      if (psiClass instanceof PsiTypeParameter) {
        Set<PsiClass> visited = new HashSet<>();
        visited.add(psiClass);
        final PsiTypeParameter boundTypeParameter = (PsiTypeParameter)psiClass;
        if (beforeSubstitutor.getSubstitutionMap().containsKey(boundTypeParameter)) {
          return erasure(beforeSubstitutor.substitute(boundTypeParameter));
        }
        return typeParameterErasureInner(boundTypeParameter, visited, beforeSubstitutor);
      }
      else if (psiClass != null) {
        return JavaPsiFacade.getElementFactory(typeParameter.getProject()).createType(psiClass);
      }
    }
    return PsiType.getJavaLangObject(typeParameter.getManager(), typeParameter.getResolveScope());
  }

  private static PsiClassType typeParameterErasureInner(@NotNull PsiTypeParameter typeParameter,
                                                        @NotNull Set<? super PsiClass> visited,
                                                        @NotNull PsiSubstitutor beforeSubstitutor) {
    final PsiClassType[] extendsList = typeParameter.getExtendsList().getReferencedTypes();
    if (extendsList.length > 0) {
      final PsiClass psiClass = extendsList[0].resolve();
      if (psiClass instanceof PsiTypeParameter) {
        if (!visited.contains(psiClass)) {
          visited.add(psiClass);
          if (beforeSubstitutor.getSubstitutionMap().containsKey(psiClass)) {
            return (PsiClassType)erasure(beforeSubstitutor.substitute((PsiTypeParameter)psiClass));
          }
          return typeParameterErasureInner((PsiTypeParameter)psiClass, visited, beforeSubstitutor);
        }
      }
      else if (psiClass != null) {
        return JavaPsiFacade.getElementFactory(typeParameter.getProject()).createType(psiClass);
      }
    }
    return PsiType.getJavaLangObject(typeParameter.getManager(), typeParameter.getResolveScope());
  }

  @Contract("null -> null")
  public static PsiType erasure(@Nullable PsiType type) {
    return erasure(type, PsiSubstitutor.EMPTY);
  }

  @Contract("null, _ -> null")
  public static PsiType erasure(@Nullable PsiType type, @NotNull PsiSubstitutor beforeSubstitutor) {
    if (type == null) return null;
    return type.accept(new PsiTypeVisitor<PsiType>() {
      @NotNull
      @Override
      public PsiType visitType(@NotNull PsiType type) {
        return type;
      }

      @Override
      public PsiType visitClassType(@NotNull PsiClassType classType) {
        final PsiClass aClass = classType.resolve();
        if (aClass instanceof PsiTypeParameter && !isFreshVariable((PsiTypeParameter)aClass)) {
          return typeParameterErasure((PsiTypeParameter)aClass, beforeSubstitutor);
        }
        return classType.rawType();
      }

      @Override
      public PsiType visitWildcardType(@NotNull PsiWildcardType wildcardType) {
        return wildcardType;
      }

      @Nullable
      @Override
      public PsiType visitCapturedWildcardType(@NotNull PsiCapturedWildcardType capturedWildcardType) {
        return capturedWildcardType.getUpperBound().accept(this);
      }

      @Override
      public PsiType visitPrimitiveType(@NotNull PsiPrimitiveType primitiveType) {
        return primitiveType;
      }

      @Override
      public PsiType visitArrayType(@NotNull PsiArrayType arrayType) {
        final PsiType componentType = arrayType.getComponentType();
        final PsiType newComponentType = componentType.accept(this);
        if (newComponentType == componentType) return arrayType;
        return newComponentType != null ? newComponentType.createArrayType() : null;
      }

      @Override
      public PsiType visitDisjunctionType(@NotNull PsiDisjunctionType disjunctionType) {
        final PsiClassType lub = PsiTypesUtil.getLowestUpperBoundClassType(disjunctionType);
        return lub != null ? erasure(lub, beforeSubstitutor) : disjunctionType;
      }
    });
  }

  public static Object computeCastTo(final Object operand, PsiType castType) {
    if (operand == null || castType == null) return null;
    Object value;
    if (operand instanceof String && castType.equalsToText(JAVA_LANG_STRING) ||
        operand instanceof Boolean && PsiTypes.booleanType().equals(castType)) {
      value = operand;
    }
    else {
      final PsiType primitiveType = wrapperToPrimitive(operand);
      if (primitiveType == null) return null;
      // identity cast, including (boolean)boolValue
      if (castType.equals(primitiveType)) return operand;
      final int rankTo = getTypeRank(castType);
      value = cast(operand, rankTo);
    }
    return value;
  }

  @NotNull
  public static PsiType unboxAndBalanceTypes(PsiType type1, PsiType type2) {
    if (type1 instanceof PsiClassType) type1 = PsiPrimitiveType.getUnboxedType(type1);
    if (type2 instanceof PsiClassType) type2 = PsiPrimitiveType.getUnboxedType(type2);

    if (PsiTypes.doubleType().equals(type1) || PsiTypes.doubleType().equals(type2)) return PsiTypes.doubleType();
    if (PsiTypes.floatType().equals(type1) || PsiTypes.floatType().equals(type2)) return PsiTypes.floatType();
    if (PsiTypes.longType().equals(type1) || PsiTypes.longType().equals(type2)) return PsiTypes.longType();
    return PsiTypes.intType();
  }

  public static IElementType convertEQtoOperation(IElementType eqOpSign) {
    IElementType opSign = null;
    if (eqOpSign == JavaTokenType.ANDEQ) {
      opSign = JavaTokenType.AND;
    }
    else if (eqOpSign == JavaTokenType.ASTERISKEQ) {
      opSign = JavaTokenType.ASTERISK;
    }
    else if (eqOpSign == JavaTokenType.DIVEQ) {
      opSign = JavaTokenType.DIV;
    }
    else if (eqOpSign == JavaTokenType.GTGTEQ) {
      opSign = JavaTokenType.GTGT;
    }
    else if (eqOpSign == JavaTokenType.GTGTGTEQ) {
      opSign = JavaTokenType.GTGTGT;
    }
    else if (eqOpSign == JavaTokenType.LTLTEQ) {
      opSign = JavaTokenType.LTLT;
    }
    else if (eqOpSign == JavaTokenType.MINUSEQ) {
      opSign = JavaTokenType.MINUS;
    }
    else if (eqOpSign == JavaTokenType.OREQ) {
      opSign = JavaTokenType.OR;
    }
    else if (eqOpSign == JavaTokenType.PERCEQ) {
      opSign = JavaTokenType.PERC;
    }
    else if (eqOpSign == JavaTokenType.PLUSEQ) {
      opSign = JavaTokenType.PLUS;
    }
    else if (eqOpSign == JavaTokenType.XOREQ) {
      opSign = JavaTokenType.XOR;
    }
    return opSign;
  }

  public static PsiType calcTypeForBinaryExpression(PsiType lType, PsiType rType, @NotNull IElementType sign, boolean accessLType) {
    if (sign == JavaTokenType.PLUS) {
      // evaluate right argument first, since '+-/*%' is left associative and left operand tends to be bigger
      if (rType == null) return null;
      if (rType.equalsToText(JAVA_LANG_STRING)) {
        return rType;
      }
      if (!accessLType) return NULL_TYPE;
      if (lType == null) return null;
      if (lType.equalsToText(JAVA_LANG_STRING)) {
        return lType;
      }
      return unboxAndBalanceTypes(lType, rType);
    }
    if (sign == JavaTokenType.MINUS || sign == JavaTokenType.ASTERISK || sign == JavaTokenType.DIV || sign == JavaTokenType.PERC) {
      if (rType == null) return null;
      if (!accessLType) return NULL_TYPE;
      if (lType == null) return null;
      return unboxAndBalanceTypes(lType, rType);
    }
    if (sign == JavaTokenType.LTLT || sign == JavaTokenType.GTGT || sign == JavaTokenType.GTGTGT) {
      if (!accessLType) return NULL_TYPE;
      if (lType instanceof PsiClassType) lType = PsiPrimitiveType.getUnboxedType(lType);
      if (PsiTypes.byteType().equals(lType) || PsiTypes.charType().equals(lType) || PsiTypes.shortType().equals(lType)) {
        return PsiTypes.intType();
      }
      return lType;
    }
    if (PsiBinaryExpression.BOOLEAN_OPERATION_TOKENS.contains(sign)) {
      return PsiTypes.booleanType();
    }
    if (sign == JavaTokenType.OR || sign == JavaTokenType.XOR || sign == JavaTokenType.AND) {
      if (rType instanceof PsiClassType) rType = PsiPrimitiveType.getUnboxedType(rType);

      if (lType instanceof PsiClassType) lType = PsiPrimitiveType.getUnboxedType(lType);

      if (rType == null) return null;
      if (PsiTypes.booleanType().equals(rType)) return PsiTypes.booleanType();
      if (!accessLType) return NULL_TYPE;
      if (lType == null) return null;
      if (PsiTypes.booleanType().equals(lType)) return PsiTypes.booleanType();
      if (PsiTypes.longType().equals(lType) || PsiTypes.longType().equals(rType)) return PsiTypes.longType();
      return PsiTypes.intType();
    }
    LOG.error("Unknown token: "+sign);
    return null;
  }

  /**
   * See JLS 3.10.2. Floating-Point Literals
   * @return true  if floating point literal consists of zeros only
   */
  public static boolean isFPZero(@NotNull String text) {
    for (int i = 0; i < text.length(); i++) {
      final char c = text.charAt(i);
      if (Character.isDigit(c) && c != '0') return false;
      final char d = Character.toUpperCase(c);
      if (d == 'E' || d == 'P') break;
    }
    return true;
  }

  public static boolean areSameFreshVariables(@NotNull PsiTypeParameter p1, @NotNull PsiTypeParameter p2) {
    final PsiElement originalContext = p1.getUserData(ORIGINAL_CONTEXT);
    return originalContext != null && originalContext == p2.getUserData(ORIGINAL_CONTEXT);
  }

  public static boolean isFreshVariable(@NotNull PsiTypeParameter typeParameter) {
    return typeParameter.getUserData(ORIGINAL_CONTEXT) != null;
  }

  public static void markAsFreshVariable(@NotNull PsiTypeParameter parameter, PsiElement context) {
    parameter.putUserData(ORIGINAL_CONTEXT, context);
  }

  /**
   * the upper bound for the non-physical (and may be non-denotable) type parameter temporarily created during type inference
   * @see com.intellij.psi.impl.source.resolve.graphInference.InferenceSession
   */
  public static PsiType getInferredUpperBoundForSynthetic(@NotNull PsiTypeParameter psiClass) {
    return psiClass.getUserData(UPPER_BOUND);
  }

  /**
   * the lower bound for the non-physical (and may be non-denotable) type parameter temporarily created during type inference
   * @see com.intellij.psi.impl.source.resolve.graphInference.InferenceSession
   */
  public static PsiType getInferredLowerBoundForSynthetic(@NotNull PsiTypeParameter psiClass) {
    return psiClass.getUserData(LOWER_BOUND);
  }

  public static void setInferredBoundsForSynthetic(@NotNull PsiTypeParameter parameter, PsiType lowerBound, @NotNull PsiType upperBound) {
    assert !parameter.isPhysical() : parameter;
    parameter.putUserData(UPPER_BOUND, upperBound);
    parameter.putUserData(LOWER_BOUND, lowerBound);
  }

  /**
   * Returns true if numeric conversion (widening or narrowing) does not lose the information.
   * This differs slightly from {@link #isAssignable(PsiType, PsiType)} result as some assignable types
   * still may lose the information. E.g. {@code double doubleVar = longVar} may lose round the long value.
   *
   * @param target target type
   * @param source source type
   * @return true if numeric conversion (widening or narrowing) does not lose the information.
   */
  public static boolean isSafeConversion(PsiType target, PsiType source) {
    /*  From \ To  byte short char int long float double
     *  byte        +    +    -    +    +    +    +
     *  short       -    +    -    +    +    +    +
     *  char        -    -    +    +    +    +    +
     *  int         -    -    -    +    +    -    +
     *  long        -    -    -    -    +    -    -
     *  float       -    -    -    -    -    +    +
     *  double      -    -    -    -    -    -    +
     */
    if (target == null || source == null) return false;
    if (target.equals(source)) return true;

    int sourceRank = TYPE_TO_RANK_MAP.get(source);
    int targetRank = TYPE_TO_RANK_MAP.get(target);
    if (sourceRank == -1 || sourceRank > MAX_NUMERIC_RANK ||
        targetRank == -1 || targetRank > MAX_NUMERIC_RANK ||
        !IS_ASSIGNABLE_BIT_SET[sourceRank-1][targetRank-1]) {
      return false;
    }
    if (PsiTypes.intType().equals(source) && PsiTypes.floatType().equals(target)) return false;
    if (PsiTypes.longType().equals(source) && isFloatOrDoubleType(target)) return false;
    return true;
  }

  private static Object cast(@NotNull Object operand, @TypeRank int rankTo) {
    Number number = operand instanceof Character ? Integer.valueOf((Character)operand)
                                                 : operand instanceof Number ? (Number)operand : null;
    if (number == null) return null;
    switch (rankTo) {
      case BYTE_RANK:
        return number.byteValue();
      case SHORT_RANK:
        return number.shortValue();
      case CHAR_RANK:
        return (char)number.intValue();
      case INT_RANK:
        return number.intValue();
      case LONG_RANK:
        return number.longValue();
      case FLOAT_RANK:
        return number.floatValue();
      case DOUBLE_RANK:
        return number.doubleValue();
      default:
        return null;
    }
  }

  private static final Map<Class<?>, PsiType> WRAPPER_TO_PRIMITIVE = new HashMap<>(8);

  static {
    WRAPPER_TO_PRIMITIVE.put(Boolean.class, PsiTypes.booleanType());
    WRAPPER_TO_PRIMITIVE.put(Byte.class, PsiTypes.byteType());
    WRAPPER_TO_PRIMITIVE.put(Character.class, PsiTypes.charType());
    WRAPPER_TO_PRIMITIVE.put(Short.class, PsiTypes.shortType());
    WRAPPER_TO_PRIMITIVE.put(Integer.class, PsiTypes.intType());
    WRAPPER_TO_PRIMITIVE.put(Long.class, PsiTypes.longType());
    WRAPPER_TO_PRIMITIVE.put(Float.class, PsiTypes.floatType());
    WRAPPER_TO_PRIMITIVE.put(Double.class, PsiTypes.doubleType());
  }

  private static PsiType wrapperToPrimitive(@NotNull Object o) {
    return WRAPPER_TO_PRIMITIVE.get(o.getClass());
  }
}
