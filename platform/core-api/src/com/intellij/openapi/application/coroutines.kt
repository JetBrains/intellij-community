// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.openapi.application

import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asContextElement
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.CoroutineContext

/**
 * Suspends until it's possible to obtain the read lock and then
 * runs the [action] holding the lock **without** preventing write actions.
 * See [constrainedReadAction] for semantic details.
 *
 * @see readActionBlocking
 */
suspend fun <T> readAction(action: () -> T): T {
  return constrainedReadAction(action = action)
}

/**
 * Suspends until it's possible to obtain the read lock in smart mode and then
 * runs the [action] holding the lock **without** preventing write actions.
 * See [constrainedReadAction] for semantic details.
 *
 * @see smartReadActionBlocking
 */
suspend fun <T> smartReadAction(project: Project, action: () -> T): T {
  return constrainedReadAction(ReadConstraint.inSmartMode(project), action = action)
}

/**
 * Runs given [action] under [read lock][com.intellij.openapi.application.Application.runReadAction]
 * **without** preventing write actions.
 *
 * The function suspends if at the moment of calling it's not possible to acquire the read lock,
 * or if [constraints] are not [satisfied][ReadConstraint.isSatisfied].
 * If the write action happens while the [action] is running, then the [action] is canceled,
 * and the function suspends until its possible to acquire the read lock, and then the [action] is tried again.
 *
 * Since the [action] might me executed several times, it must be idempotent.
 * The function returns when given [action] was completed fully.
 * To support cancellation, the [action] must regularly invoke [com.intellij.openapi.progress.ProgressManager.checkCanceled].
 *
 * @see constrainedReadActionBlocking
 */
suspend fun <T> constrainedReadAction(vararg constraints: ReadConstraint, action: () -> T): T {
  return readActionSupport().executeReadAction(constraints.toList(), blocking = false, action)
}

/**
 * Suspends until it's possible to obtain the read lock and then
 * runs the [action] holding the lock and **preventing** write actions.
 * See [constrainedReadActionBlocking] for semantic details.
 *
 * @see readAction
 */
suspend fun <T> readActionBlocking(action: () -> T): T {
  return constrainedReadActionBlocking(action = action)
}

/**
 * Suspends until it's possible to obtain the read lock in smart mode and then
 * runs the [action] holding the lock and **preventing** write actions.
 * See [constrainedReadActionBlocking] for semantic details.
 *
 * @see smartReadAction
 */
suspend fun <T> smartReadActionBlocking(project: Project, action: () -> T): T {
  return constrainedReadActionBlocking(ReadConstraint.inSmartMode(project), action = action)
}

/**
 * Runs given [action] under [read lock][com.intellij.openapi.application.Application.runReadAction]
 * **preventing** write actions.
 *
 * The function suspends if at the moment of calling it's not possible to acquire the read lock,
 * or if [constraints] are not [satisfied][ReadConstraint.isSatisfied].
 * If the write action happens while the [action] is running, then the [action] is **not** canceled,
 * meaning the [action] will block pending write actions until finished.
 *
 * The function returns when given [action] was completed fully.
 * To support cancellation, the [action] must regularly invoke [com.intellij.openapi.progress.ProgressManager.checkCanceled].
 *
 * @see constrainedReadAction
 */
suspend fun <T> constrainedReadActionBlocking(vararg constraints: ReadConstraint, action: () -> T): T {
  return readActionSupport().executeReadAction(constraints.toList(), blocking = true, action)
}

private fun readActionSupport() = ApplicationManager.getApplication().getService(ReadActionSupport::class.java)

/**
 * The code [without][ModalityState.any] context modality state must only perform pure UI operations,
 * it must not access any PSI, VFS, project model, or indexes.
 */
fun ModalityState.asContextElement(): CoroutineContext = coroutineSupport().asContextElement(this)

/**
 * @return UI dispatcher which dispatches within the [context modality state][asContextElement].
 */
@Suppress("unused") // unused receiver
val Dispatchers.EDT: CoroutineContext get() = coroutineSupport().edtDispatcher()

private fun coroutineSupport() = ApplicationManager.getApplication().getService(CoroutineSupport::class.java)
