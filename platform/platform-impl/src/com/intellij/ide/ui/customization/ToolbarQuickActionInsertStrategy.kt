// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.customization

sealed interface ToolbarQuickActionInsertStrategy {

  fun addActions(actionIds: List<String>, schema: CustomActionsSchema): Boolean

  fun checkExists(actionId: String, schema: CustomActionsSchema): Boolean

  fun orElse(other: ToolbarQuickActionInsertStrategy): ToolbarQuickActionInsertStrategy = JoinStrategy(this, other)
}

private class JoinStrategy(val first: ToolbarQuickActionInsertStrategy, val second: ToolbarQuickActionInsertStrategy): ToolbarQuickActionInsertStrategy {
  override fun addActions(actionIds: List<String>, schema: CustomActionsSchema): Boolean {
    if (first.addActions(actionIds, schema)) return true
    else return second.addActions(actionIds, schema)
  }

  override fun checkExists(actionId: String, schema: CustomActionsSchema) = first.checkExists(actionId, schema) || second.checkExists(actionId, schema)
}
