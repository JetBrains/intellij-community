// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.ide.wizard.AbstractWizardStepEx
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

//TODO: suggest "predefined" configurations (e.g one per every configured SFTP connection)
abstract class RemoteTargetType<C : RemoteTargetConfiguration>(id: String) : BaseExtendableType<C>(id) {
  open fun providesNewWizard(project: Project, runtimeType: LanguageRuntimeType<*>?): Boolean = false

  open fun createStepsForNewWizard(project: Project, configToConfigure: C, runtimeType: LanguageRuntimeType<*>?)
    : List<AbstractWizardStepEx>? = null

  abstract fun createRunner(project: Project, config: C): TargetEnvironmentFactory

  companion object {
    @JvmStatic
    val EXTENSION_NAME = ExtensionPointName.create<RemoteTargetType<*>>("com.intellij.ir.targetType")
  }
}