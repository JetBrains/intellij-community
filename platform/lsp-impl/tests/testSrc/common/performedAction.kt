// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.common

import com.intellij.openapi.actionSystem.ex.ActionContextElement
import kotlinx.coroutines.withContext

/**
 * Runs [body] with [actionId] named as the action being performed, the way
 * `ActionManagerImpl.performWithActionCallbacks` names it while a real action runs.
 *
 * The LSP navigation features answer only while a matching action is running, and they read the action from the thread
 * context. Tests that drive a handler directly -- to see its result instead of where it navigates -- have no action to
 * carry that, so they install the element themselves.
 */
internal suspend fun <T> withPerformedAction(actionId: String, body: suspend () -> T): T =
  withContext(ActionContextElement(actionId, place = "", inputEventId = -1, parent = null)) {
    body()
  }
