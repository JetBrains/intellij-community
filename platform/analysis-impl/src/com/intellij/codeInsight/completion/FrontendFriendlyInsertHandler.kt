// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.completion.serialization.InsertHandlerSerializer
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.serialization.DescriptorConverter
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * Marker interface to be used for insert handlers of Backend's lookup elements that are safe to run on Frontend in Remote Development environment.
 *
 * Must not contain any heavy computations, resolve, or index access.
 *
 * Must be registered in `plugin.xml` as `completion.frontendFriendlyInsertHandler` extension point.
 * That said, it's allowed and encouraged to make frontend-friendly insert handlers stateful.
 * Their constructors should accept their state as a parameter.
 *
 * To allow transferring FFIHs to Frontend, make the class `@Serializable` and register it in `plugin.xml`:
 * ```
 *   <completion.frontendFriendlyInsertHandler target="MyFFIH"/>
 * ```
 *
 * If the insert handler cannot be serializable, convert it to a serializable FFIH with [InsertHandlerToFrontendFriendlyConverter].
 *
 * Note: it's explicitly forbidden to specify a custom [LookupElement] as a type parameter because
 * it is going to be called with a generic LookupElement instance on Frontend.
 *
 */
@Serializable(with = InsertHandlerSerializer::class)
@ApiStatus.Experimental
interface FrontendFriendlyInsertHandler : InsertHandler<LookupElement>

/**
 * Converts a backend-only insert handler to a [FrontendFriendlyInsertHandler].
 *
 * Use a converter when the insert handler cannot run on Frontend as is, for example because it keeps PSI or does resolve.
 * The converter takes the data that Frontend needs and puts it into a serializable [FrontendFriendlyInsertHandler].
 *
 * Register the converter in `plugin.xml` together with the target and the descriptor classes:
 * ```
 *   <completion.frontendFriendlyInsertHandler target="MyInsertHandler" converter="MyConverter" descriptor="MyFFIH"/>
 * ```
 *
 * [toDescriptor] returns `null` when the insert handler cannot run on Frontend.
 * Then the lookup element keeps its insert handler on Backend.
 *
 * @see FrontendFriendlyInsertHandler
 */
@ApiStatus.Experimental
interface InsertHandlerToFrontendFriendlyConverter<IH : InsertHandler<*>> : DescriptorConverter<IH, FrontendFriendlyInsertHandler>
