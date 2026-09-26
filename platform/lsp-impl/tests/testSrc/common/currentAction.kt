// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.common

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.components.service
import com.intellij.platform.lsp.impl.features.navigation.CurrentActionHolder

/**
 * Runs [body] with [CurrentActionHolder] set to [actionClass],
 * the same way `CurrentActionListener` sets it during a real action.
 * The LSP navigation providers send requests only while a matching action runs.
 */
internal suspend fun <T> withCurrentAction(actionClass: Class<out AnAction>, body: suspend () -> T): T {
  val holder = service<CurrentActionHolder>()
  holder.currentActionClass = actionClass
  try {
    return body()
  }
  finally {
    holder.currentActionClass = null
  }
}
