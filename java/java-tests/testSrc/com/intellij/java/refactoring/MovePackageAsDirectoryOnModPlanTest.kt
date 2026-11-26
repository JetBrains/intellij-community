// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring

import com.intellij.psi.PsiDirectory
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.testFramework.ModPlanMoveTestSupport
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.extension.ExtendWith

@TestApplication
@ExtendWith(KnownGapsExtension::class)
class MovePackageAsDirectoryOnModPlanTest : MovePackageAsDirectoryTest() {
  override fun moveDirectory(directory: PsiDirectory, targetDirectory: PsiDirectory) {
    ModPlanMoveTestSupport.moveOnModPlan(directory.project, listOf(directory), targetDirectory.virtualFile.url, ModPlanOptionValues.EMPTY)
  }
}