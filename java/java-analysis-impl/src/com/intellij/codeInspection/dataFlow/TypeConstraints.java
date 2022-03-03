// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.jvm.JvmPsiRangeSetUtil;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.psi.CommonClassNames.*;
import static com.intellij.psi.util.TypeConversionUtil.canConvertSealedTo;

public final class TypeConstraints {
  /**
   * Top constraint (no restriction; any non-primitive value satisfies this)
   */
  public static final TypeConstraint TOP = new TypeConstraint() {
    @NotNull @Override public TypeConstraint join(@NotNull TypeConstraint other) { return this;}
    @NotNull @Override public TypeConstraint tryJoinExactly(@NotNull TypeConstraint other) { return this;}
    @NotNull @Override public TypeConstraint meet(@NotNull TypeConstraint other) { return other; }
    @Override public boolean isSuperConstraintOf(@NotNull TypeConstraint other) { return true; }
    @Override public boolean isSubtypeOf(@NotNull String className) { return false;}
    @Override public TypeConstraint tryNegate() { return BOTTOM; }
    @Override public String toString() { return ""; }
    @Override public DfType getUnboxedType() { return DfType.TOP; }
  };
  /**
   * Bottom constraint (no actual type satisfies this)
   */
  public static final TypeConstraint BOTTOM = new TypeConstraint() {
    @NotNull @Override public TypeConstraint join(@NotNull TypeConstraint other) { return other;}
    @NotNull @Override public TypeConstraint tryJoinExactly(@NotNull TypeConstraint other) { return other;}
    @NotNull @Override public TypeConstraint meet(@NotNull TypeConstraint other) { return this;}
    @Override public boolean isSuperConstraintOf(@NotNull TypeConstraint other) { return other == this; }
    @Override public boolean isSubtypeOf(@NotNull String className) { return false;}
    @Override public TypeConstraint tryNegate() { return TOP; }
    @Override public String toString() { return "<impossible type>"; }
  };

  /**
   * Exactly java.lang.Object class
   */
  public static final TypeConstraint.Exact EXACTLY_OBJECT = new TypeConstraint.Exact() {
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
  };

  @Nullable
  private static TypeConstraint.Exact createExact(@NotNull PsiType type) {
    if (type instanceof PsiArrayType) {
      PsiType componentType = ((PsiArrayType)type).getComponentType();
      if (componentType instanceof PsiPrimitiveType) {
        for (PrimitiveArray p : PrimitiveArray.values()) {
          if (p.getType().equals(componentType)) {
            return p;
          }
        }
        return null;
      }
      TypeConstraint.Exact componentConstraint = createExact(componentType);
      return componentConstraint == null ? null : new ExactArray(componentConstraint);
    }
    if (type instanceof PsiClassType) {
      PsiClass psiClass = ((PsiClassType)type).resolve();
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
    TypeConstraint.Exact exact = createExact(type);
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
      TypeConstraint.Exact exact = createExact(superType);
      if (exact != null && !exact.isFinal()) {
        return new ExactSubclass(id, exact);
      }
    }
    if (superType instanceof PsiIntersectionType) {
      List<TypeConstraint.Exact> supers = new ArrayList<>();
      for (PsiType conjunct : ((PsiIntersectionType)superType).getConjuncts()) {
        TypeConstraint.Exact exact = createExact(conjunct);
        if (exact == null || exact.isFinal()) return BOTTOM;
        supers.add(exact);
      }
      return new ExactSubclass(id, supers.toArray(new TypeConstraint.Exact[0]));
    }
    return BOTTOM;
  }

  public static @NotNull TypeConstraint exactSubtype(@NotNull PsiElement id, @NotNull List<PsiClass> superClasses) {
    TypeConstraint.Exact[] supers = ContainerUtil.map2Array(superClasses, TypeConstraint.Exact.class, cls -> exactClass(cls));
    if (ContainerUtil.or(supers, TypeConstraint.Exact::isFinal)) return BOTTOM;
    if (supers.length == 0) {
      supers = new TypeConstraint.Exact[]{EXACTLY_OBJECT};
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
    if (type instanceof PsiDisjunctionType) {
      type = ((PsiDisjunctionType)type).getLeastUpperBound();
    }
    if (type instanceof PsiIntersectionType) {
      PsiType[] conjuncts = ((PsiIntersectionType)type).getConjuncts();
      TypeConstraint result = TOP;
      for (PsiType conjunct : conjuncts) {
        TypeConstraint.Exact exact = createExact(conjunct);
        if (exact == null) {
          return new Unresolved(type.getCanonicalText()).instanceOf();
        }
        result = result.meet(exact.instanceOf());
      }
      return result;
    }
    TypeConstraint.Exact exact = createExact(type);
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
        PsiType[] types = StreamEx.of(((PsiIntersectionType)normalized).getConjuncts())
          .map(t -> PsiTypesUtil.createArrayType(t, dimensions))
          .toArray(PsiType.EMPTY_ARRAY);
        return PsiIntersectionType.createIntersection(true, types);
      }
      return PsiTypesUtil.createArrayType(normalized, dimensions);
    }
    if (psiType instanceof PsiWildcardType) {
      return normalizeType(((PsiWildcardType)psiType).getExtendsBound());
    }
    if (psiType instanceof PsiCapturedWildcardType) {
      return normalizeType(((PsiCapturedWildcardType)psiType).getUpperBound());
    }
    if (psiType instanceof PsiIntersectionType) {
      PsiType[] types =
        StreamEx.of(((PsiIntersectionType)psiType).getConjuncts()).map(TypeConstraints::normalizeType).toArray(PsiType.EMPTY_ARRAY);
      if (types.length > 0) {
        return PsiIntersectionType.createIntersection(true, types);
      }
    }
    if (psiType instanceof PsiClassType) {
      return normalizeClassType((PsiClassType)psiType, new HashSet<>());
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
  public static TypeConstraint.Exact exactClass(@NotNull PsiClass psiClass) {
    String name = psiClass.getQualifiedName();
    if (name != null) {
      switch (name) {
        case JAVA_LANG_OBJECT:
          return EXACTLY_OBJECT;
        case JAVA_LANG_CLONEABLE:
          return ArraySuperInterface.CLONEABLE;
        case JAVA_IO_SERIALIZABLE:
          return ArraySuperInterface.SERIALIZABLE;
      }
    }
    return new ExactClass(psiClass, false);
  }

  @NotNull
  public static TypeConstraint.Exact singleton(@NotNull PsiClass psiClass) {
    if (!psiClass.hasModifierProperty(PsiModifier.FINAL)) {
      throw new IllegalArgumentException("Singleton class must be final");
    }
    return new ExactClass(psiClass, true);
  }

  private enum PrimitiveArray implements TypeConstraint.Exact {
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

  private enum ArraySuperInterface implements TypeConstraint.Exact {
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
      if (other instanceof ExactClass) {
        return InheritanceUtil.isInheritor(((ExactClass)other).myClass, myReference);
      }
      if (other instanceof ExactSubclass) {
        for (Exact superClass : ((ExactSubclass)other).mySupers) {
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

  private static final class ExactClass implements TypeConstraint.Exact {
    private final @NotNull PsiClass myClass;
    private final boolean mySingleton;

    ExactClass(@NotNull PsiClass aClass, boolean singleton) {
      assert !(aClass instanceof PsiTypeParameter);
      mySingleton = singleton;
      myClass = aClass;
    }

    @Override
    public boolean isEnum() {
      return myClass.isEnum();
    }

    @Override
    public boolean isSingleton() {
      return mySingleton;
    }

    @Override
    public @Nullable PsiEnumConstant getEnumConstant(int ordinal) {
      int cur = 0;
      for (PsiField field : myClass.getFields()) {
        if (field instanceof PsiEnumConstant) {
          if (cur == ordinal) return (PsiEnumConstant)field;
          cur++;
        }
      }
      return null;
    }

    @Override
    public boolean equals(Object obj) {
      return obj == this || obj instanceof ExactClass &&
                            mySingleton == ((ExactClass)obj).mySingleton &&
                            myClass.getManager().areElementsEquivalent(myClass, ((ExactClass)obj).myClass);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(myClass.getName());
    }

    @Override
    public DfType getUnboxedType() {
      String name = myClass.getQualifiedName();
      if (name == null) return DfType.BOTTOM;
      switch (name) {
        case JAVA_LANG_BOOLEAN:
          return DfTypes.BOOLEAN;
        case JAVA_LANG_INTEGER:
          return DfTypes.INT;
        case JAVA_LANG_LONG:
          return DfTypes.LONG;
        case JAVA_LANG_DOUBLE:
          return DfTypes.DOUBLE;
        case JAVA_LANG_FLOAT:
          return DfTypes.FLOAT;
        case JAVA_LANG_BYTE:
          return DfTypes.intRange(Objects.requireNonNull(JvmPsiRangeSetUtil.typeRange(PsiType.BYTE)));
        case JAVA_LANG_SHORT:
          return DfTypes.intRange(Objects.requireNonNull(JvmPsiRangeSetUtil.typeRange(PsiType.SHORT)));
        case JAVA_LANG_CHARACTER:
          return DfTypes.intRange(Objects.requireNonNull(JvmPsiRangeSetUtil.typeRange(PsiType.CHAR)));
        default:
          return DfType.BOTTOM;
      }
    }

    @Override
    public boolean isPrimitiveWrapper() {
      String name = myClass.getQualifiedName();
      return name != null && TypeConversionUtil.isPrimitiveWrapper(name);
    }

    @Override
    public boolean canBeInstantiated() {
      // Abstract final type is incorrect. We, however, assume that final wins: it can be instantiated
      // otherwise TypeConstraints.instanceOf(type) would return impossible type
      return (myClass.hasModifierProperty(PsiModifier.FINAL) || !myClass.hasModifierProperty(PsiModifier.ABSTRACT)) &&
             !JAVA_LANG_VOID.equals(myClass.getQualifiedName());
    }

    @Override
    public boolean isComparedByEquals() {
      String name = myClass.getQualifiedName();
      return name != null && (JAVA_LANG_STRING.equals(name) || TypeConversionUtil.isPrimitiveWrapper(name));
    }

    @NotNull
    @Override
    public PsiType getPsiType(Project project) {
      return JavaPsiFacade.getElementFactory(project).createType(myClass);
    }

    @NotNull
    @Override
    public String toString() {
      String name = myClass.getQualifiedName();
      if (name == null) {
        name = myClass.getName();
      }
      if (name == null && myClass instanceof PsiAnonymousClass) {
        PsiClassType baseClassType = ((PsiAnonymousClass)myClass).getBaseClassType();
        name = "anonymous " + createExact(baseClassType);
      }
      return String.valueOf(name);
    }

    @Override
    public boolean isFinal() {
      return myClass.hasModifierProperty(PsiModifier.FINAL);
    }

    @Override
    public StreamEx<Exact> superTypes() {
      Set<PsiClass> superTypes = new LinkedHashSet<>();
      InheritanceUtil.processSupers(myClass, false, t -> {
        if (!(t instanceof PsiTypeParameter) && !t.hasModifierProperty(PsiModifier.FINAL)) {
          superTypes.add(t);
        }
        return true;
      });
      return StreamEx.of(superTypes).map(TypeConstraints::exactClass);
    }

    @Override
    public boolean isAssignableFrom(@NotNull Exact other) {
      if (equals(other) || other instanceof Unresolved) return true;
      if (other instanceof ExactClass) {
        return InheritanceUtil.isInheritorOrSelf(((ExactClass)other).myClass, myClass, true);
      }
      if (other instanceof ExactSubclass) {
        for (Exact superClass : ((ExactSubclass)other).mySupers) {
          if (isAssignableFrom(superClass)) return true;
        }
      }
      return false;
    }

    @Override
    public boolean isConvertibleFrom(@NotNull Exact other) {
      if (equals(other) || other instanceof Unresolved || other == EXACTLY_OBJECT) return true;
      if (other instanceof ArraySuperInterface) {
        if (myClass.isInterface()) return true;
        if (!myClass.hasModifierProperty(PsiModifier.FINAL)) return true;
        return InheritanceUtil.isInheritor(myClass, ((ArraySuperInterface)other).myReference);
      }
      if (other instanceof ExactClass) {
        PsiClass otherClass = ((ExactClass)other).myClass;
        if (myClass.isInterface() || otherClass.isInterface()) {
          if (otherClass.hasModifierProperty(PsiModifier.SEALED)) return canConvertSealedTo(otherClass, myClass);
          if (myClass.hasModifierProperty(PsiModifier.SEALED)) return canConvertSealedTo(myClass, otherClass);
        }
        if (myClass.isInterface() && otherClass.isInterface()) return true;
        if (myClass.isInterface() && !otherClass.hasModifierProperty(PsiModifier.FINAL)) return true;
        if (otherClass.isInterface() && !myClass.hasModifierProperty(PsiModifier.FINAL)) return true;
        PsiManager manager = myClass.getManager();
        return manager.areElementsEquivalent(myClass, otherClass) ||
               otherClass.isInheritor(myClass, true) ||
               myClass.isInheritor(otherClass, true);
      }
      if (other instanceof ExactSubclass) {
        for (Exact superClass : ((ExactSubclass)other).mySupers) {
          if (isConvertibleFrom(superClass)) return true;
        }
      }
      return false;
    }
  }

  /**
   * Some unknown subclass that has given list of supertypes
   */
  private static final class ExactSubclass implements TypeConstraint.Exact {
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
      if (obj == null || obj.getClass() != this.getClass()) return false;
      ExactSubclass subclass = (ExactSubclass)obj;
      return subclass.myId.equals(myId) && Arrays.equals(subclass.mySupers, mySupers);
    }

    @Override
    public String toString() {
      return "? extends " + StreamEx.of(mySupers).map(Exact::toString).joining(" & ");
    }
  }

  private static final class ExactArray implements TypeConstraint.Exact {
    private final @NotNull Exact myComponent;

    private ExactArray(@NotNull Exact component) {
      myComponent = component;
    }

    @Nullable
    @Override
    public PsiType getPsiType(Project project) {
      PsiType componentType = myComponent.getPsiType(project);
      return componentType == null ? null : componentType.createArrayType();
    }

    @Override
    public boolean equals(Object obj) {
      return obj == this || obj instanceof ExactArray && myComponent.equals(((ExactArray)obj).myComponent);
    }

    @Override
    public int hashCode() {
      return myComponent.hashCode() * 31 + 1;
    }

    @NotNull
    @Override
    public String toString() {
      return myComponent+"[]";
    }

    @Override
    public boolean isFinal() {
      return myComponent.isFinal();
    }

    @Override
    public StreamEx<Exact> superTypes() {
      return myComponent.superTypes().<Exact>map(ExactArray::new).append(ArraySuperInterface.values()).append(EXACTLY_OBJECT);
    }

    @Override
    public boolean isAssignableFrom(@NotNull Exact other) {
      if (!(other instanceof ExactArray)) return false;
      return myComponent.isAssignableFrom(((ExactArray)other).myComponent);
    }

    @Override
    public boolean isConvertibleFrom(@NotNull Exact other) {
      if (other instanceof ExactArray) {
        return myComponent.isConvertibleFrom(((ExactArray)other).myComponent);
      }
      if (other instanceof ArraySuperInterface) return true;
      if (other == EXACTLY_OBJECT) return true;
      return false;
    }

    @Override
    public @NotNull DfType getArrayComponentType() {
      return myComponent.instanceOf().asDfType();
    }

    @Override
    public boolean isArray() {
      return true;
    }
  }

  private static final class Unresolved implements TypeConstraint.Exact {
    private final @NotNull String myReference;

    private Unresolved(@NotNull String reference) {
      myReference = reference;
    }

    @Override
    public boolean isResolved() {
      return false;
    }

    @Override
    public boolean equals(Object obj) {
      return obj == this || obj instanceof Unresolved && myReference.equals(((Unresolved)obj).myReference);
    }

    @Override
    public int hashCode() {
      return myReference.hashCode();
    }

    @NotNull
    @Override
    public String toString() {
      return "<unresolved> "+myReference;
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
}
