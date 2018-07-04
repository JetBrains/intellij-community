// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.lang.jvm.JvmAnnotation;
import com.intellij.lang.jvm.annotation.JvmAnnotationAttribute;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * Represents a Java annotation.
 *
 * @author ven
 */
public interface PsiAnnotation extends PsiAnnotationMemberValue, PsiMetaOwner, JvmAnnotation {
  /**
   * The empty array of PSI annotations which can be reused to avoid unnecessary allocations.
   */
  PsiAnnotation[] EMPTY_ARRAY = new PsiAnnotation[0];

  ArrayFactory<PsiAnnotation> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new PsiAnnotation[count];

  @NonNls String DEFAULT_REFERENCED_METHOD_NAME = "value";

  /**
   * Kinds of element to which an annotation type is applicable (see {@link java.lang.annotation.ElementType}).
   */
  enum TargetType {
    // see java.lang.annotation.ElementType
    TYPE, FIELD, METHOD, PARAMETER, CONSTRUCTOR, LOCAL_VARIABLE, ANNOTATION_TYPE, PACKAGE, TYPE_USE, TYPE_PARAMETER, MODULE,
    // auxiliary value, used when it's impossible to determine annotation's targets
    UNKNOWN;

    public static final TargetType[] EMPTY_ARRAY = {};
  }

  /**
   * Returns the list of parameters for the annotation.
   *
   * @return the parameter list instance.
   */
  @NotNull
  PsiAnnotationParameterList getParameterList();

  /**
   * Returns the fully qualified name of the annotation class.
   *
   * @return the class name, or null if the annotation is unresolved.
   */
  @Nullable
  @NonNls
  String getQualifiedName();

  /**
   * Returns the reference element representing the name of the annotation.
   *
   * @return the annotation name element.
   */
  @Nullable
  PsiJavaCodeReferenceElement getNameReferenceElement();

  /**
   * Returns the value of the annotation element with the specified name.
   *
   * @param attributeName name of the annotation element for which the value is requested. If it isn't defined in annotation,
   *                      the default value is returned.
   * @return the element value, or null if the annotation does not contain a value for
   *         the element and the element has no default value.
   */
  @Nullable
  PsiAnnotationMemberValue findAttributeValue(@Nullable @NonNls String attributeName);

  /**
   * Returns the value of the annotation element with the specified name.
   *
   * @param attributeName name of the annotation element for which the value is requested, declared in this annotation.
   * @return the element value, or null if the annotation does not contain a value for
   *         the element.
   */
  @Nullable
  PsiAnnotationMemberValue findDeclaredAttributeValue(@Nullable @NonNls String attributeName);

  /**
   * Set annotation attribute value. Adds new name-value pair or uses an existing one, expands unnamed 'value' attribute name if needed.
   *
   * @param attributeName attribute name
   * @param value         new value template element
   * @return new declared attribute value
   */
  <T extends PsiAnnotationMemberValue> T setDeclaredAttributeValue(@Nullable @NonNls String attributeName, @Nullable T value);

  /**
   * Returns an owner of the annotation - usually a parent, but for type annotations the owner might be a type element.
   *
   * @return annotation owner
   */
  @Nullable
  PsiAnnotationOwner getOwner();

  @Nullable
  @Override
  default PsiElement getSourceElement() {
    return this;
  }

  @Override
  default void navigate(boolean requestFocus) {}

  @Override
  default boolean canNavigate() {
    return false;
  }

  @Override
  default boolean canNavigateToSource() {
    return false;
  }

  @NotNull
  @Override
  default List<JvmAnnotationAttribute> getAttributes() {
    return Arrays.asList(getParameterList().getAttributes());
  }
}
