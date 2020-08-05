// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class TypeConstraints {
  /**
   * Top constraint (no restriction; any non-primitive value satisfies this)
   */
  public static final TypeConstraint TOP = new TypeConstraint() {
    @NotNull @Override public TypeConstraint join(@NotNull TypeConstraint other) { return this;}
    @NotNull @Override public TypeConstraint meet(@NotNull TypeConstraint other) { return other; }
    @Override public boolean isSuperConstraintOf(@NotNull TypeConstraint other) { return true; }
    @Override public TypeConstraint tryNegate() { return BOTTOM; }
    @Override public String toString() { return ""; }
  };
  /**
   * Bottom constraint (no actual type satisfies this)
   */
  public static final TypeConstraint BOTTOM = new TypeConstraint() {
    @NotNull @Override public TypeConstraint join(@NotNull TypeConstraint other) { return other;}
    @NotNull @Override public TypeConstraint meet(@NotNull TypeConstraint other) { return this;}
    @Override public boolean isSuperConstraintOf(@NotNull TypeConstraint other) { return other == this; }
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
    @Override public String toString() { return CommonClassNames.JAVA_LANG_OBJECT;}

    @Override
    public PsiType getPsiType(Project project) {
      return JavaPsiFacade.getElementFactory(project).createTypeByFQClassName(CommonClassNames.JAVA_LANG_OBJECT);
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
      return PsiTypesUtil.createArrayType(normalizeType(psiType.getDeepComponentType()), psiType.getArrayDimensions());
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
  private static TypeConstraint.Exact exactClass(@NotNull PsiClass psiClass) {
    String name = psiClass.getQualifiedName();
    if (name != null) {
      switch (name) {
        case CommonClassNames.JAVA_LANG_OBJECT:
          return EXACTLY_OBJECT;
        case CommonClassNames.JAVA_LANG_CLONEABLE:
          return ArraySuperInterface.CLONEABLE;
        case CommonClassNames.JAVA_IO_SERIALIZABLE:
          return ArraySuperInterface.SERIALIZABLE;
      }
    }
    return new ExactClass(psiClass);
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
    public boolean isFinal() {
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
    CLONEABLE(CommonClassNames.JAVA_LANG_CLONEABLE),
    SERIALIZABLE(CommonClassNames.JAVA_IO_SERIALIZABLE);
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

    ExactClass(@NotNull PsiClass aClass) {
      assert !(aClass instanceof PsiTypeParameter);
      myClass = aClass;
    }

    @Override
    public boolean equals(Object obj) {
      return obj == this || obj instanceof ExactClass &&
                            myClass.getManager().areElementsEquivalent(myClass, ((ExactClass)obj).myClass);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(myClass.getName());
    }

    @Override
    public boolean canBeInstantiated() {
      // Abstract final type is incorrect. We, however, assume that final wins: it can be instantiated
      // otherwise TypeConstraints.instanceOf(type) would return impossible type
      return (myClass.hasModifierProperty(PsiModifier.FINAL) || !myClass.hasModifierProperty(PsiModifier.ABSTRACT)) &&
             !CommonClassNames.JAVA_LANG_VOID.equals(myClass.getQualifiedName());
    }

    @Override
    public boolean isComparedByEquals() {
      String name = myClass.getQualifiedName();
      return name != null && (CommonClassNames.JAVA_LANG_STRING.equals(name) || TypeConversionUtil.isPrimitiveWrapper(name));
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
      List<Exact> superTypes = new ArrayList<>();
      InheritanceUtil.processSupers(myClass, false, t -> {
        if (!t.hasModifierProperty(PsiModifier.FINAL)) {
          superTypes.add(exactClass(t));
        }
        return true;
      });
      return StreamEx.of(superTypes);
    }

    @Override
    public boolean isAssignableFrom(@NotNull Exact other) {
      if (equals(other) || other instanceof Unresolved) return true;
      if (other instanceof ExactClass) {
        String name = myClass.getQualifiedName();
        if (name == null) return false;
        return InheritanceUtil.isInheritor(((ExactClass)other).myClass, name);
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
        if (myClass.isInterface() && otherClass.isInterface()) return true;
        if (myClass.isInterface() && !otherClass.hasModifierProperty(PsiModifier.FINAL)) return true;
        if (otherClass.isInterface() && !myClass.hasModifierProperty(PsiModifier.FINAL)) return true;
        String otherName = otherClass.getQualifiedName();
        String myName = myClass.getQualifiedName();
        return otherName != null && InheritanceUtil.isInheritor(myClass, otherName) ||
               myName != null && InheritanceUtil.isInheritor(otherClass, myName);
      }
      return false;
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
      if (other instanceof ExactClass) {
        return CommonClassNames.JAVA_LANG_OBJECT.equals(((ExactClass)other).myClass.getQualifiedName());
      }
      return false;
    }

    @Override
    public @NotNull Exact getArrayComponent() {
      return myComponent;
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
