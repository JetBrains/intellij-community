// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import org.jetbrains.annotations.ApiStatus

/**
 * - `false` means log an exception and proceed
 * - `true` means throw an exception
 */
@get:ApiStatus.Internal
val isMessageBusThrowsWhenDisposed: Boolean = System.getProperty("ijpl.message.bus.throws.when.disposed", "true").toBoolean()

/**
 * - `false` means no implicit write-intent lock for activities and coroutines
 * - `true` means `IdeEventQueue` will wrap activities with the write-intent lock
 */
@get:ApiStatus.Internal
val isCoroutineWILEnabled: Boolean = System.getProperty("ide.coroutine.write.intent.lock", "true").toBoolean()

/**
 * - `false` means exceptions from [com.intellij.util.messages.Topic] subscribers are being logged
 * - `true` means exceptions from [com.intellij.util.messages.Topic] subscribers are being rethrown
 *    at [com.intellij.util.messages.MessageBus.syncPublisher] usages (the old behavior)
 */
@get:ApiStatus.Internal
val isMessageBusErrorPropagationEnabled: Boolean = System.getProperty("ijpl.message.bus.rethrows.errors.from.subscribers", "false").toBoolean()

/**
 * - `false` means that lock permits are bound only to threads
 * - `true` means that lock permits also stored in coroutine contexts
 */
@get:ApiStatus.Internal
val isLockStoredInContext: Boolean = System.getProperty("ide.store.lock.in.context", "true").toBoolean()

/**
 * - `false` means that [backgroundWriteAction] will perform write actions from a non-modal context on a background thread
 * - `true` means that [backgroundWriteAction] will perform write actions in and old way (on EDT)
 */
@ApiStatus.Internal
val useBackgroundWriteAction: Boolean = System.getProperty("idea.background.write.action.enabled", "false").toBoolean()

/**
 * - `false` means wrong action chains are ignored and not reported
 * - `true` means chains of actions like `WriteIntentReadAction -> ReadAction -> WriteAction` will be reported as warnings
 *
 *  Such reporting fails tests as I/O takes too much time and tests timeouts.
 */
@get:ApiStatus.Internal
val reportInvalidActionChains: Boolean = System.getProperty("ijpl.report.invalid.action.chains", "false").toBoolean()

/**
 * - `false` means `Application.invokeLater()` calls with implicit `ModalityState.any()` are not reported
 * - `true` means `Application.invokeLater()` calls with implicit `ModalityState.any()` are reported via `LOG.error()`
 */
@get:ApiStatus.Internal
val reportInvokeLaterWithoutModality: Boolean = System.getProperty("ijpl.report.invoke.without.modal", "false").toBoolean()

/**
 * A flag that determines whether to apply user-interactive QoS (Quality of Service) settings for the Event Dispatch Thread (EDT).
 *
 * This property is internally managed and its value is configured via the system property `ide.set.qos.for.edt`.
 * By default, the property is set to `true`.
 *
 * This configuration may influence the prioritization of tasks executed on the EDT.
 */
@get:ApiStatus.Internal
val setUserInteractiveQosForEdt: Boolean = System.getProperty("ide.set.qos.for.edt", "true").toBoolean()

/**
 * Experimental mode for EditorImpl over Rhizome and Andel.
 * See <a href="https://youtrack.jetbrains.com/issue/IJPL-54">IJPL-54</a>
 *
 * - `false` means old reliable behavior
 * - `true` means new experimental behavior
 */
@get:ApiStatus.Internal
val isRhizomeAdEnabled: Boolean = System.getProperty("ijpl.rhizome.ad.enabled", "false").toBoolean()
