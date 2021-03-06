// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  override fun getPresentableText(): String = target.presentation.presentableText

  override fun performRename() {
    if (editor != null &&
        editor.settings.isVariableInplaceRenameEnabled &&
        inplaceRename(project, editor, target)) {
      return
    }
    showDialogAndRename(project, target)
  }
}
