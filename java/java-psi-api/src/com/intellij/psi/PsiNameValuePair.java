// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.lang.jvm.annotation.JvmAnnotationAttribute;
import com.intellij.lang.jvm.annotation.JvmAnnotationAttributeValue;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.PsiJvmConversionHelper.getAnnotationAttributeName;
import static com.intellij.psi.PsiJvmConversionHelper.getAnnotationAttributeValue;

/**
 * Represents a single element-value pair of an annotation parameter list.
 *
 * @author ven
 * @see PsiAnnotation
 * @see PsiAnnotationParameterList
 */
public interface PsiNameValuePair extends PsiElement, JvmAnnotationAttribute {
  /**
   * The empty array of PSI name/value pairs which can be reused to avoid unnecessary allocations.
   */
  PsiNameValuePair[] EMPTY_ARRAY = new PsiNameValuePair[0];

  ArrayFactory<PsiNameValuePair> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new PsiNameValuePair[count];

  /**
   * Returns the identifier specifying the name of the element.
   *
   * @return the name identifier, or null if the annotation declaration is incomplete.
   */
  @Nullable
  PsiIdentifier getNameIdentifier();

  /**
   * Returns the name of the element.
   *
   * @return the name, or null if the annotation declaration is incomplete.
   */
  @Nullable
  @NonNls
  String getName();

  @Nullable
  String getLiteralValue();

  /**
   * Returns the value for the element.
   *
   * @return the value for the element.
   */
  @Nullable
  PsiAnnotationMemberValue getValue();

  /**
   * @return a element representing the annotation attribute's value. The main difference to {@link #getValue()} is that this method
   * avoids expensive AST loading (see {@link com.intellij.extapi.psi.StubBasedPsiElementBase} doc).
   * The downside is that the result might not be in the same tree as the parent, might be non-physical and so
   * should only be used for read operations.
   */
  @Nullable
  default PsiAnnotationMemberValue getDetachedValue() {
    return getValue();
  }

  @NotNull
  PsiAnnotationMemberValue setValue(@NotNull PsiAnnotationMemberValue newValue);

  @NotNull
  @Override
  default String getAttributeName() {
    return getAnnotationAttributeName(this);
  }

  @Nullable
  @Override
  default JvmAnnotationAttributeValue getAttributeValue() {
    return getAnnotationAttributeValue(this);
  }
}
