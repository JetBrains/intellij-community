// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.reference;

import com.intellij.psi.PsiField;
import org.jetbrains.uast.UField;

/**
 * A node in the reference graph corresponding to a Java field.
 *
 * @author anna
 */
public interface RefField extends RefJavaElement {

  /**
   * Checks if the field is used for reading.
   *
   * @return true if the field has read accesses, false otherwise.
   */
  boolean isUsedForReading();

  /**
   * Checks if the field is used for writing.
   *
   * @return true if the field has write accesses, false otherwise.
   */
  boolean isUsedForWriting();

  /**
   * Checks if the only write access of the field is its initializer.
   *
   * @return true if the only write access of the field is its initializer, false otherwise.
   */
  boolean isOnlyAssignedInInitializer();

  /**
   * Checks if this field is an enum constant.
   *
   * @return true if the field is an enum constant, false otherwise.
   */
  default boolean isEnumConstant() {
    return false;
  }

  /**
   * Checks if this field is implicitly read.
   * @see com.intellij.codeInsight.daemon.ImplicitUsageProvider
   *
   * @return true if the field is implicitly read, false otherwise.
   */
  default boolean isImplicitlyRead() {
    return false;
  }

  /**
   * Checks if this field is implicitly written.
   * @see com.intellij.codeInsight.daemon.ImplicitUsageProvider
   *
   * @return true if the field is implicitly written, false otherwise.
   */
  default boolean isImplicitlyWritten() {
    return false;
  }

  /**
   * Returns the reference graph node for the class to which the field belongs.
   *
   * @return the owner class of the field.
   */
  RefClass getOwnerClass();

  @Deprecated
  @Override
  PsiField getElement();

  @Override
  default UField getUastElement() {
    throw new UnsupportedOperationException();
  }
}
