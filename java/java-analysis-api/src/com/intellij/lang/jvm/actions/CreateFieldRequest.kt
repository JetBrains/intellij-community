// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions

import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.types.JvmSubstitutor

interface CreateFieldRequest : ActionRequest {

  /**
   * @return name of the field to be created
   */
  val fieldName: String

  /**
   * @return expected types of the field to be created
   */
  val fieldType: ExpectedTypes

  /**
   * Given:
   * - target class: `A<T>`
   * - expected field type: `String`
   * - usage: `new A<String>.foo`
   *
   * To make newly created field `foo` have type `T` the substitutor is needed to provide mapping T -> String.
   *
   * @return call-site substitutor for the target
   */
  val targetSubstitutor: JvmSubstitutor

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
