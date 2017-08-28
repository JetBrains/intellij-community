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
package com.intellij.lang.jvm.actions

import com.intellij.lang.jvm.JvmModifier

interface CreateFieldRequest {

  /**
   * Request may be bound to the PSI-element in call-site language,
   * which means request will become invalid if element is invalidated.
   *
   * @return `true` if it is safe to call other methods of this object, `false` otherwise
   */
  val isValid: Boolean

  /**
   * @return name of the field to be created
   */
  val fieldName: String

  /**
   * The common interface is not ready yet, so this is [Any]?.
   * For Java language an array of [com.intellij.codeInsight.ExpectedTypeInfo] is returned now.
   *
   * @return expected type info of the field to be created
   */
  val fieldType: Any?

  /**
   * Implementation are free to render any modifiers as long as they don't contradict with requested ones.
   * Example: if constant field is requested then it will be rendered
   *          with `static final` modifiers even if they are not present in this collection.
   *
   * @return modifiers that should be present when requested field is compiled
   */
  val modifiers: Collection<JvmModifier>

  /**
   * Constant fields may be used in annotations and in other constant expressions.
   *
   * @return whether the field must be a compile-time constant
   */
  val constant: Boolean
}
