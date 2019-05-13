// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.types;

import org.jetbrains.annotations.NotNull;

/**
 * Represents wildcard type, for example {@code ? extends Number} in {@code List<? extends Number>}.
 *
 * @see java.lang.reflect.WildcardType
 */
public interface JvmWildcardType extends JvmType {

  /**
   * An upper bound that this wildcard imposes on type parameter value.<br>
   * That is:
   * <ul>
   * <li> for {@code ? extends XXX}: {@code XXX}
   * <li> for {@code ? super XXX}: {@code java.lang.Object}
   * <li> for {@code ?}: {@code java.lang.Object}
   * </ul>
   * <p>
   *
   * @return an upper bound
   * @see java.lang.reflect.WildcardType#getUpperBounds
   */
  @NotNull
  JvmType upperBound();

  /**
   * A lower bound that this wildcard imposes on type parameter value.<br>
   * That is:
   * <ul>
   * <li> for {@code ? extends XXX}: empty iterable
   * <li> for {@code ? super XXX}: {@code XXX}
   * <li> for {@code ?}: null type
   * </ul>
   *
   * @return a lower bound
   * @see java.lang.reflect.WildcardType#getLowerBounds()
   */
  @NotNull
  JvmType lowerBound();

  @Override
  default <T> T accept(@NotNull JvmTypeVisitor<T> visitor) {
    return visitor.visitWildcardType(this);
  }
}
