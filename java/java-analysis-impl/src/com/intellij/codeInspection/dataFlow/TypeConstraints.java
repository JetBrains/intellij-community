// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.TypeConstraint.Exact;
import com.intellij.codeInspection.dataFlow.java.JavaClassDef;
import com.intellij.codeInspection.dataFlow.jvm.JvmPsiRangeSetUtil;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

import static com.intellij.psi.CommonClassNames.*;

public final class TypeConstraints {
  /**
   * Top constraint (no restriction; any non-primitive value satisfies this)
   */
  public static final TypeConstraint TOP = new TopConstraint();
  /**
   * Bottom constraint (no actual type satisfies this)
   */
  public static final TypeConstraint BOTTOM = new BottomConstraint();

  /**
   * Exactly java.lang.Object class
   */
  public static final Exact EXACTLY_OBJECT = new ExactObject();

  @Nullable
  private static Exact createExact(@NotNull PsiType type) {
    if (type instanceof PsiArrayType arrayType) {
      PsiType componentType = arrayType.getComponentType();
      if (componentType instanceof PsiPrimitiveType) {
        for (PrimitiveArray p : PrimitiveArray.values()) {
          if (p.getType().equals(componentType)) {
            return p;
          }
        }
        return null;
      }
      Exact componentConstraint = createExact(componentType);
      return componentConstraint == null ? null : new ExactArray(componentConstraint);
    }
    if (type instanceof PsiClassType classType) {
      PsiClass psiClass = classType.resolve();
      if (psiClass == null) {
        return new Unresolved(type.getCanonicalText());
      }
      if (!(psiClass instanceof PsiTypeParameter)) {
        return exactClass(psiClass);
      }
    }
    return null;
  }

  /**
   * @param type PsiType
   * @return a constraint for the object that has exactly given PsiType;
   * {@link #BOTTOM} if the object of given type cannot be instantiated.
   */
  @NotNull
  @Contract(pure = true)
  public static TypeConstraint exact(@NotNull PsiType type) {
    type = normalizeType(type);
    Exact exact = createExact(type);
    if (exact != null && exact.canBeInstantiated()) return exact;
    return BOTTOM;
  }

  /**
   * Creates an exact final class that has no corresponding PsiClass (e.g. a class of lambda expression)
   *
   * @param id an element that uniquely identifies this subclass
   * @param superType direct supertype
   * @return a type that represents a final subclass that has no corresponding PsiClass.
   * {@link #BOTTOM} if creation of such a subclass is impossible
   */
  public static @NotNull TypeConstraint exactSubtype(@NotNull PsiElement id, @NotNull PsiType superType) {
    superType = normalizeType(superType);
    if (superType instanceof PsiClassType) {
      Exact exact = createExact(superType);
      if (exact != null && !exact.isFinal()) {
        return new ExactSubclass(id, exact);
      }
    }
    if (superType instanceof PsiIntersectionType intersectionType) {
      List<Exact> supers = new ArrayList<>();
      for (PsiType conjunct : intersectionType.getConjuncts()) {
        Exact exact = createExact(conjunct);
        if (exact == null || exact.isFinal()) return BOTTOM;
        supers.add(exact);
      }
      return new ExactSubclass(id, supers.toArray(new Exact[0]));
    }
    return BOTTOM;
  }

  public static @NotNull TypeConstraint exactSubtype(@NotNull PsiElement id, @NotNull List<ClassDef> superClasses) {
    Exact[] supers = ContainerUtil.map2Array(superClasses, Exact.class, cls -> exactClass(cls));
    if (ContainerUtil.or(supers, Exact::isFinal)) return BOTTOM;
    if (supers.length == 0) {
      supers = new Exact[]{EXACTLY_OBJECT};
    }
    return new ExactSubclass(id, supers);
  }

  /**
   * @param type PsiType
   * @return a constraint for the object whose type is the supplied type or any subtype
   */
  @NotNull
  @Contract(pure = true)
  public static TypeConstraint instanceOf(@NotNull PsiType type) {
    if (type instanceof PsiLambdaExpressionType || type instanceof PsiMethodReferenceType) return TOP;
    type = normalizeType(type);
    if (type instanceof PsiDisjunctionType disjunctionType) {
      type = disjunctionType.getLeastUpperBound();
    }
    if (type instanceof PsiIntersectionType intersectionType) {
      PsiType[] conjuncts = intersectionType.getConjuncts();
      TypeConstraint result = TOP;
      for (PsiType conjunct : conjuncts) {
        Exact exact = createExact(conjunct);
        if (exact == null) {
          return new Unresolved(type.getCanonicalText()).instanceOf();
        }
        result = result.meet(exact.instanceOf());
      }
      return result;
    }
    Exact exact = createExact(type);
    if (exact == null) {
      return new Unresolved(type.getCanonicalText()).instanceOf();
    }
    return exact.instanceOf();
  }

  @NotNull
  private static PsiType normalizeType(@NotNull PsiType psiType) {
    if (psiType instanceof PsiArrayType) {
      PsiType normalized = normalizeType(psiType.getDeepComponentType());
      int dimensions = psiType.getArrayDimensions();
      if (normalized instanceof PsiIntersectionType) {
        PsiType[] types = ContainerUtil.map2Array(((PsiIntersectionType)normalized).getConjuncts(), PsiType.EMPTY_ARRAY, t -> PsiTypesUtil.createArrayType(t, dimensions));
        return PsiIntersectionType.createIntersection(true, types);
      }
      return PsiTypesUtil.createArrayType(normalized, dimensions);
    }
    if (psiType instanceof PsiWildcardType wildcardType) {
      return normalizeType(wildcardType.getExtendsBound());
    }
    if (psiType instanceof PsiCapturedWildcardType wildcardType) {
      return normalizeType(wildcardType.getUpperBound());
    }
    if (psiType instanceof PsiIntersectionType intersectionType) {
      PsiType[] types = ContainerUtil.map2Array(intersectionType.getConjuncts(), PsiType.EMPTY_ARRAY, TypeConstraints::normalizeType);
      if (types.length > 0) {
        return PsiIntersectionType.createIntersection(true, types);
      }
    }
    if (psiType instanceof PsiClassType classType) {
      return normalizeClassType(classType, new HashSet<>());
    }
    return psiType;
  }

  @NotNull
  private static PsiType normalizeClassType(@NotNull PsiClassType psiType, Set<PsiClass> processed) {
    PsiClass aClass = psiType.resolve();
    if (aClass instanceof PsiTypeParameter) {
      PsiClassType[] types = aClass.getExtendsListTypes();
      List<PsiType> result = new ArrayList<>();
      for (PsiClassType type : types) {
        PsiClass resolved = type.resolve();
        if (resolved != null && processed.add(resolved)) {
          PsiClassType classType = JavaPsiFacade.getElementFactory(aClass.getProject()).createType(resolved);
          result.add(normalizeClassType(classType, processed));
        }
      }
      if (!result.isEmpty()) {
        return PsiIntersectionType.createIntersection(true, result.toArray(PsiType.EMPTY_ARRAY));
      }
      return PsiType.getJavaLangObject(aClass.getManager(), aClass.getResolveScope());
    }
    return psiType.rawType();
  }

  @NotNull
  public static Exact exactClass(@NotNull PsiClass psiClass) {
    return exactClass(new JavaClassDef(psiClass));
  }

  @NotNull
  public static Exact exactClass(@NotNull ClassDef classDef) {
    String name = classDef.getQualifiedName();
    if (name != null) {
      switch (name) {
        case JAVA_LANG_OBJECT -> {
          return EXACTLY_OBJECT;
        }
        case JAVA_LANG_CLONEABLE -> {
          return ArraySuperInterface.CLONEABLE;
        }
        case JAVA_IO_SERIALIZABLE -> {
          return ArraySuperInterface.SERIALIZABLE;
        }
      }
    }
    return new ExactClass(classDef, false);
  }

  @NotNull
  public static Exact singleton(@NotNull ClassDef classDef) {
    if (!classDef.isFinal()) {
      throw new IllegalArgumentException("Singleton class must be final");
    }
    return new ExactClass(classDef, true);
  }

  public static Exact unresolved(String fqn) {
    return new Unresolved(fqn);
  }

  enum PrimitiveArray implements Exact {
    BOOLEAN(PsiType.BOOLEAN), INT(PsiType.INT),
    BYTE(PsiType.BYTE), SHORT(PsiType.SHORT), LONG(PsiType.LONG),
    CHAR(PsiType.CHAR), FLOAT(PsiType.FLOAT), DOUBLE(PsiType.DOUBLE);
    private final PsiPrimitiveType myType;

    PrimitiveArray(PsiPrimitiveType type) {
      myType = type;
    }

    @NotNull
    @Override
    public PsiType getPsiType(Project project) {
      return myType.createArrayType();
    }

    @NotNull
    @Override
    public String toString() {
      return myType.getCanonicalText()+"[]";
    }

    PsiPrimitiveType getType() {
      return myType;
    }

    @Override
    public @NotNull DfType getArrayComponentType() {
      return DfTypes.typedObject(myType, Nullability.UNKNOWN);
    }

    @Override
    public boolean isFinal() {
      return true;
    }

    @Override
    public boolean isArray() {
      return true;
    }

    @Override
    public StreamEx<Exact> superTypes() {
      return StreamEx.<Exact>of(ArraySuperInterface.values()).append(EXACTLY_OBJECT);
    }

    @Override
    public boolean isAssignableFrom(@NotNull Exact other) {
      return other.equals(this);
    }

    @Override
    public boolean isConvertibleFrom(@NotNull Exact other) {
      return other.equals(this) || other.isAssignableFrom(this);
    }
  }

  enum ArraySuperInterface implements Exact {
    CLONEABLE(JAVA_LANG_CLONEABLE),
    SERIALIZABLE(JAVA_IO_SERIALIZABLE);
    private final @NotNull String myReference;

    ArraySuperInterface(@NotNull String reference) {
      myReference = reference;
    }

    @NotNull
    @Override
    public PsiType getPsiType(Project project) {
      return JavaPsiFacade.getElementFactory(project).createTypeByFQClassName(myReference);
    }

    @NotNull
    @Override
    public String toString() {
      return myReference;
    }

    @Override
    public boolean isFinal() {
      return false;
    }

    @Override
    public StreamEx<Exact> superTypes() {
      return StreamEx.of(EXACTLY_OBJECT);
    }

    @Override
    public boolean isAssignableFrom(@NotNull Exact other) {
      if (equals(other)) return true;
      if (other instanceof PrimitiveArray || other instanceof ExactArray || other instanceof Unresolved) return true;
      if (other instanceof ExactClass exactClass) {
        return exactClass.classDef.isInheritor(myReference);
      }
      if (other instanceof ExactSubclass subclass) {
        for (Exact superClass : subclass.mySupers) {
          if (isAssignableFrom(superClass)) return true;
        }
      }
      return false;
    }

    @Override
    public boolean isConvertibleFrom(@NotNull Exact other) {
      return !other.isFinal() || isAssignableFrom(other);
    }

    @Override
    public boolean canBeInstantiated() {
      return false;
    }
  }

  record ExactClass(@NotNull ClassDef classDef, boolean isSingleton) implements Exact {
    ExactClass {
      assert !(classDef instanceof PsiTypeParameter);
    }

    @Override
    public @NotNull Exact convert(TypeConstraintFactory factory) {
      String qualifiedName = classDef.getQualifiedName();
      if (qualifiedName != null) {
        return factory.create(qualifiedName);
      }
      return unresolved(classDef.toString());
    }

    @Override
    public boolean isEnum() {
      return classDef.isEnum();
    }

    @Override
    public @Nullable PsiEnumConstant getEnumConstant(int ordinal) {
      return classDef.getEnumConstant(ordinal);
    }

    @Override
    public DfType getUnboxedType() {
      String name = classDef.getQualifiedName();
      if (name == null) return DfType.BOTTOM;
      return switch (name) {
        case JAVA_LANG_BOOLEAN -> DfTypes.BOOLEAN;
        case JAVA_LANG_INTEGER -> DfTypes.INT;
        case JAVA_LANG_LONG -> DfTypes.LONG;
        case JAVA_LANG_DOUBLE -> DfTypes.DOUBLE;
        case JAVA_LANG_FLOAT -> DfTypes.FLOAT;
        case JAVA_LANG_BYTE -> DfTypes.intRange(Objects.requireNonNull(JvmPsiRangeSetUtil.typeRange(PsiType.BYTE)));
        case JAVA_LANG_SHORT -> DfTypes.intRange(Objects.requireNonNull(JvmPsiRangeSetUtil.typeRange(PsiType.SHORT)));
        case JAVA_LANG_CHARACTER -> DfTypes.intRange(Objects.requireNonNull(JvmPsiRangeSetUtil.typeRange(PsiType.CHAR)));
        default -> DfType.BOTTOM;
      };
    }

    @Override
    public boolean isPrimitiveWrapper() {
      String name = classDef.getQualifiedName();
      return name != null && TypeConversionUtil.isPrimitiveWrapper(name);
    }

    @Override
    public boolean canBeInstantiated() {
      // Abstract final type is incorrect. We, however, assume that final wins: it can be instantiated
      // otherwise TypeConstraints.instanceOf(type) would return impossible type
      return (classDef.isFinal() || !classDef.isAbstract()) && !JAVA_LANG_VOID.equals(classDef.getQualifiedName());
    }

    @Override
    public boolean isComparedByEquals() {
      String name = classDef.getQualifiedName();
      return name != null && (JAVA_LANG_STRING.equals(name) || TypeConversionUtil.isPrimitiveWrapper(name));
    }

    @Nullable
    @Override
    public PsiType getPsiType(Project project) {
      return classDef.toPsiType(project);
    }

    @NotNull
    @Override
    public String toString() {
      return classDef.toString();
    }

    @Override
    public boolean isFinal() {
      return classDef.isFinal();
    }

    @Override
    public StreamEx<Exact> superTypes() {
      return StreamEx.of(classDef.superTypes()).map(TypeConstraints::exactClass);
    }

    @Override
    public boolean isAssignableFrom(@NotNull Exact other) {
      if (equals(other) || other instanceof Unresolved) return true;
      if (other instanceof ExactClass exactClass) {
        return exactClass.classDef.isInheritor(classDef);
      }
      if (other instanceof ExactSubclass subclass) {
        for (Exact superClass : subclass.mySupers) {
          if (isAssignableFrom(superClass)) return true;
        }
      }
      return false;
    }

    @Override
    public boolean isConvertibleFrom(@NotNull Exact other) {
      if (equals(other) || other instanceof Unresolved || other == EXACTLY_OBJECT) return true;
      if (other instanceof ArraySuperInterface) {
        if (classDef.isInterface()) return true;
        if (!classDef.isFinal()) return true;
        return classDef.isInheritor(((ArraySuperInterface)other).myReference);
      }
      if (other instanceof ExactClass exactClass) {
        return classDef.isConvertible(exactClass.classDef);
      }
      if (other instanceof ExactSubclass subclass) {
        for (Exact superClass : subclass.mySupers) {
          if (isConvertibleFrom(superClass)) return true;
        }
      }
      return false;
    }
  }

  /**
   * Some unknown subclass that has given list of supertypes
   */
  static final class ExactSubclass implements Exact {
    private final @NotNull Exact @NotNull[] mySupers;
    private final @NotNull Object myId;

    ExactSubclass(@NotNull Object id, @NotNull Exact @NotNull ... supers) {
      assert supers.length != 0;
      for (Exact superClass : supers) {
        if (!(superClass instanceof ExactClass ||
              superClass instanceof ArraySuperInterface ||
              superClass instanceof Unresolved ||
              superClass == EXACTLY_OBJECT)) {
          throw new IllegalArgumentException("Unexpected supertype: "+superClass);
        }
      }
      mySupers = supers;
      myId = id;
    }

    @Override
    public @NotNull Exact convert(TypeConstraintFactory factory) {
      return new ExactSubclass(myId, ContainerUtil.map2Array(mySupers, Exact.class, ex -> ex.convert(factory)));
    }

    @Override
    public boolean isFinal() {
      return true;
    }

    @Override
    public StreamEx<Exact> superTypes() {
      return StreamEx.of(mySupers).flatMap(Exact::superTypes).distinct();
    }

    @Override
    public boolean isAssignableFrom(@NotNull Exact other) {
      return equals(other);
    }

    @Override
    public boolean isConvertibleFrom(@NotNull Exact other) {
      return equals(other) || other.isAssignableFrom(this);
    }

    @Override
    public String toShortString() {
      return "? extends " + StreamEx.of(mySupers).map(Exact::toShortString).joining(" & ");
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(mySupers) * 31 + myId.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) return true;
      return obj instanceof ExactSubclass subclass && subclass.myId.equals(myId) && Arrays.equals(subclass.mySupers, mySupers);
    }

    @Override
    public String toString() {
      return "? extends " + StreamEx.of(mySupers).map(Exact::toString).joining(" & ");
    }
  }

  record ExactArray(@NotNull Exact component) implements Exact {
    @Override
    public @NotNull Exact convert(TypeConstraintFactory factory) {
      Exact component = this.component.convert(factory);
      return component == this.component ? this : new ExactArray(component);
    }

    @Nullable
    @Override
    public PsiType getPsiType(Project project) {
      PsiType componentType = component.getPsiType(project);
      return componentType == null ? null : componentType.createArrayType();
    }

    @NotNull
    @Override
    public String toString() {
      return component + "[]";
    }

    @Override
    public boolean isFinal() {
      return component.isFinal();
    }

    @Override
    public StreamEx<Exact> superTypes() {
      return component.superTypes().<Exact>map(ExactArray::new).append(ArraySuperInterface.values()).append(EXACTLY_OBJECT);
    }

    @Override
    public boolean isAssignableFrom(@NotNull Exact other) {
      return other instanceof ExactArray exactArray && component.isAssignableFrom(exactArray.component);
    }

    @Override
    public boolean isConvertibleFrom(@NotNull Exact other) {
      if (other instanceof ExactArray exactArray) {
        return component.isConvertibleFrom(exactArray.component);
      }
      if (other instanceof ArraySuperInterface) return true;
      if (other == EXACTLY_OBJECT) return true;
      return false;
    }

    @Override
    public @NotNull DfType getArrayComponentType() {
      return component.instanceOf().asDfType();
    }

    @Override
    public boolean isArray() {
      return true;
    }
  }

  record Unresolved(@NotNull String reference) implements Exact {
    @Override
    public boolean isResolved() {
      return false;
    }

    @NotNull
    @Override
    public String toString() {
      return "<unresolved> " + reference;
    }

    @Override
    public boolean isFinal() {
      return false;
    }

    @Override
    public StreamEx<Exact> superTypes() {
      return StreamEx.of(EXACTLY_OBJECT);
    }

    @Override
    public boolean isAssignableFrom(@NotNull Exact other) {
      return other instanceof Unresolved || other instanceof ExactClass;
    }

    @Override
    public boolean isConvertibleFrom(@NotNull Exact other) {
      return other instanceof Unresolved || other instanceof ExactClass || other instanceof ArraySuperInterface;
    }
  }

  /**
   * Ab abstraction layer for Class definition in JVM language necessary to support TypeConstraints
   * It's expected that only one implementation of ClassDef should be used during the same analysis.
   * Different implementations should not meet each other (e.g., in {@link #isConvertible(ClassDef)},
   * in {@link #isInheritor(ClassDef)}, or in {@link #equals(Object)} calls).
   */
  public interface ClassDef {
    boolean isInheritor(@NotNull String superClassQualifiedName);
    boolean isInheritor(@NotNull ClassDef superType);
    boolean isConvertible(@NotNull ClassDef other);
    boolean isInterface();
    boolean isEnum();
    boolean isFinal();
    boolean isAbstract();

    /**
     * @param ordinal enum constant ordinal
     * @return a PsiElement that represents the corresponding enum constant; null if current ClassDef is not an enum,
     * or ordinal is out of bounds
     */
    @Nullable PsiEnumConstant getEnumConstant(int ordinal);

    /**
     * @return a Java fully-qualified name, like "java.lang.String". If another JVM language maps its own custom name
     * to JVM class, then the Java name of JVM class must be returned (e.g. "java.lang.String" instead of "kotlin.String")
     */
    @Nullable String getQualifiedName();

    /**
     * @return stream containing all super-types (non-repeating), including classes and interfaces
     */
    @NotNull Stream<@NotNull ClassDef> superTypes();

    /**
     * @param project current project
     * @return a PsiType that corresponds to this class; null if the type cannot be created
     */
    @Nullable PsiType toPsiType(@NotNull Project project);
  }

  @FunctionalInterface
  public interface TypeConstraintFactory {
    @NotNull Exact create(@NotNull String fqn);
  }

  static final class TopConstraint implements TypeConstraint {
    @NotNull @Override public TypeConstraint join(@NotNull TypeConstraint other) { return this;}

    @NotNull @Override public TypeConstraint tryJoinExactly(@NotNull TypeConstraint other) { return this;}

    @NotNull @Override public TypeConstraint meet(@NotNull TypeConstraint other) { return other; }

    @Override public boolean isSuperConstraintOf(@NotNull TypeConstraint other) { return true; }

    @Override public boolean isSubtypeOf(@NotNull String className) { return false;}

    @Override public TypeConstraint tryNegate() { return BOTTOM; }

    @Override public String toString() { return ""; }

    @Override public DfType getUnboxedType() { return DfType.TOP; }

    @Override public @NotNull TypeConstraint arrayOf() { return new ExactArray(EXACTLY_OBJECT).instanceOf(); }
  }

  static final class BottomConstraint implements TypeConstraint {
    @NotNull @Override public TypeConstraint join(@NotNull TypeConstraint other) { return other;}

    @NotNull @Override public TypeConstraint tryJoinExactly(@NotNull TypeConstraint other) { return other;}

    @NotNull @Override public TypeConstraint meet(@NotNull TypeConstraint other) { return this;}

    @Override public boolean isSuperConstraintOf(@NotNull TypeConstraint other) { return other == this; }

    @Override public boolean isSubtypeOf(@NotNull String className) { return false;}

    @Override public TypeConstraint tryNegate() { return TOP; }

    @Override public String toString() { return "<impossible type>"; }
  }

  static final class ExactObject implements Exact {
    @Override public StreamEx<Exact> superTypes() { return StreamEx.empty();}

    @Override public boolean isFinal() { return false;}

    @Override public boolean isAssignableFrom(@NotNull Exact other) { return true;}

    @Override public boolean isConvertibleFrom(@NotNull Exact other) { return true;}

    @NotNull @Override public TypeConstraint instanceOf() { return TOP;}

    @NotNull @Override public TypeConstraint notInstanceOf() { return BOTTOM;}

    @Override public String toString() { return JAVA_LANG_OBJECT;}

    @Override
    public PsiType getPsiType(Project project) {
      return JavaPsiFacade.getElementFactory(project).createTypeByFQClassName(JAVA_LANG_OBJECT);
    }
  }
}
