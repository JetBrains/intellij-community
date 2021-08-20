// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import org.jetbrains.annotations.Nullable
import java.io.File

abstract class NewWizardModuleBuilder<T> : ModuleBuilder() {
  private var step: NewModuleStepWithSettings<T>? = null

  abstract fun createStep(context: WizardContext): NewModuleStepWithSettings<T>

  final override fun getCustomOptionsStep(context: WizardContext, parentDisposable: Disposable): ModuleWizardStep {
    return createStep(context).also { step = it }
  }

  fun createProject(context: WizardContext): @Nullable Project? {
    val name = step!!.baseSettings.name
    val path = step!!.baseSettings.path

    val toPath = File(path, name).toPath()
    val project = ProjectManagerEx.getInstanceEx().newProject(toPath, OpenProjectTask())
    if (project == null) {
      LOG.error("Cannot create project by path: $toPath")
      return null
    }

    step!!.setupProject(project, context)
    return project
  }

  override fun cleanup() {
    step = null
  }

  companion object {
    const val DEFAULT_GROUP = "Default"
    const val GENERATORS = "Generators"

    private val LOG = logger<NewWizardModuleBuilder<*>>()
  }
}