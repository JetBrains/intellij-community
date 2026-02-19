// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.validation


/**
 * Creates validation requestor from two validation requestors.
 * It allows creating one complex validation requestor from several simple validation requestors.
 * New validation subscribes on both validation requestors.
 */
infix fun DialogValidationRequestor.and(requestor: DialogValidationRequestor): DialogValidationRequestor =
  DialogValidationRequestor { parentDisposable, validate ->
    subscribe(parentDisposable, validate)
    requestor.subscribe(parentDisposable, validate)
  }

/**
 * Creates validation requestor from two validation requestors with shared parameter.
 * It allows creating one complex validation requestor from several simple validation requestors.
 * New validation subscribes on both validation requestors.
 */
infix fun <T> DialogValidationRequestor.WithParameter<T>.and(requestor: DialogValidationRequestor.WithParameter<T>): DialogValidationRequestor.WithParameter<T> =
  DialogValidationRequestor.WithParameter { invoke(it) and requestor(it) }

/**
 * Creates validation requestor from two validation requestors with parameter.
 * It allows creating one complex validation requestor from several simple validation requestors.
 * New validation subscribes on both validation requestors.
 */
infix fun <T> DialogValidationRequestor.WithParameter<T>.and(requestor: DialogValidationRequestor): DialogValidationRequestor.WithParameter<T> =
  DialogValidationRequestor.WithParameter { invoke(it) and requestor }

/**
 * Creates validation requestor from two validation requestors with parameter.
 * It allows creating one complex validation requestor from several simple validation requestors.
 * New validation subscribes on both validation requestors.
 */
infix fun <T> DialogValidationRequestor.and(requestor: DialogValidationRequestor.WithParameter<T>): DialogValidationRequestor.WithParameter<T> =
  DialogValidationRequestor.WithParameter { this and requestor(it) }