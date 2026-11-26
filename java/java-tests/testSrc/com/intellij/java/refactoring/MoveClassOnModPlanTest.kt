// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDirectory
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.refactoring.modplan.move.ModPlanMoveOptions
import com.intellij.testFramework.ModPlanMoveTestSupport
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.extension.ExtendWith

@TestApplication
@ExtendWith(KnownGapsExtension::class)
class MoveClassOnModPlanTest : MoveClassTest() {
  override fun move(classes: Array<PsiClass>, targetDirectory: PsiDirectory) {
    val options = ModPlanOptionValues.EMPTY
      .with(ModPlanMoveOptions.SEARCH_IN_COMMENTS, true)
      .with(ModPlanMoveOptions.SEARCH_FOR_TEXT, true)
    ModPlanMoveTestSupport.moveOnModPlan(targetDirectory.project, ModPlanMoveTestSupport.filesOf(classes.toList()),
                                         targetDirectory.virtualFile.url, options)
  }

  override fun moveFile(fixture: LightJavaCodeInsightTestFixture, filePath: String, targetPath: String) {
    val file = fixture.psiManager.findFile(fixture.findFileInTempDir(filePath))!!
    val targetDirectory = fixture.psiManager.findDirectory(fixture.findFileInTempDir(targetPath))!!
    ModPlanMoveTestSupport.moveOnModPlan(fixture.project, listOf(file), targetDirectory.virtualFile.url, ModPlanOptionValues.EMPTY)
  }
}