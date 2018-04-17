// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm;

import com.intellij.lang.jvm.types.JvmReferenceType;
import com.intellij.lang.jvm.types.JvmType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a method or a constructor.
 *
 * @see java.lang.reflect.Method
 * @see java.lang.reflect.Constructor
 * @see java.lang.reflect.Executable
 */
public interface JvmMethod extends JvmTypeParametersOwner {

  JvmMethod[] EMPTY_ARRAY = new JvmMethod[0];

  /**
   * @return {@code true} if this method is a constructor
   */
  boolean isConstructor();

  /**
   * @see java.lang.reflect.Executable#getName
   */
  @NotNull
  @Override
  String getName();

  /**
   * @return return type of a method or {@code null} if this {@code JvmMethod} represents a constructor
   * @see java.lang.reflect.Method#getGenericReturnType
   * @see java.lang.reflect.Method#getAnnotatedReturnType
   */
  @Nullable
  JvmType getReturnType();

  /**
   * @since 2018.2
   */
  default boolean hasParameters() {
    return getParameters().length > 0;
  }

  /**
   * @see java.lang.reflect.Executable#getParameters
   */
  @NotNull
  JvmParameter[] getParameters();

  /**
   * @see java.lang.reflect.Executable#isVarArgs
   */
  boolean isVarArgs();

  /**
   * @see java.lang.reflect.Method#getGenericExceptionTypes
   * @see java.lang.reflect.Method#getAnnotatedExceptionTypes
   */
  @NotNull
  JvmReferenceType[] getThrowsTypes();

  @Override
  default <T> T accept(@NotNull JvmElementVisitor<T> visitor) {
    return visitor.visitMethod(this);
  }
}
