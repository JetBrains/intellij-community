// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.api

import com.intellij.find.usages.symbol.SymbolSearchTargetFactory
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

import org.jetbrains.annotations.ApiStatus.Internal

/**
 * To provide a [RenameValidator] by a [RenameTarget], either:
 *
 *  * implement [RenameTargetRenameValidatorFactory] and register as `com.intellij.rename.renameTargetRenameValidatorFactory` extension
 *  * implement [RenameTargetWithValidation] in a [RenameTarget] to provide rename validation for the target
 *  * implement [RenameValidator] in a [RenameTarget]
 *
 */
interface RenameTargetRenameValidatorFactory {

  /**
   * @return validator to be used when checking validity of new name for a given [renameTarget]
   */
  fun renameValidator(project: Project, renameTarget: RenameTarget): RenameValidator?

  companion object {

    internal val EP_NAME = ExtensionPointName.create<RenameTargetRenameValidatorFactory>(
      "com.intellij.rename.renameTargetRenameValidatorFactory"
    )
  }
}
