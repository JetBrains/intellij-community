// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.model

import com.intellij.testGuiFramework.impl.selectWithKeyboard
import com.intellij.testGuiFramework.util.logError
import com.intellij.testGuiFramework.util.logUIStep
import com.intellij.testGuiFramework.util.scenarios.*

const val localTimeout = 2L // default timeout is 2 minutes and it's too big for most of tasks here

// Attention: it's supposed that Project Structure dialog is open both before the function
// executed and after
fun ProjectStructureDialogModel.checkFacetInOneModule(expectedFacet: FacetStructure, vararg path: String) {
  checkModule {
    with(guiTestCase) {
      try {
        jTree(path[0], timeout = localTimeout).selectWithKeyboard(this, *path)
        logUIStep("Check facet for module `${path.joinToString(" -> ")}`")
        (this as KotlinGuiTestCase).checkFacetState(expectedFacet)
      }
      catch (e: Exception) {
        guiTestCase.logError("Kotlin facet for module `${path.joinToString(" -> ")}` not found")
      }
    }
  }
}

// Attention: it's supposed that Project Structure dialog is open both before the function
// executed and after
fun ProjectStructureDialogModel.checkLibrariesFromMavenGradle(buildSystem: BuildSystem,
                                                              kotlinVersion: String,
                                                              expectedJars: Collection<String>) {
  if (buildSystem == BuildSystem.IDEA) return
  expectedJars
    .map { "$buildSystem: $it${if (it.endsWith(":")) kotlinVersion else ""}" }
    .forEach { checkLibraryPresent(it) }
}

// Attention: it's supposed that Project Structure dialog is open both before the function
// executed and after
fun ProjectStructureDialogModel.checkLibrariesFromIDEA(expectedLibName: String,
                                                       expectedJars: Collection<String>) {
  expectedJars
    .map { arrayOf(if (it.endsWith("-sources.jar")) "Sources" else "Classes", it) }
    .forEach { checkLibraryPresent(*it) }
}
