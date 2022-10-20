// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.impl

import com.intellij.openapi.project.Project
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.refactoring.rename.api.RenameTargetRenameValidatorFactory
import com.intellij.refactoring.rename.api.RenameTargetWithValidation
import com.intellij.refactoring.rename.api.RenameValidator

internal object RenameValidatorFactory {

  fun renameValidator(project: Project, renameTarget: RenameTarget): RenameValidator? {
    for (factory: RenameTargetRenameValidatorFactory in RenameTargetRenameValidatorFactory.EP_NAME.extensions) {
      return factory.renameValidator(project, renameTarget) ?: continue
    }
    if (renameTarget is RenameTargetWithValidation) {
      return renameTarget.renameValidator
    }
    if (renameTarget is RenameValidator) {
      return renameTarget
    }
    return null
  }

}