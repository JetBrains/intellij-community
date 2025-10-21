// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.lookup.LookupElement
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

//TODO IJPL-207762 mark experimental
/**
 * Marker interface to be used for insert handlers of Backend's lookup elements that are safe to run on Frontend in Remote Development environment.
 *
 * Must be [kotlinx.serialization.Serializable]
 *
 * Must not contain any heavy computations, resolve, or index access.
 *
 * Must be registered in `plugin.xml` as `completion.frontendFriendlyInsertHandler` extension point.
 * That said, it's allowed and encouraged to make frontend-friendly insert handlers stateful.
 * Their constructors should accept their state as a parameter.
 *
 * Note: it's explicitly forbidden to specify a custom [com.intellij.codeInsight.lookup.LookupElement] as a type parameter because
 * it is going to be called with a generic LookupElement instance on Frontend.
 *
 */
@Serializable(with = FrontendFriendlyInsertHandlerSerializer::class)
@ApiStatus.Internal
interface FrontendFriendlyInsertHandler : InsertHandler<LookupElement>

/**
 * An insert handler that can be converted to a [FrontendFriendlyInsertHandler] depending on the context.
 */
@ApiStatus.Internal
interface FrontendConvertibleInsertHandler<T : LookupElement> : InsertHandler<T> {
  fun asFrontendFriendly(): FrontendFriendlyInsertHandler?
}

/**
 * Marker interface for Backend's lookup elements which [LookupElement.handleInsert] are safe to run on Frontend in Remote Development environment.
 * Must not contain any heavy computations, resolve, or index access.
 */
@ApiStatus.Internal
interface FrontendFriendlyLookupElement {
  /**
   * @return the frontend-friendly insert handler for this lookup element to be transferred to Frontend, or `null` if the frontend-friendly insert handler is not available
   */
  @get:ApiStatus.OverrideOnly
  val frontendFriendlyInsertHandler: FrontendFriendlyInsertHandler?
}

/**
 * Tries to convert the given [InsertHandler] to a [FrontendFriendlyInsertHandler].
 */
@ApiStatus.Internal
fun InsertHandler<*>.asFrontendFriendly(): FrontendFriendlyInsertHandler? =
  this as? FrontendFriendlyInsertHandler ?: (this as? FrontendConvertibleInsertHandler<*>)?.asFrontendFriendly()// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
