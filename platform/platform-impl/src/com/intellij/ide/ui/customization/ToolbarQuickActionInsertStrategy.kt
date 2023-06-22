// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.customization

interface ToolbarQuickActionInsertStrategy {

  fun addToSchema(actionIds: List<String>, schema: CustomActionsSchema): Boolean

  fun orElse(other: ToolbarQuickActionInsertStrategy): ToolbarQuickActionInsertStrategy = join(this, other)
}

private fun join(first: ToolbarQuickActionInsertStrategy, second: ToolbarQuickActionInsertStrategy): ToolbarQuickActionInsertStrategy =
  object: ToolbarQuickActionInsertStrategy {
    override fun addToSchema(actionIds: List<String>, schema: CustomActionsSchema): Boolean {
      if (first.addToSchema(actionIds, schema)) return true
      else return second.addToSchema(actionIds, schema)
    }
  }
