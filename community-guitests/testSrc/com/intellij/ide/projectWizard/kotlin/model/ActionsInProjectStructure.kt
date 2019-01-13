// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.model

import com.intellij.testGuiFramework.impl.jTree
import com.intellij.testGuiFramework.util.scenarios.ProjectStructureDialogModel
import com.intellij.testGuiFramework.util.scenarios.checkLibraryPresent
import com.intellij.testGuiFramework.util.scenarios.checkModule
import com.intellij.testGuiFramework.util.step
import org.fest.swing.exception.ComponentLookupException

// Attention: it's supposed that Project Structure dialog is open both before the function
// executed and after
fun ProjectStructureDialogModel.checkFacetInOneModule(expectedFacet: FacetStructure, vararg path: String) {
  checkModule {
    with(guiTestCase) {
      step("Check facet for module `${path.joinToString(" -> ")}`") {
        try {
          jTree(*path).clickPath()
          (this as KotlinGuiTestCase).checkFacetState(expectedFacet)
        }
        catch (e: ComponentLookupException) {
          val errorMessage = "Kotlin facet for module `${path.joinToString(" -> ")}` not found"
          throw IllegalStateException(errorMessage, e as Throwable)
        }
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
