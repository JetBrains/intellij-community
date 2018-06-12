// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.lang.jvm.types.JvmPrimitiveTypeKind;
import com.intellij.lang.jvm.types.JvmType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Representation of Java type (primitive type, array or class type).
 */
public abstract class PsiType implements PsiAnnotationOwner, Cloneable, JvmType {
  @SuppressWarnings("StaticInitializerReferencesSubClass") public static final PsiPrimitiveType BYTE = new PsiPrimitiveType(JvmPrimitiveTypeKind.BYTE);
  @SuppressWarnings("StaticInitializerReferencesSubClass") public static final PsiPrimitiveType CHAR = new PsiPrimitiveType(JvmPrimitiveTypeKind.CHAR);
  @SuppressWarnings("StaticInitializerReferencesSubClass") public static final PsiPrimitiveType DOUBLE = new PsiPrimitiveType(JvmPrimitiveTypeKind.DOUBLE);
  @SuppressWarnings("StaticInitializerReferencesSubClass") public static final PsiPrimitiveType FLOAT = new PsiPrimitiveType(JvmPrimitiveTypeKind.FLOAT);
  @SuppressWarnings("StaticInitializerReferencesSubClass") public static final PsiPrimitiveType INT = new PsiPrimitiveType(JvmPrimitiveTypeKind.INT);
  @SuppressWarnings("StaticInitializerReferencesSubClass") public static final PsiPrimitiveType LONG = new PsiPrimitiveType(JvmPrimitiveTypeKind.LONG);
  @SuppressWarnings("StaticInitializerReferencesSubClass") public static final PsiPrimitiveType SHORT = new PsiPrimitiveType(JvmPrimitiveTypeKind.SHORT);
  @SuppressWarnings("StaticInitializerReferencesSubClass") public static final PsiPrimitiveType BOOLEAN = new PsiPrimitiveType(JvmPrimitiveTypeKind.BOOLEAN);
  @SuppressWarnings("StaticInitializerReferencesSubClass") public static final PsiPrimitiveType VOID = new PsiPrimitiveType(JvmPrimitiveTypeKind.VOID);
  @SuppressWarnings("StaticInitializerReferencesSubClass") public static final PsiPrimitiveType NULL = new PsiPrimitiveType(null);

  public static final PsiType[] EMPTY_ARRAY = new PsiType[0];
  public static final ArrayFactory<PsiType> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new PsiType[count];

  @NotNull
  public static PsiType[] createArray(int count) {
    return ARRAY_FACTORY.create(count);
  }

  private TypeAnnotationProvider myAnnotationProvider;

  /**
   * Constructs a PsiType with given annotations
   */
  protected PsiType(@NotNull final PsiAnnotation[] annotations) {
    this(TypeAnnotationProvider.Static.create(annotations));
  }

  /**
   * Constructs a PsiType that will take its annotations from the given annotation provider.
   */
  protected PsiType(@NotNull TypeAnnotationProvider annotations) {
    myAnnotationProvider = annotations;
  }

  @NotNull
  public PsiType annotate(@NotNull TypeAnnotationProvider provider) {
    if (provider == myAnnotationProvider) return this;

    try {
      PsiType copy = (PsiType)clone();
      copy.myAnnotationProvider = provider;
      return copy;
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Creates array type with this type as a component.
   */
  @NotNull
  public PsiArrayType createArrayType() {
    return new PsiArrayType(this);
  }

  /** @deprecated use {@link #annotate(TypeAnnotationProvider)} (to be removed in IDEA 18) */
  @NotNull
  public PsiArrayType createArrayType(@NotNull PsiAnnotation... annotations) {
    return new PsiArrayType(this, annotations);
  }

  /**
   * Returns text of the type that can be presented to a user (references normally non-qualified).
   */
  @NotNull
  public String getPresentableText(boolean annotated) {
    return getPresentableText();
  }

  /**
   * Same as {@code getPresentableText(false)}.
   */
  @NotNull
  public abstract String getPresentableText();

  /**
   * Returns canonical representation of the type (all references fully-qualified).
   */
  @NotNull
  public String getCanonicalText(boolean annotated) {
    return getCanonicalText();
  }

  /**
   * Same as {@code getCanonicalText(false)}.
   */
  @NotNull
  public abstract String getCanonicalText();

  /**
   * Return canonical text of the type with some internal details added for presentational purposes. Use with care.
   * todo[r.sh] merge with getPresentableText()
   */
  @NotNull
  public String getInternalCanonicalText() {
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
  public abstract boolean equalsToText(@NotNull String text);

  /**
   * Returns the class type for qualified class name.
   *
   * @param qName qualified class name.
   * @param resolveScope the scope in which the class is searched.
   * @return the class instance.
   */
  @NotNull
  public static PsiClassType getTypeByName(@NotNull String qName, @NotNull Project project, @NotNull GlobalSearchScope resolveScope) {
    PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    return factory.createTypeByFQClassName(qName, resolveScope);
  }

  /**
   * Returns the class type for the java.lang.Object class.
   *
   * @param manager      the PSI manager used to create the class type.
   * @param resolveScope the scope in which the class is searched.
   * @return the class instance.
   */
  @NotNull
  public static PsiClassType getJavaLangObject(@NotNull PsiManager manager, @NotNull GlobalSearchScope resolveScope) {
    return getTypeByName(CommonClassNames.JAVA_LANG_OBJECT, manager.getProject(), resolveScope);
  }

  /**
   * Returns the class type for the java.lang.Class class.
   *
   * @param manager      the PSI manager used to create the class type.
   * @param resolveScope the scope in which the class is searched.
   * @return the class instance.
   */
  @NotNull
  public static PsiClassType getJavaLangClass(@NotNull PsiManager manager, @NotNull GlobalSearchScope resolveScope) {
    return getTypeByName(CommonClassNames.JAVA_LANG_CLASS, manager.getProject(), resolveScope);
  }

  /**
   * Returns the class type for the java.lang.Throwable class.
   *
   * @param manager      the PSI manager used to create the class type.
   * @param resolveScope the scope in which the class is searched.
   * @return the class instance.
   */
  @NotNull
  public static PsiClassType getJavaLangThrowable(@NotNull PsiManager manager, @NotNull GlobalSearchScope resolveScope) {
    return getTypeByName(CommonClassNames.JAVA_LANG_THROWABLE, manager.getProject(), resolveScope);
  }

  /**
   * Returns the class type for the java.lang.String class.
   *
   * @param manager      the PSI manager used to create the class type.
   * @param resolveScope the scope in which the class is searched.
   * @return the class instance.
   */
  @NotNull
  public static PsiClassType getJavaLangString(@NotNull PsiManager manager, @NotNull GlobalSearchScope resolveScope) {
    return getTypeByName(CommonClassNames.JAVA_LANG_STRING, manager.getProject(), resolveScope);
  }

  /**
   * Returns the class type for the java.lang.Error class.
   *
   * @param manager      the PSI manager used to create the class type.
   * @param resolveScope the scope in which the class is searched.
   * @return the class instance.
   */
  @NotNull
  public static PsiClassType getJavaLangError(@NotNull PsiManager manager, @NotNull GlobalSearchScope resolveScope) {
    return getTypeByName(CommonClassNames.JAVA_LANG_ERROR, manager.getProject(), resolveScope);
  }

  /**
   * Returns the class type for the java.lang.RuntimeException class.
   *
   * @param manager      the PSI manager used to create the class type.
   * @param resolveScope the scope in which the class is searched.
   * @return the class instance.
   */
  @NotNull
  public static PsiClassType getJavaLangRuntimeException(@NotNull PsiManager manager, @NotNull GlobalSearchScope resolveScope) {
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
  @NotNull
  public final PsiType getDeepComponentType() {
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
  @Nullable
  public abstract GlobalSearchScope getResolveScope();

  /**
   * Returns the list of superclass types for a class type.
   *
   * @return the array of superclass types, or an empty array if the type is not a class type.
   */
  @NotNull
  public abstract PsiType[] getSuperTypes();

  /**
   * @return provider for this type's annotations. Can be used to construct other PsiType instances
   * without actually evaluating the annotation array, which can be computationally expensive sometimes.
   */
  @NotNull
  public final TypeAnnotationProvider getAnnotationProvider() {
    return myAnnotationProvider;
  }

  /**
   * @return annotations for this type. Uses {@link #getAnnotationProvider()} to retrieve the annotations.
   */
  @Override
  @NotNull
  public PsiAnnotation[] getAnnotations() {
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
  @NotNull
  public PsiAnnotation addAnnotation(@NotNull String qualifiedName) {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public PsiAnnotation[] getApplicableAnnotations() {
    return getAnnotations();
  }

  @Override
  public String toString() {
    return "PsiType:" + getPresentableText();
  }

  protected abstract static class Stub extends PsiType {
    protected Stub(@NotNull PsiAnnotation[] annotations) {
      super(annotations);
    }

    protected Stub(@NotNull TypeAnnotationProvider annotations) {
      super(annotations);
    }

    @NotNull
    @Override
    public final String getPresentableText() {
      return getPresentableText(false);
    }

    @NotNull
    @Override
    public abstract String getPresentableText(boolean annotated);

    @NotNull
    @Override
    public final String getCanonicalText() {
      return getCanonicalText(false);
    }

    @NotNull
    @Override
    public abstract String getCanonicalText(boolean annotated);
  }
}