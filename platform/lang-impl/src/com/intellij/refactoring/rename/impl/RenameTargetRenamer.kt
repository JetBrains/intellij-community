// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.impl

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.refactoring.rename.Renamer
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.refactoring.rename.inplace.inplaceRename

class RenameTargetRenamer(
  private val project: Project,
  private val editor: Editor?,
  private val target: RenameTarget
) : Renamer {

  override fun getPresentableText(): String = target.presentation().presentableText

  override fun performRename(): Unit = startRename(project, editor, target)
}

/**
 * TODO candidate for a public API
 */
internal fun startRename(project: Project, editor: Editor?, target: RenameTarget) {
  if (editor != null &&
      editor.settings.isVariableInplaceRenameEnabled &&
      inplaceRename(project, editor, target)) {
    return
  }
  showDialogAndRename(project, target)
}
