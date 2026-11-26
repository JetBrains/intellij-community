// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring

import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.refactoring.modplan.move.ModPlanMoveOptions
import com.intellij.testFramework.ModPlanMoveTestSupport
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.extension.ExtendWith

@TestApplication
@ExtendWith(KnownGapsExtension::class)
class MovePackageOnModPlanTest : MovePackageTest() {
  override fun move(packagesAndClasses: Array<PsiElement>, targetDirectory: PsiDirectory) {
    val options = ModPlanOptionValues.EMPTY.with(ModPlanMoveOptions.SEARCH_IN_COMMENTS, true)
    ModPlanMoveTestSupport.moveOnModPlan(targetDirectory.project, ModPlanMoveTestSupport.filesOf(packagesAndClasses.toList()),
                                         targetDirectory.virtualFile.url, options)
  }
}