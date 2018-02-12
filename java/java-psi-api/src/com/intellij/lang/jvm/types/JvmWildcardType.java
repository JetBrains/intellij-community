/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
}
