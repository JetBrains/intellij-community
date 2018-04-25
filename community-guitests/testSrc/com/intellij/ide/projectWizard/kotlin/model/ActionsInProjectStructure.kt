// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.model

import com.intellij.testGuiFramework.fixtures.extended.ExtendedTreeFixture
import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.util.*

// Attention: it's supposed that Project Structure dialog is open both before the function
// executed and after
fun KotlinGuiTestCase.checkFacetInOneModule(expectedFacet: FacetStructure, vararg path: String){
  dialog("Project Structure", needToKeepDialog = true){
    logUIStep("Click Modules")
    jList("Modules").clickItem("Modules")

    try {
      jTree(path[0]).selectWithKeyboard(this@checkFacetInOneModule, *path)
      logUIStep("Check facet for module `${path.joinToString(" -> ")}`")
      checkFacetState(expectedFacet)
    }
    catch (e: Exception) {
      logError("Kotlin facet for module `${path.joinToString(" -> ")}` not found")
    }
  }
}

fun ExtendedTreeFixture.selectWithKeyboard(testCase: GuiTestCase, vararg path: String) {
  click()
  testCase.shortcut(Key.HOME) // select the top row
  for (step in 0 until path.size) {
    val regex = Regex("""(.*)\(\d+\)""")
    val clearedPathStep = if (regex.matches(path[step])) regex.matchEntire(path[step])!!.groups[1]?.value ?: path[step] else path[step]
    testCase.typeText(clearedPathStep)
    testCase.shortcut(Key.RIGHT)
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
