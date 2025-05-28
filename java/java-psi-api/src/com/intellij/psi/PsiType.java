// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.codeInsight.TypeNullability;
import com.intellij.lang.jvm.types.JvmPrimitiveTypeKind;
import com.intellij.lang.jvm.types.JvmType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.*;

/**
 * Representation of a Java type (primitive type, array or class type).
 * <p/>
 * <h3><a id="deprecated-constants">Deprecated constants</a></h3>
 * All static fields in this class representing instances of {@link PsiPrimitiveType} are deprecated. It was done to avoid deadlocks 
 * during initialization of the class. According to <a href="https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.5">section 5.5</a>
 * of JVM specification, when a class is initialized, JVM firstly synchronizes on an initialization lock specific for that class, then
 * initializes its super class and then computes initializers for its static fields. So because of these fields initialization of {@link PsiType}
 * performs initialization of {@link PsiPrimitiveType}, and initialization of {@link PsiPrimitiveType} performs initialization of {@link PsiType}
 * because it's the super class of its super class. Therefore, if one thread starts initialization of {@link PsiType}, and another thread
 * starts initialization of {@link PsiPrimitiveType} at the same time, it will result in a deadlock. To avoid this, methods from
 * {@link PsiTypes} must be used to get instances of the primitive types.
 */
@SuppressWarnings("StaticInitializerReferencesSubClass")
public abstract class PsiType implements PsiAnnotationOwner, Cloneable, JvmType {
  /** @deprecated use {@link PsiTypes#byteType()} instead, see <a href="#deprecated-constants">Deprecated Constants</a> for details. */
  @Deprecated @ApiStatus.ScheduledForRemoval
  public static final PsiPrimitiveType BYTE = new PsiPrimitiveType(JvmPrimitiveTypeKind.BYTE);
  /** @deprecated use {@link PsiTypes#charType()} instead, see <a href="#deprecated-constants">Deprecated Constants</a> for details. */
  @Deprecated @ApiStatus.ScheduledForRemoval
  public static final PsiPrimitiveType CHAR = new PsiPrimitiveType(JvmPrimitiveTypeKind.CHAR);
  /** @deprecated use {@link PsiTypes#doubleType()} instead, see <a href="#deprecated-constants">Deprecated Constants</a> for details. */
  @Deprecated @ApiStatus.ScheduledForRemoval
  public static final PsiPrimitiveType DOUBLE = new PsiPrimitiveType(JvmPrimitiveTypeKind.DOUBLE);
  /** @deprecated use {@link PsiTypes#floatType()} instead, see <a href="#deprecated-constants">Deprecated Constants</a> for details. */
  @Deprecated @ApiStatus.ScheduledForRemoval
  public static final PsiPrimitiveType FLOAT = new PsiPrimitiveType(JvmPrimitiveTypeKind.FLOAT);
  /** @deprecated use {@link PsiTypes#intType()} instead, see <a href="#deprecated-constants">Deprecated Constants</a> for details. */
  @Deprecated @ApiStatus.ScheduledForRemoval
  public static final PsiPrimitiveType INT = new PsiPrimitiveType(JvmPrimitiveTypeKind.INT);
  /** @deprecated use {@link PsiTypes#longType()} instead, see <a href="#deprecated-constants">Deprecated Constants</a> for details. */
  @Deprecated @ApiStatus.ScheduledForRemoval
  public static final PsiPrimitiveType LONG = new PsiPrimitiveType(JvmPrimitiveTypeKind.LONG);
  /** @deprecated use {@link PsiTypes#shortType()} instead, see <a href="#deprecated-constants">Deprecated Constants</a> for details. */
  @Deprecated @ApiStatus.ScheduledForRemoval
  public static final PsiPrimitiveType SHORT = new PsiPrimitiveType(JvmPrimitiveTypeKind.SHORT);
  /** @deprecated use {@link PsiTypes#booleanType()} instead, see <a href="#deprecated-constants">Deprecated Constants</a> for details. */
  @Deprecated @ApiStatus.ScheduledForRemoval
  public static final PsiPrimitiveType BOOLEAN = new PsiPrimitiveType(JvmPrimitiveTypeKind.BOOLEAN);
  /** @deprecated use {@link PsiTypes#voidType()} instead, see <a href="#deprecated-constants">Deprecated Constants</a> for details. */
  @Deprecated @ApiStatus.ScheduledForRemoval
  public static final PsiPrimitiveType VOID = new PsiPrimitiveType(JvmPrimitiveTypeKind.VOID);
  /** @deprecated use {@link PsiTypes#nullType()} instead, see <a href="#deprecated-constants">Deprecated Constants</a> for details. */
  @Deprecated @ApiStatus.ScheduledForRemoval
  public static final PsiPrimitiveType NULL = new PsiPrimitiveType(null);

  public static final PsiType[] EMPTY_ARRAY = new PsiType[0];
  public static final ArrayFactory<PsiType> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new PsiType[count];

  public static PsiType @NotNull [] createArray(int count) {
    return ARRAY_FACTORY.create(count);
  }

  private TypeAnnotationProvider myAnnotationProvider;

  /**
   * Constructs a PsiType with given annotations
   */
  protected PsiType(final PsiAnnotation @NotNull [] annotations) {
    this(TypeAnnotationProvider.Static.create(annotations));
  }

  /**
   * Constructs a PsiType that will take its annotations from the given annotation provider.
   */
  protected PsiType(@NotNull TypeAnnotationProvider annotations) {
    myAnnotationProvider = annotations;
  }

  public @NotNull PsiType annotate(@NotNull TypeAnnotationProvider provider) {
    if (provider == myAnnotationProvider) return this;

    try {
      PsiType copy = (PsiType)clone();
      copy.myAnnotationProvider = provider.withOwner(copy);
      return copy;
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns a type with the specified nullability. May return the original type if nullability update
   * cannot be performed (e.g., for primitive type)
   * 
   * @param nullability wanted nullability
   * @return the type with the specified nullability, or the original type if nullability cannot be updated.
   */
  public @NotNull PsiType withNullability(@NotNull TypeNullability nullability) {
    return this;
  }

  /**
   * Creates array type with this type as a component.
   */
  public @NotNull PsiArrayType createArrayType() {
    return new PsiArrayType(this);
  }

  /** @deprecated use {@link #annotate(TypeAnnotationProvider)} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public @NotNull PsiArrayType createArrayType(PsiAnnotation @NotNull ... annotations) {
    return new PsiArrayType(this, annotations);
  }

  /**
   * Returns text of the type that can be presented to a user (references normally non-qualified).
   */
  public @NotNull @NlsSafe String getPresentableText(boolean annotated) {
    return getPresentableText();
  }

  /**
   * Same as {@code getPresentableText(false)}.
   */
  public abstract @NotNull @NlsSafe String getPresentableText();

  /**
   * Returns canonical representation of the type (all references fully-qualified).
   */
  public @NotNull String getCanonicalText(boolean annotated) {
    return getCanonicalText();
  }

  /**
   * Same as {@code getCanonicalText(false)}.
   */
  public abstract @NotNull @NlsSafe String getCanonicalText();

  /**
   * Return canonical text of the type with some internal details added for presentational purposes. Use with care.
   * todo[r.sh] merge with getPresentableText()
   */
  public @NotNull String getInternalCanonicalText() {
    return getCanonicalText();
  }

  /**
   * Checks if the type is currently valid.
   *
   * @return true if the type is valid, false otherwise.
   * @see PsiElement#isValid()
   */
  public abstract boolean isValid();

  /**
   * @return true if values of type {@code type} can be assigned to rvalues of this type.
   */
  @Contract(pure = true)
  public boolean isAssignableFrom(@NotNull PsiType type) {
    return TypeConversionUtil.isAssignable(this, type);
  }

  /**
   * Checks whether values of type {@code type} can be casted to this type.
   */
  public boolean isConvertibleFrom(@NotNull PsiType type) {
    return TypeConversionUtil.areTypesConvertible(type, this);
  }

  /**
   * Checks if the specified string is equivalent to the canonical text of the type.
   *
   * @param text the text to compare with.
   * @return true if the string is equivalent to the type, false otherwise
   */
  public abstract boolean equalsToText(@NotNull @NonNls String text);

  /**
   * @return nullability of this type
   */
  public @NotNull TypeNullability getNullability() {
    return TypeNullability.UNKNOWN;
  }

  /**
   * Returns the class type for qualified class name.
   *
   * @param qName qualified class name.
   * @param resolveScope the scope in which the class is searched.
   * @return the class instance.
   */
  public static @NotNull PsiClassType getTypeByName(@NotNull String qName, @NotNull Project project, @NotNull GlobalSearchScope resolveScope) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    return factory.createTypeByFQClassName(qName, resolveScope);
  }

  /**
   * Returns the class type for the java.lang.Object class.
   *
   * @param manager      the PSI manager used to create the class type.
   * @param resolveScope the scope in which the class is searched.
   * @return the class instance.
   */
  public static @NotNull PsiClassType getJavaLangObject(@NotNull PsiManager manager, @NotNull GlobalSearchScope resolveScope) {
    return getTypeByName(CommonClassNames.JAVA_LANG_OBJECT, manager.getProject(), resolveScope);
  }

  /**
   * Returns the class type for the java.lang.Class class.
   *
   * @param manager      the PSI manager used to create the class type.
   * @param resolveScope the scope in which the class is searched.
   * @return the class instance.
   */
  public static @NotNull PsiClassType getJavaLangClass(@NotNull PsiManager manager, @NotNull GlobalSearchScope resolveScope) {
    return getTypeByName(CommonClassNames.JAVA_LANG_CLASS, manager.getProject(), resolveScope);
  }

  /**
   * Returns the class type for the java.lang.Throwable class.
   *
   * @param manager      the PSI manager used to create the class type.
   * @param resolveScope the scope in which the class is searched.
   * @return the class instance.
   */
  public static @NotNull PsiClassType getJavaLangThrowable(@NotNull PsiManager manager, @NotNull GlobalSearchScope resolveScope) {
    return getTypeByName(CommonClassNames.JAVA_LANG_THROWABLE, manager.getProject(), resolveScope);
  }

  /**
   * Returns the class type for the java.lang.String class.
   *
   * @param manager      the PSI manager used to create the class type.
   * @param resolveScope the scope in which the class is searched.
   * @return the class instance.
   */
  public static @NotNull PsiClassType getJavaLangString(@NotNull PsiManager manager, @NotNull GlobalSearchScope resolveScope) {
    return getTypeByName(CommonClassNames.JAVA_LANG_STRING, manager.getProject(), resolveScope);
  }

  /**
   * Returns the class type for the java.lang.Error class.
   *
   * @param manager      the PSI manager used to create the class type.
   * @param resolveScope the scope in which the class is searched.
   * @return the class instance.
   */
  public static @NotNull PsiClassType getJavaLangError(@NotNull PsiManager manager, @NotNull GlobalSearchScope resolveScope) {
    return getTypeByName(CommonClassNames.JAVA_LANG_ERROR, manager.getProject(), resolveScope);
  }

  /**
   * Returns the class type for the java.lang.RuntimeException class.
   *
   * @param manager      the PSI manager used to create the class type.
   * @param resolveScope the scope in which the class is searched.
   * @return the class instance.
   */
  public static @NotNull PsiClassType getJavaLangRuntimeException(@NotNull PsiManager manager, @NotNull GlobalSearchScope resolveScope) {
    return getTypeByName(CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION, manager.getProject(), resolveScope);
  }

  /**
   * Passes the type to the specified visitor.
   *
   * @param visitor the visitor to accept the type.
   * @return the value returned by the visitor.
   */
  public abstract <A> A accept(@NotNull PsiTypeVisitor<A> visitor);

  /**
   * Returns the number of array dimensions for the type.
   *
   * @return the number of dimensions, or 0 if the type is not an array type.
   */
  public final int getArrayDimensions() {
    PsiType type = this;
    int dims = 0;
    while (type instanceof PsiArrayType) {
      dims++;
      type = ((PsiArrayType)type).getComponentType();
    }
    return dims;
  }

  /**
   * Returns the innermost component type for an array type.
   *
   * @return the innermost (non-array) component of the type, or {@code this} if the type is not
   *         an array type.
   */
  public final @NotNull PsiType getDeepComponentType() {
    PsiType type = this;
    while (type instanceof PsiArrayType) {
      type = ((PsiArrayType)type).getComponentType();
    }
    return type;
  }

  /**
   * Returns the scope in which the reference to the underlying class of a class type is searched.
   *
   * @return the resolve scope instance, or null if the type is a primitive or an array of primitives.
   */
  public abstract @Nullable GlobalSearchScope getResolveScope();

  /**
   * Returns the list of superclass types for a class type.
   *
   * @return the array of superclass types, or an empty array if the type is not a class type.
   */
  public abstract PsiType @NotNull [] getSuperTypes();

  /**
   * @return provider for this type's annotations. Can be used to construct other PsiType instances
   * without actually evaluating the annotation array, which can be computationally expensive sometimes.
   */
  public final @NotNull TypeAnnotationProvider getAnnotationProvider() {
    return myAnnotationProvider;
  }

  /**
   * @return annotations for this type. Uses {@link #getAnnotationProvider()} to retrieve the annotations.
   */
  @Override
  public PsiAnnotation @NotNull [] getAnnotations() {
    return myAnnotationProvider.getAnnotations();
  }

  @Override
  public PsiAnnotation findAnnotation(@NotNull String qualifiedName) {
    for (PsiAnnotation annotation : getAnnotations()) {
      if (qualifiedName.equals(annotation.getQualifiedName())) {
        return annotation;
      }
    }
    return null;
  }

  @Override
  public @NotNull PsiAnnotation addAnnotation(@NotNull String qualifiedName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiAnnotation @NotNull [] getApplicableAnnotations() {
    return getAnnotations();
  }

  @Override
  public String toString() {
    return "PsiType:" + getPresentableText();
  }

  protected abstract static class Stub extends PsiType {
    protected Stub(PsiAnnotation @NotNull [] annotations) {
      super(annotations);
    }

    protected Stub(@NotNull TypeAnnotationProvider annotations) {
      super(annotations);
    }

    @Override
    public final @NotNull String getPresentableText() {
      return getPresentableText(false);
    }

    @Override
    public abstract @NotNull String getPresentableText(boolean annotated);

    @Override
    public final @NotNull String getCanonicalText() {
      return getCanonicalText(false);
    }

    @Override
    public abstract @NotNull String getCanonicalText(boolean annotated);
  }
}