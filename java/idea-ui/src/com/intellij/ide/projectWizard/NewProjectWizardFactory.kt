// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import org.jetbrains.annotations.TestOnly

interface NewProjectWizardFactory {
  fun create(project: Project, modulesProvider: ModulesProvider): NewProjectWizard

  companion object {
    @JvmStatic
    @JvmName("create")
    operator fun invoke() = ApplicationManager.getApplication().getService(NewProjectWizardFactory::class.java)
                            ?: NewProjectWizardFactoryImpl()
  }
}

internal class NewProjectWizardFactoryImpl : NewProjectWizardFactory {
  override fun create(project: Project, modulesProvider: ModulesProvider): NewProjectWizard {
    return NewProjectWizard(project, modulesProvider)
  }
}

@TestOnly
internal class TestNewProjectWizardFactoryImpl : NewProjectWizardFactory {
  override fun create(project: Project, modulesProvider: ModulesProvider): NewProjectWizard {
    return object : NewProjectWizard(project, modulesProvider) {
      override fun showAndGet(): Boolean {
        return true
      }
    }
  }
}