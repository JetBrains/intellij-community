// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import org.jetbrains.annotations.ApiStatus

private const val ENABLE_NEW_LOCK_PROPERTY = "idea.enable.new.lock"
private const val COROUTINE_WIL_PROPERTY = "ide.coroutine.write.intent.lock"

@get:ApiStatus.Internal
val isNewLockEnabled: Boolean
  get() = System.getProperty(ENABLE_NEW_LOCK_PROPERTY, "true").toBoolean()

/**
 * - `false` means log an exception and proceed.
 * - `true` means throw an exception.
 */
@get:ApiStatus.Internal
val isMessageBusThrowsWhenDisposed: Boolean =
  System.getProperty("ijpl.message.bus.throws.when.disposed", "true").toBoolean()

/**
 * - `false` means no implicit write intent lock for activities and coroutines
 * - `true` means [IdeEventQueue] will wrap activities into write intent lock.
 */
@get:ApiStatus.Internal
val isCoroutineWILEnabled: Boolean =
  System.getProperty(COROUTINE_WIL_PROPERTY, "true").toBoolean()
