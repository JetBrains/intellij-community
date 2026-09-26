// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.impl

import com.intellij.openapi.command.undo.BasicUndoableAction
import com.intellij.refactoring.rename.api.ModifiableRenameUsage.ModelUpdate

internal class RenameUndoableAction(
  private val modelUpdate: ModelUpdate,
  private val oldName: String,
  private val newName: String
) : BasicUndoableAction() {

  override fun undo() {
    modelUpdate.updateModel(oldName)
  }

  override fun redo() {
    modelUpdate.updateModel(newName)
  }
}
