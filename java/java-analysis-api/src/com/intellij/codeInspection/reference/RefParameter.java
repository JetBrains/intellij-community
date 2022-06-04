// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.reference;

import com.intellij.psi.PsiParameter;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UParameter;

/**
 * A node in the reference graph corresponding to a Java method parameter.
 *
 * @author anna
 */
public interface RefParameter extends RefJavaElement {
  Object VALUE_IS_NOT_CONST = ObjectUtils.sentinel("VALUE_IS_NOT_CONST");
  Object VALUE_UNDEFINED = ObjectUtils.sentinel("VALUE_UNDEFINED");

  /**
   * Checks if the parameter is used for reading.
   *
   * @return true if the parameter has read accesses, false otherwise.
   */
  boolean isUsedForReading();

  /**
   * Checks if the parameter is used for writing.
   *
   * @return true if the parameter has write accesses, false otherwise.
   */
  boolean isUsedForWriting();

  /**
   * Returns the index of the parameter in the parameter list of its owner method.
   *
   * @return the index of the parameter.
   */
  int getIndex();

  /**
   * @see RefParameter#getActualConstValue()
   */
  @Deprecated(forRemoval = true)
  @Nullable
  default String getActualValueIfSame() {
    throw new UnsupportedOperationException();
  }


  /**
   * If all invocations of the method pass the same value to the parameter, returns
   * that value (the name of a static final field or the text of a literal expression).
   * Otherwise, returns {@link RefParameter#VALUE_IS_NOT_CONST}.
   *
   * @return the parameter value or null if it's different or impossible to determine.
   */
  @Nullable
  default Object getActualConstValue() {
    //noinspection deprecation
    return getActualValueIfSame();
  }

  /**
   * Marks the parameter as referenced for reading or writing.
   *
   * @param forWriting true if the parameter is marked as referenced for writing, false
   * otherwise.
   */
  void parameterReferenced(final boolean forWriting);

  @Override
  default UParameter getUastElement() {
    throw new UnsupportedOperationException();
  }

  @Deprecated
  @Override
  default PsiParameter getElement() {
    return ObjectUtils.tryCast(getPsiElement(), PsiParameter.class);
  }

  default int getUsageCount() {
    throw new UnsupportedOperationException();
  }
}
