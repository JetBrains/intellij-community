// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import org.jetbrains.annotations.ApiStatus

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
  System.getProperty("ide.coroutine.write.intent.lock", "true").toBoolean()

/**
 * - `false` means exceptions from [com.intellij.util.messages.Topic] subscribers are being logged
 * - `true` means exceptions from [com.intellij.util.messages.Topic] subscribers are being rethrown at [com.intellij.util.messages.MessageBus.syncPublisher] usages (the old behavior)
 */
@get:ApiStatus.Internal
val isMessageBusErrorPropagationEnabled: Boolean =
  System.getProperty("ijpl.message.bus.rethrows.errors.from.subscribers", "false").toBoolean()

/**
 * - `false` means lock permits are bound only to threads
 * - `true` means lock permits also stored in coroutine contexts.
 */
@get:ApiStatus.Internal
val isLockStoredInContext: Boolean =
  System.getProperty("ide.store.lock.in.context", "true").toBoolean()

/**
 * `false` means that [backgroundWriteAction] will perform write actions from a non-modal context on a background thread.
 * `true` means that [backgroundWriteAction] will perform write actions in and old way (on EDT).
 */
@ApiStatus.Internal
val useBackgroundWriteAction: Boolean = System.getProperty("idea.background.write.action.enabled", "false").toBoolean()
