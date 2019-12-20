// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class TypeConstraints {
  public static final TypeConstraint TOP = new TypeConstraint() {
    @NotNull
    @Override
    public TypeConstraint join(@NotNull TypeConstraint other) {
      return this;
    }

    @NotNull
    @Override
    public TypeConstraint meet(@NotNull TypeConstraint other) {
      return other;
    }

    @Override
    public boolean isSuperConstraintOf(@NotNull TypeConstraint other) {
      return true;
    }

    @Override
    public TypeConstraint tryNegate() {
      return BOTTOM;
    }

    @NotNull
    @Override
    public String toString() {
      return "";
    }
  };
  public static final TypeConstraint BOTTOM = new TypeConstraint() {
    @NotNull
    @Override
    public TypeConstraint join(@NotNull TypeConstraint other) {
      return other;
    }

    @NotNull
    @Override
    public TypeConstraint meet(@NotNull TypeConstraint other) {
      return this;
    }

    @Override
    public TypeConstraint tryNegate() {
      return TOP;
    }

    @Override
    public boolean isSuperConstraintOf(@NotNull TypeConstraint other) {
      return other == this;
    }

    @NotNull
    @Override
    public String toString() {
      return "<impossible type>";
    }
  };

  @Nullable
  private static TypeConstraint.Exact createExact(@NotNull PsiType type) {
    if (type instanceof PsiArrayType) {
      PsiType componentType = ((PsiArrayType)type).getComponentType();
      if (componentType instanceof PsiPrimitiveType) {
        return StreamEx.of(PrimitiveArray.values()).findFirst(p -> p.getType().equals(componentType)).orElse(null);
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
   * {@link #BOTTOM} if the object of given type cannot be instantiated
   */
  @NotNull
  public static TypeConstraint exact(@NotNull PsiType type) {
    type = normalizeType(type);
    TypeConstraint.Exact exact = createExact(type);
    if (exact != null && exact.canBeInstantiated()) return exact;
    return BOTTOM;
  }

  @NotNull
  public static TypeConstraint instanceOf(@NotNull PsiType type) {
    type = normalizeType(type);
    if (type instanceof PsiIntersectionType) {
      PsiType[] conjuncts = ((PsiIntersectionType)type).getConjuncts();
      TypeConstraint result = TOP;
      for (PsiType conjunct : conjuncts) {
        TypeConstraint.Exact exact = createExact(conjunct);
        if (exact == null) {
          return new TypeConstraint.Constrained(Collections.singleton(new Unresolved(type.getCanonicalText())), Collections.emptySet());
        }
        result = result.meet(new TypeConstraint.Constrained(Collections.singleton(exact), Collections.emptySet()));
      }
      return result;
    }
    TypeConstraint.Exact exact = createExact(type);
    if (exact == null) return new TypeConstraint.Constrained(Collections.singleton(new Unresolved(type.getCanonicalText())), Collections.emptySet());
    return exact.instanceOf();
  }

  @NotNull
  public static TypeConstraint notInstanceOf(@NotNull PsiType type) {
    type = normalizeType(type);
    TypeConstraint.Exact exact = createExact(type);
    if (exact != null) {
      return new TypeConstraint.Constrained(Collections.emptySet(), Collections.singleton(exact));
    }
    return TOP;
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
  public static TypeConstraint.Exact exactClass(@NotNull PsiClass psiClass) {
    if (psiClass.isInterface()) {
      if (CommonClassNames.JAVA_LANG_CLONEABLE.equals(psiClass.getQualifiedName())) {
        return ArraySuperInterface.CLONEABLE;
      }
      if (CommonClassNames.JAVA_IO_SERIALIZABLE.equals(psiClass.getQualifiedName())) {
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
      return myType+"[]";
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
      return StreamEx.of(ArraySuperInterface.values());
    }

    @Override
    public boolean isAssignableFrom(Exact other) {
      return other.equals(this);
    }

    @Override
    public boolean isConvertibleFrom(Exact other) {
      return other.equals(this) || other.isAssignableFrom(this);
    }

    @Override
    public boolean canBeInstantiated() {
      return true;
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
      return StreamEx.empty();
    }

    @Override
    public boolean isAssignableFrom(Exact other) {
      if (equals(other)) return true;
      if (other instanceof PrimitiveArray || other instanceof ExactArray || other instanceof Unresolved) return true;
      if (other instanceof ExactClass) {
        return InheritanceUtil.isInheritor(((ExactClass)other).myClass, myReference);
      }
      if (other instanceof ExactSubClass) {
        return isAssignableFrom(((ExactSubClass)other).mySuper);
      }
      return false;
    }

    @Override
    public boolean isConvertibleFrom(Exact other) {
      return false;
    }

    @Override
    public boolean canBeInstantiated() {
      return true;
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
      return obj == this || obj instanceof ExactClass && myClass.equals(((ExactClass)obj).myClass);
    }

    @Override
    public int hashCode() {
      return myClass.hashCode();
    }

    @Override
    public TypeConstraint instanceOf() {
      return isObject() ? TOP : Exact.super.instanceOf();
    }

    @Override
    public boolean canBeInstantiated() {
      return !myClass.hasModifierProperty(PsiModifier.ABSTRACT);
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
      // TODO: support anonymous classes
      return String.valueOf(myClass.getQualifiedName());
    }

    @Override
    public boolean isFinal() {
      return myClass.hasModifierProperty(PsiModifier.FINAL);
    }

    @Override
    public StreamEx<Exact> superTypes() {
      List<Exact> superTypes = new ArrayList<>();
      InheritanceUtil.processSupers(myClass, false, t -> {
        if (!CommonClassNames.JAVA_LANG_OBJECT.equals(t.getQualifiedName())) {
          superTypes.add(exactClass(t));
        }
        return true;
      });
      return StreamEx.of(superTypes);
    }

    @Override
    public boolean isAssignableFrom(Exact other) {
      if (equals(other) || isObject() || other instanceof Unresolved) return true;
      if (other instanceof ExactClass) {
        String name = myClass.getQualifiedName();
        if (name == null) return false;
        return InheritanceUtil.isInheritor(((ExactClass)other).myClass, name);
      }
      if (other instanceof ExactSubClass) {
        return isAssignableFrom(((ExactSubClass)other).mySuper);
      }
      return false;
    }

    @Override
    public boolean isConvertibleFrom(Exact other) {
      if (equals(other) || isObject() || other instanceof Unresolved) return true;
      if (other instanceof ArraySuperInterface) {
        if (myClass.isInterface()) return true;
        if (!myClass.hasModifierProperty(PsiModifier.FINAL)) return true;
        return InheritanceUtil.isInheritor(myClass, ((ArraySuperInterface)other).myReference);
      }
      if (other instanceof ExactClass) {
        if (((ExactClass)other).isObject()) return true;
        PsiClass otherClass = ((ExactClass)other).myClass;
        if (myClass.isInterface() && otherClass.isInterface()) return true;
        if (myClass.isInterface() && !otherClass.hasModifierProperty(PsiModifier.FINAL)) return true;
        if (otherClass.isInterface() && !myClass.hasModifierProperty(PsiModifier.FINAL)) return true;
        String otherName = otherClass.getQualifiedName();
        String myName = myClass.getQualifiedName();
        return otherName != null && InheritanceUtil.isInheritor(myClass, otherName) ||
               myName != null && InheritanceUtil.isInheritor(otherClass, myName);
      }
      if (other instanceof ExactSubClass) {
        return isAssignableFrom(((ExactSubClass)other).mySuper);
      }
      return false;
    }

    boolean isObject() {
      return CommonClassNames.JAVA_LANG_OBJECT.equals(myClass.getQualifiedName());
    }
  }

  public static final class ExactSubClass implements TypeConstraint.Exact {
    private final @NotNull ExactClass mySuper;
    private final @NotNull Object myTag;

    ExactSubClass(@NotNull ExactClass aSuper, @NotNull Object tag) {
      mySuper = aSuper;
      myTag = tag;
    }

    @Override
    public boolean equals(Object obj) {
      return obj == this ||
             obj instanceof ExactSubClass &&
             mySuper.equals(((ExactSubClass)obj).mySuper) &&
             myTag.equals(((ExactSubClass)obj).myTag);
    }

    @Override
    public int hashCode() {
      return mySuper.hashCode() * 31 + myTag.hashCode();
    }

    @NotNull
    @Override
    public String toString() {
      return "unknown subclass of " + mySuper;
    }

    @Override
    public boolean isFinal() {
      return true;
    }

    @Override
    public StreamEx<Exact> superTypes() {
      return mySuper.superTypes().prepend(mySuper);
    }

    @Override
    public boolean isAssignableFrom(Exact other) {
      return equals(other);
    }

    @Override
    public boolean isConvertibleFrom(Exact other) {
      return other.isAssignableFrom(this);
    }

    @Override
    public boolean canBeInstantiated() {
      return true;
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
      return myComponent instanceof ExactArray || myComponent instanceof PrimitiveArray ? myComponent.superTypes()
             : myComponent.superTypes().<Exact>map(ExactArray::new).append(ArraySuperInterface.values());
    }

    @Override
    public boolean isAssignableFrom(Exact other) {
      if (!(other instanceof ExactArray)) return false;
      return myComponent.isAssignableFrom(((ExactArray)other).myComponent);
    }
    
    @Override
    public boolean isConvertibleFrom(Exact other) {
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
    public boolean canBeInstantiated() {
      return true;
    }
  }

  private static final class Unresolved implements TypeConstraint.Exact {
    private final @NotNull String myReference;

    private Unresolved(@NotNull String reference) {
      myReference = reference;
    }

    @Override
    public boolean equals(Object obj) {
      return obj == this || obj instanceof Unresolved && myReference.equals(((Unresolved)obj).myReference);
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
      return StreamEx.empty();
    }

    @Override
    public boolean isAssignableFrom(Exact other) {
      return other instanceof Unresolved || other instanceof ExactClass || other instanceof ExactSubClass;
    }

    @Override
    public boolean isConvertibleFrom(Exact other) {
      return other instanceof Unresolved || other instanceof ExactClass || other instanceof ExactSubClass || 
             other instanceof ArraySuperInterface;
    }

    @Override
    public boolean canBeInstantiated() {
      return true;
    }
  }
}
