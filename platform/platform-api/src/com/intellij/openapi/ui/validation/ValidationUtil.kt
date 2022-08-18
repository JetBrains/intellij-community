// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.validation

import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.ui.ValidationInfo
import java.io.File
import java.nio.file.Path

/**
 * Created validation with parameter that produces error if [getMessage] returns non-null value.
 */
fun <T> validationErrorFor(getMessage: (T) -> @NlsContexts.DialogMessage String?) =
  DialogValidation.WithParameter<() -> T> {
    DialogValidation {
      val message = getMessage(it())
      if (message != null) {
        ValidationInfo(message)
      }
      else {
        null
      }
    }
  }

/**
 * Created validation with two parameters that produces error if [getMessage] returns non-null value.
 */
fun <T1, T2> validationErrorFor(getMessage: (T1, T2) -> @NlsContexts.DialogMessage String?) =
  validationErrorWithTwoParametersFor(getMessage) { validationErrorFor(it) }

/**
 * Created validation with [Path] parameter that produces error if [getMessage] returns non-null value.
 */
fun validationPathErrorFor(getMessage: (Path) -> @NlsContexts.DialogMessage String?) =
  validationErrorFor<String> { getMessage(Path.of(it)) }

/**
 * Created validation with custom and [Path] parameters that produces error if [getMessage] returns non-null value.
 */
fun <T> validationPathErrorFor(getMessage: (T, Path) -> @NlsContexts.DialogMessage String?) =
  validationErrorWithTwoParametersFor(getMessage) { validationPathErrorFor(it) }

/**
 * Created validation with [File] parameter that produces error if [getMessage] returns non-null value.
 */
fun validationFileErrorFor(getMessage: (File) -> @NlsContexts.DialogMessage String?) =
  validationPathErrorFor { getMessage(it.toFile()) }

/**
 * Created validation with custom and [File] parameters that produces error if [getMessage] returns non-null value.
 */
fun <T> validationFileErrorFor(getMessage: (T, File) -> @NlsContexts.DialogMessage String?) =
  validationErrorWithTwoParametersFor(getMessage) { validationFileErrorFor(it) }

/**
 * Created validation with parameter that produces error if [isNotValid] is true.
 */
fun <T> validationErrorIf(message: @NlsContexts.DialogMessage String, isNotValid: (T) -> Boolean) =
  validationErrorFor(createMessageGetter(message, isNotValid))

/**
 * Created validation with two parameters that produces error if [isNotValid] is true.
 */
fun <T1, T2> validationErrorIf(message: @NlsContexts.DialogMessage String, isNotValid: (T1, T2) -> Boolean) =
  validationErrorFor(createMessageGetter(message, isNotValid))

/**
 * Create validation with two parameters from [validationBuilder] with one parameter.
 * Note: appended parameter is first.
 */
private fun <T1, T2, R> validationErrorWithTwoParametersFor(
  getMessage: (T1, T2) -> @NlsContexts.DialogMessage String?,
  validationBuilder: ((T2) -> @NlsContexts.DialogMessage String?) -> DialogValidation.WithParameter<R>
) = DialogValidation.WithTwoParameters<T1, R> { parameter ->
  validationBuilder { getMessage(parameter, it) }
}

/**
 * Wraps [message] with function which return this [message] if [isNotValid] returns true.
 * [message] is a message that should be shown when component data is invalid.
 * @see validationErrorFor
 */
private fun <T> createMessageGetter(
  message: @NlsContexts.DialogMessage String,
  isNotValid: (T) -> Boolean
) = { it: T -> if (isNotValid(it)) message else null }

/**
 * Wraps [message] with function which return this [message] if [isNotValid] returns true.
 * [message] is a message that should be shown when component data is invalid.
 * @see validationErrorFor
 */
private fun <T1, T2> createMessageGetter(
  message: @NlsContexts.DialogMessage String,
  isNotValid: (T1, T2) -> Boolean
) = { p1: T1, p2: T2 -> if (isNotValid(p1, p2)) message else null }
