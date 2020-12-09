// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.impl

import com.intellij.openapi.project.Project
import com.intellij.refactoring.rename.Renamer
import com.intellij.refactoring.rename.api.RenameTarget

class RenameTargetRenamer(
  private val project: Project,
  private val target: RenameTarget
) : Renamer {

  override fun getPresentableText(): String = target.presentation.presentableText

  override fun performRename(): Unit = rename(project, target)
}
