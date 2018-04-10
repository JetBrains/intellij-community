// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.model

import com.intellij.testGuiFramework.util.*

// Attention: it's supposed that Project Structure dialog is open both before the function
// executed and after
fun KotlinGuiTestCase.checkFacetInOneModule(expectedFacet: FacetStructure, vararg path: String){
  dialog("Project Structure", needToKeepDialog = true){
    logUIStep("Click Modules")
    jList("Modules").clickItem("Modules")

    try {
      for(step in 0 until path.size){
        jTree(path[0]).doubleClickXPath(*path.copyOfRange(0, step + 1))
      }
      logUIStep("Check facet for module `${path.joinToString(" -> ")}`")
      checkFacetState(expectedFacet)
    }
    catch (e: Exception) {
      logError("Kotlin facet for module `${path.joinToString(" -> ")}` not found")
    }
  }
}

// Attention: it's supposed that Project Structure dialog is open both before the function
// executed and after
fun KotlinGuiTestCase.checkLibrariesFromMavenGradle(buildSystem: BuildSystem,
                                                    kotlinVersion: String,
                                                    expectedJars: Collection<String>){
  if(buildSystem == BuildSystem.IDEA) return
  dialog("Project Structure", needToKeepDialog = true){
    val tabs = jList("Libraries")
    logUIStep("Click Libraries")
    tabs.clickItem("Libraries")
    expectedJars
      .map { "$buildSystem: $it${if (it.endsWith(":")) kotlinVersion else ""}" }
      .forEach { testTreeItemExist("Library", it) }
  }
}

// Attention: it's supposed that Project Structure dialog is open both before the function
// executed and after
fun KotlinGuiTestCase.checkLibrariesFromIDEA(expectedLibName: String,
                                             expectedJars: Collection<String>){
  dialog("Project Structure", needToKeepDialog = true){
    val tabs = jList("Libraries")
    logUIStep("Click Libraries")
    tabs.clickItem("Libraries")
    logTestStep("Check that Kotlin libraries are added")
    testTreeItemExist("Library", expectedLibName)
    expectedJars
      .map { arrayOf(if (it.endsWith("-sources.jar")) "Sources" else "Classes", it) }
      .forEach { testTreeItemExist("Library", *it) }
  }
}


fun KotlinGuiTestCase.checkInProjectStructure(actions: KotlinGuiTestCase.()->Unit) {
  logTestStep("Check structure of gradle project")
  ideFrame {
    invokeMainMenu("ShowProjectStructureSettings")
    dialog("Project Structure") {
      try {
        actions()
      }
      catch (t: Throwable) {
        throw t
      }
      finally {
        logUIStep("Close Project Structure dialog with Cancel")
        button("Cancel").click()
      }
    }
  }
}
