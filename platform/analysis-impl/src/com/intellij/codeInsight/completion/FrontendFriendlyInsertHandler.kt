// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.completion.serialization.InsertHandlerSerializer
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.serialization.DescriptorConverter
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

//TODO IJPL-207762 mark experimental
/**
 * Marker interface to be used for insert handlers of Backend's lookup elements that are safe to run on Frontend in Remote Development environment.
 *
 * Must not contain any heavy computations, resolve, or index access.
 *
 * Must be registered in `plugin.xml` as `completion.frontendFriendlyInsertHandler` extension point.
 * That said, it's allowed and encouraged to make frontend-friendly insert handlers stateful.
 * Their constructors should accept their state as a parameter.
 *
 * To allow transferring FFIHs to Frontend, you either need to make the class @kotlinx.Serializable or add a converter to a serializable Data Transfer Object.
 * If you prefer DTO way, you must register the converter and DTO classes in plugin.xml:
 * ```
 *   <completion.frontendFriendlyInsertHandler target="MyFFIH" converter="MyFFIHConverter" dataTransferObject="MyFFIHDto"/>
 * ```
 *
 * Note: it's explicitly forbidden to specify a custom [LookupElement] as a type parameter because
 * it is going to be called with a generic LookupElement instance on Frontend.
 *
 */
@Serializable(with = InsertHandlerSerializer::class)
@ApiStatus.Internal
interface FrontendFriendlyInsertHandler : InsertHandler<LookupElement>

@ApiStatus.Internal
interface InsertHandlerToFrontendFriendlyConverter<IH : InsertHandler<*>> : DescriptorConverter<IH, FrontendFriendlyInsertHandler>
