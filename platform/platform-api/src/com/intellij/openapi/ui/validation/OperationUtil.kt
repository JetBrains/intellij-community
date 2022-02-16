// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.validation

import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import javax.swing.text.JTextComponent

/**
 * Creates validation from two validations.
 * It allows creating one complex validation from several simple validations.
 * New validation returns null (data is valid) when both validations return null.
 */
infix fun DialogValidation.and(validation: DialogValidation) =
  DialogValidation { validate() ?: validation.validate() }

/**
 * Creates validation from two validations with shared parameter.
 * It allows creating one complex validation from several simple validations.
 * New validation returns null (data is valid) when both validations return null.
 */
infix fun <T> DialogValidation.WithParameter<T>.and(validation: DialogValidation.WithParameter<T>) =
  DialogValidation.WithParameter<T> { curry(it) and validation(it) }

/**
 * Creates validation from two validations with parameter.
 * It allows creating one complex validation from several simple validations.
 * New validation returns null (data is valid) when both validations return null.
 */
infix fun <T> DialogValidation.WithParameter<T>.and(validation: DialogValidation) =
  DialogValidation.WithParameter<T> { curry(it) and validation }

/**
 * Creates validation from two validations with parameter.
 * It allows creating one complex validation from several simple validations.
 * New validation returns null (data is valid) when both validations return null.
 */
infix fun <T> DialogValidation.and(validation: DialogValidation.WithParameter<T>) =
  DialogValidation.WithParameter<T> { this and validation(it) }

/**
 * Creates validation from two validations with two shared parameters.
 * It allows creating one complex validation from several simple validations.
 * New validation returns null (data is valid) when both validations return null.
 */
infix fun <T1, T2> DialogValidation.WithTwoParameters<T1, T2>.and(validation: DialogValidation.WithTwoParameters<T1, T2>) =
  DialogValidation.WithTwoParameters<T1, T2> { curry(it) and validation(it) }

/**
 * Creates validation from two validations with two parameters.
 * It allows creating one complex validation from several simple validations.
 * New validation returns null (data is valid) when both validations return null.
 */
infix fun <T1, T2> DialogValidation.WithTwoParameters<T1, T2>.and(validation: DialogValidation) =
  DialogValidation.WithTwoParameters<T1, T2> { curry(it) and validation }

/**
 * Creates validation from two validations with two parameters where second one is shared.
 * It allows creating one complex validation from several simple validations.
 * New validation returns null (data is valid) when both validations return null.
 */
infix fun <T1, T2> DialogValidation.WithTwoParameters<T1, T2>.and(validation: DialogValidation.WithParameter<T2>) =
  DialogValidation.WithTwoParameters<T1, T2> { curry(it) and validation }

/**
 * Creates validation from two validations with two parameters.
 * It allows creating one complex validation from several simple validations.
 * New validation returns null (data is valid) when both validations return null.
 */
infix fun <T1, T2> DialogValidation.and(validation: DialogValidation.WithTwoParameters<T1, T2>) =
  DialogValidation.WithTwoParameters<T1, T2> { this and validation(it) }

/**
 * Creates validation from two validations with two parameters where second one is shared.
 * It allows creating one complex validation from several simple validations.
 * New validation returns null (data is valid) when both validations return null.
 */
infix fun <T1, T2> DialogValidation.WithParameter<T2>.and(validation: DialogValidation.WithTwoParameters<T1, T2>) =
  DialogValidation.WithTwoParameters<T1, T2> { this and validation(it) }

/**
 * Transforms validation result. It allows changing validation info properties.
 * For example, you can make warning from error validation.
 */
fun DialogValidation.transformResult(transform: ValidationInfo.() -> ValidationInfo?) =
  DialogValidation { validate()?.transform() }

/**
 * Transforms validation result. It allows changing validation info properties.
 * For example, you can make warning from error validation.
 */
fun <T> DialogValidation.WithParameter<T>.transformResult(transform: ValidationInfo.() -> ValidationInfo?) =
  DialogValidation.WithParameter<T> { curry(it).transformResult(transform) }

/**
 * Transforms validation result. It allows changing validation info properties.
 * For example, you can make warning from error validation.
 */
fun <T1, T2> DialogValidation.WithTwoParameters<T1, T2>.transformResult(transform: ValidationInfo.() -> ValidationInfo?) =
  DialogValidation.WithTwoParameters<T1, T2> { curry(it).transformResult(transform) }

fun DialogValidation.asWarning() = transformResult { asWarning() }

fun DialogValidation.withOKEnabled() = transformResult { withOKEnabled() }

fun <T> DialogValidation.WithParameter<T>.asWarning() = transformResult { asWarning() }

fun <T> DialogValidation.WithParameter<T>.withOKEnabled() = transformResult { withOKEnabled() }

fun <T1, T2> DialogValidation.WithTwoParameters<T1, T2>.asWarning() = transformResult { asWarning() }

fun <T1, T2> DialogValidation.WithTwoParameters<T1, T2>.withOKEnabled() = transformResult { withOKEnabled() }

/**
 * Transforms validation builder parameter.
 * For example, it allows using existed text field validation for text field with browse button.
 */
fun <T, R> DialogValidation.WithParameter<R>.transformParameter(transform: T.() -> R) =
  DialogValidation.WithParameter<T> { parameter ->
    curry(transform(parameter))
  }

/**
 * Transforms string validation into validation for [JTextComponent].
 */
fun DialogValidation.WithParameter<() -> String>.forTextComponent() =
  transformParameter<JTextComponent, () -> String> { ::getText }

/**
 * Transforms string validation into validation for [TextFieldWithBrowseButton].
 */
fun DialogValidation.WithParameter<() -> String>.forTextFieldWithBrowseButton() =
  transformParameter<TextFieldWithBrowseButton, () -> String> { ::getText }

/**
 * Transforms string validation into validation for [ObservableProperty].
 * Note: This validation can be used with UI components but value for validation
 * is got from [ObservableProperty] which passed as validation parameter. So,
 * property in parameter should be bound with component value.
 * @see DialogValidation.WithParameter
 */
fun <T> DialogValidation.WithParameter<() -> T>.forProperty() =
  transformParameter<ObservableProperty<T>, () -> T> { ::get }

/**
 * Transforms  string validation into validation for [ObservableProperty] and additional parameter.
 * @see forProperty with one parameter
 */
fun <T1, T2> DialogValidation.WithTwoParameters<T1, () -> T2>.forProperty() =
  DialogValidation.WithTwoParameters<T1, ObservableProperty<T2>> { parameter ->
    curry(parameter).forProperty()
  }

operator fun <T> DialogValidation.WithParameter<() -> T>.invoke(property: ObservableProperty<T>) =
  forProperty()(property)

operator fun <T1, T2> DialogValidation.WithTwoParameters<T1, () -> T2>.invoke(parameter: T1, property: ObservableProperty<T2>) =
  forProperty()(parameter, property)

operator fun <T> DialogValidation.WithParameter<T>.invoke(parameter: T) =
  curry(parameter)

operator fun <T1, T2> DialogValidation.WithTwoParameters<T1, T2>.invoke(parameter: T1) =
  curry(parameter)

operator fun <T1, T2> DialogValidation.WithTwoParameters<T1, T2>.invoke(parameter1: T1, parameter2: T2) =
  curry(parameter1).curry(parameter2)
