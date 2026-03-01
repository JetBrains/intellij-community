// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.TailType
import com.intellij.codeInsight.completion.serialization.TailTypeSerializer
import com.intellij.codeInsight.serialization.DescriptorConverter
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * Marker interface for tail types that are safe to run on Frontend in Remote Development environment.
 *
 * Similar to [FrontendFriendlyInsertHandler], tail types implementing this interface
 * must not contain any heavy computations, resolve, or index access.
 *
 * Must be registered in `plugin.xml` as `completion.frontendFriendlyTailType` extension point.
 *
 * To allow transferring FFTTs to Frontend, you either need to make the class `@kotlinx.Serializable`
 * or add a converter to a serializable Data Transfer Object.
 * If you prefer DTO way, you must register the converter and DTO classes in plugin.xml:
 * ```
 *   <completion.frontendFriendlyTailType target="MyTailType" converter="MyTailTypeConverter" descriptor="MyTailTypeDto"/>
 * ```
 *
 * @see FrontendFriendlyInsertHandler
 * @see LookupElementWithEffectiveInsertHandler
 */
@Serializable(with = TailTypeSerializer::class)
@ApiStatus.Internal
interface FrontendFriendlyTailType

/**
 * Converter from target [TailType] implementation to [FrontendFriendlyTailType].
 *
 * Similar to [InsertHandlerToFrontendFriendlyConverter].
 */
@ApiStatus.Internal
interface TailTypeToFrontendFriendlyConverter<TT : TailType> : DescriptorConverter<TT, FrontendFriendlyTailType>
