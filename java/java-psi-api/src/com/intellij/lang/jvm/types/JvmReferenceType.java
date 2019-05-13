// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.types;

import com.intellij.lang.jvm.JvmTypeDeclaration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a type which could be resolved into a class or a type parameter and optionally has type arguments.
 * <br/>
 * Such type appears in throws, bounds, extends, implements.
 * <p>
 * While {@link Class} and {@link java.lang.reflect.TypeVariable TypeVariable}
 * <b>are</b> {@link java.lang.reflect.Type types} because they are resolved at runtime,
 * this interface defines the contract for delaying actual resolution of the type declaration.
 *
 * @see java.lang.reflect.ParameterizedType
 */
public interface JvmReferenceType extends JvmType {

  JvmReferenceType[] EMPTY_ARRAY = new JvmReferenceType[0];

  @NotNull
  String getName();

  /**
   * @return declaration that declares this type or {@code null} if it cannot be resolved
   */
  @Nullable
  default JvmTypeDeclaration resolve() {
    JvmTypeResolveResult result = resolveType();
    return result == null ? null : result.getDeclaration();
  }

  /**
   * @return resolve result or {@code null} if it cannot be resolved
   */
  @Nullable
  JvmTypeResolveResult resolveType();

  /**
   * @return type arguments or empty iterable if this type is not a parameterized type
   * @see java.lang.reflect.ParameterizedType#getActualTypeArguments
   */
  @NotNull
  Iterable<JvmType> typeArguments();

  @Override
  default <T> T accept(@NotNull JvmTypeVisitor<T> visitor) {
    return visitor.visitReferenceType(this);
  }
}
