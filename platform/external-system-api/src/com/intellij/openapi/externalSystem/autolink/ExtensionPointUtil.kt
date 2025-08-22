// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autolink

import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.ORDER_AWARE_COMPARATOR
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
inline fun <Extension : Any, R> Extension.runExtensionSafe(block: Extension.() -> R): R? =
  runCatching(block)
    .getOrLogException(logger<ExtensionPointImpl<*>>())

@ApiStatus.Internal
inline fun <Extension : Any> ExtensionPointName<Extension>.forEachExtensionSafeAsync(action: (Extension) -> Unit): Unit =
  extensionList.asSequence()
    .forEach { it.runExtensionSafe(action) }

@ApiStatus.Internal
inline fun <Extension : Any> ExtensionPointName<Extension>.forEachExtensionSafeOrdered(action: (Extension) -> Unit): Unit =
  extensionList.asSequence()
    .sortedWith(ORDER_AWARE_COMPARATOR)
    .forEach { it.runExtensionSafe(action) }

@ApiStatus.Internal
inline fun <Extension : Any, R : Any> ExtensionPointName<Extension>.mapExtensionSafe(action: (Extension) -> R): List<R> =
  extensionList.mapNotNull { it.runExtensionSafe(action) }