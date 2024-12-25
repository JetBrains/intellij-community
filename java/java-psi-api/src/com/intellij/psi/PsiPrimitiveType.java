// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.lang.jvm.types.JvmPrimitiveType;
import com.intellij.lang.jvm.types.JvmPrimitiveTypeKind;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Represents primitive types of Java language.
 */
public final class PsiPrimitiveType extends PsiType.Stub implements JvmPrimitiveType {
  private static final Map<String, PsiPrimitiveType> ourQNameToUnboxed = new HashMap<>();

  private final JvmPrimitiveTypeKind myKind;
  private final String myName;

  /**
   * This constructor stores PsiType.XXX primitive types instances in a map for later reusing.
   * PsiType.NULL is not registered and handled separately since it's not really a primitive type.
   */
  PsiPrimitiveType(@Nullable("for NULL type") JvmPrimitiveTypeKind kind) {
    super(PsiAnnotation.EMPTY_ARRAY);
    myKind = kind;
    if (kind != null) {
      ourQNameToUnboxed.put(kind.getBoxedFqn(), this);
    }
    myName = getName(kind);
  }

  public PsiPrimitiveType(@Nullable("for NULL type") JvmPrimitiveTypeKind kind, PsiAnnotation @NotNull [] annotations) {
    super(annotations);
    myKind = kind;
    myName = getName(kind);
  }

  public PsiPrimitiveType(@Nullable("for NULL type") JvmPrimitiveTypeKind kind, @NotNull TypeAnnotationProvider provider) {
    super(provider);
    myKind = kind;
    myName = getName(kind);
  }

  /**
   * @param name valid {@link JvmPrimitiveTypeKind#getName primitive name}, or NoSuchElementException will be thrown
   */
  public PsiPrimitiveType(@NotNull String name, @NotNull TypeAnnotationProvider provider) {
    super(provider);
    JvmPrimitiveTypeKind kind = JvmPrimitiveTypeKind.getKindByName(name);
    if (kind == null) throw new NoSuchElementException("Cannot find primitive type: " + name);
    myKind = kind;
    myName = name;
  }

  @Contract(pure = true)
  private static @NotNull String getName(@Nullable JvmPrimitiveTypeKind kind) {
    return kind == null ? "null" : kind.getName();
  }

  @Override
  public @NotNull JvmPrimitiveTypeKind getKind() {
    return Objects.requireNonNull(
      myKind,
      "getKind() called on PsiType.NULL\n" +
      "If your code works with JvmElement API then this should not happen " +
      "unless some implementation improperly returns PsiType.NULL " +
      "from JvmMethod.getReturnType() (or any other available methods).\n" +
      "If your code works with PsiType-s then you must check " +
      "if this type is PsiType.NULL type before calling this method"
    );
  }

  public @NotNull String getName() {
    return myName;
  }

  @Override
  public @NotNull PsiPrimitiveType annotate(@NotNull TypeAnnotationProvider provider) {
    return (PsiPrimitiveType)super.annotate(provider);
  }

  @Override
  public @NotNull String getPresentableText(boolean annotated) {
    return getText(false, annotated);
  }

  @Override
  public @NotNull String getCanonicalText(boolean annotated) {
    return getText(true, annotated);
  }

  @Override
  public @NotNull String getInternalCanonicalText() {
    return getCanonicalText(true);
  }

  private String getText(boolean qualified, boolean annotated) {
    PsiAnnotation[] annotations = annotated ? getAnnotations() : PsiAnnotation.EMPTY_ARRAY;
    if (annotations.length == 0) return myName;

    StringBuilder sb = new StringBuilder();
    PsiNameHelper.appendAnnotations(sb, annotations, qualified);
    sb.append(myName);
    return sb.toString();
  }

  /**
   * Always returns true.
   */
  @Override
  public boolean isValid() {
    for (PsiAnnotation annotation : getAnnotations()) {
      if (!annotation.isValid()) return false;
    }
    return true;
  }

  @Override
  public boolean equalsToText(@NotNull String text) {
    return myName.equals(text);
  }

  @Override
  public <A> A accept(@NotNull PsiTypeVisitor<A> visitor) {
    return visitor.visitPrimitiveType(this);
  }

  @Override
  public GlobalSearchScope getResolveScope() {
    return null;
  }

  @Override
  public PsiType @NotNull [] getSuperTypes() {
    return EMPTY_ARRAY;
  }

  /**
   * Returns the primitive type corresponding to a boxed class type.
   *
   * @param type the type to get the unboxed primitive type for.
   * @return the primitive type, or null if the type does not represent a boxed primitive type.
   */
  public static @Nullable PsiPrimitiveType getUnboxedType(@Nullable PsiType type) {
    if (!(type instanceof PsiClassType)) return null;

    PsiUtil.ensureValidType(type);
    LanguageLevel languageLevel = ((PsiClassType)type).getLanguageLevel();
    if (!languageLevel.isAtLeast(LanguageLevel.JDK_1_5)) return null;

    PsiClass psiClass = ((PsiClassType)type).resolve();
    if (psiClass == null) return null;

    PsiPrimitiveType unboxed = ourQNameToUnboxed.get(psiClass.getQualifiedName());
    if (unboxed == null) return null;

    return unboxed.annotate(type.getAnnotationProvider());
  }

  public static @Nullable PsiPrimitiveType getOptionallyUnboxedType(@Nullable PsiType type) {
    return type instanceof PsiPrimitiveType ? (PsiPrimitiveType)type : getUnboxedType(type);
  }

  /**
   * @param descriptor one letter JVM type descriptor ('B' for byte, 'J' for long, etc.)
   * @return corresponding primitive type, null if the supplied character is not a valid JVM type descriptor
   */
  @Contract(pure = true)
  public static @Nullable PsiPrimitiveType fromJvmTypeDescriptor(char descriptor) {
    switch (descriptor) {
      case 'B':
        return PsiTypes.byteType();
      case 'C':
        return PsiTypes.charType();
      case 'D':
        return PsiTypes.doubleType();
      case 'F':
        return PsiTypes.floatType();
      case 'Z':
        return PsiTypes.booleanType();
      case 'I':
        return PsiTypes.intType();
      case 'J':
        return PsiTypes.longType();
      case 'S':
        return PsiTypes.shortType();
      default:
        return null;
    }
  }

  /**
   * This method is nullable since {@link PsiTypes#nullType()} has no FQN.<br/>
   * Consider using {@link JvmPrimitiveTypeKind#getBoxedFqn()} if you know the type you need to get FQN of,
   * e.g. instead of {@code PsiType.INT.getBoxedTypeName()} use {@code JvmPrimitiveTypeKind.INT.getBoxedFqn()}.
   *
   * @see JvmPrimitiveTypeKind#getBoxedFqn
   */
  public @Nullable String getBoxedTypeName() {
    return myKind == null ? null : myKind.getBoxedFqn();
  }

  /**
   * Returns a boxed class type corresponding to the primitive type.
   *
   * @param context where this boxed type is to be used
   * @return the class type, or null if the current language level does not support autoboxing or
   * it was not possible to resolve the reference to the class.
   */
  public @Nullable PsiClassType getBoxedType(@NotNull PsiElement context) {
    PsiFile file = context.getContainingFile();
    if (file == null) return null;
    LanguageLevel languageLevel = PsiUtil.getLanguageLevel(file);
    if (!languageLevel.isAtLeast(LanguageLevel.JDK_1_5)) return null;

    String boxedQName = getBoxedTypeName();
    if (boxedQName == null) return null;

    JavaPsiFacade facade = JavaPsiFacade.getInstance(file.getProject());
    PsiClass aClass = facade.findClass(boxedQName, file.getResolveScope());
    if (aClass == null) return null;

    PsiElementFactory factory = facade.getElementFactory();
    return factory.createType(aClass, PsiSubstitutor.EMPTY, languageLevel).annotate(getAnnotationProvider());
  }

  public @Nullable PsiClassType getBoxedType(@NotNull PsiManager manager, @NotNull GlobalSearchScope resolveScope) {
    String boxedQName = getBoxedTypeName();
    if (boxedQName == null) return null;

    PsiClass aClass = JavaPsiFacade.getInstance(manager.getProject()).findClass(boxedQName, resolveScope);
    if (aClass == null) return null;

    return JavaPsiFacade.getElementFactory(manager.getProject()).createType(aClass);
  }

  @Override
  public int hashCode() {
    return myKind == null ? 0 : myKind.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return this == obj || obj instanceof PsiPrimitiveType && myKind == ((PsiPrimitiveType)obj).myKind;
  }
}
