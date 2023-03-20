// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.operation.core

import com.intellij.openapi.observable.dispatcher.SingleEventDispatcher0
import org.jetbrains.annotations.ApiStatus

/**
 * Defines observation API for observable process:
 * without any modification functions that can change operation status [isOperationInProgress].
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ObservableOperationTrace {

  val name: String

  val status: ObservableOperationStatus

  val scheduleObservable: SingleEventDispatcher0

  val startObservable: SingleEventDispatcher0

  val finishObservable: SingleEventDispatcher0
}