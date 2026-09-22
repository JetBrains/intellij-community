// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard

import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import java.nio.file.Path

/**
 * Checks how [NewProjectWizard] seeds the Location field through [com.intellij.ide.util.projectWizard.WizardContext].
 */
class NewProjectWizardLocationTest : NewProjectWizardTestCase() {

  fun testDefaultLocationSeedsProjectDirectory() {
    val location = createTempDirectoryWithSuffix("home")
    val wizard = createWizard(defaultPath = null, defaultLocation = location)

    val context = wizard.wizardContext
    assertTrue(context.isProjectFileDirectorySet)
    assertFalse(context.isProjectFileDirectorySetExplicitly)
    assertEquals(location.normalize(), context.projectDirectory)
    assertNull(context.projectName)
    assertTrue(context.isProjectLocationLocked)
  }

  fun testDefaultPathWinsOverDefaultLocation() {
    val projectPath = createTempDirectoryWithSuffix("project")
    val location = createTempDirectoryWithSuffix("home")
    val wizard = createWizard(defaultPath = projectPath.toString(), defaultLocation = location)

    val context = wizard.wizardContext
    assertTrue(context.isProjectFileDirectorySetExplicitly)
    assertEquals(projectPath.normalize(), context.projectDirectory)
    assertEquals(projectPath.fileName.toString(), context.projectName)
    assertFalse(context.isProjectLocationLocked)
  }

  fun testNoDefaultLocationKeepsSuggestedLocation() {
    val wizard = createWizard(defaultPath = null, defaultLocation = null)

    val context = wizard.wizardContext
    assertFalse(context.isProjectFileDirectorySet)
    assertNull(context.projectName)
    assertFalse(context.isProjectLocationLocked)
  }

  private fun createWizard(defaultPath: String?, defaultLocation: Path?): NewProjectWizard {
    val wizard = NewProjectWizard(null, ModulesProvider.EMPTY_MODULES_PROVIDER, defaultPath, defaultLocation)
    setWizard(wizard)
    return wizard
  }
}
