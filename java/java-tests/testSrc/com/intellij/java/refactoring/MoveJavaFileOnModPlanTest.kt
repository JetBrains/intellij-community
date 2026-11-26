// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring

import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.testFramework.ModPlanMoveTestSupport
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.extension.ExtendWith

@TestApplication
@ExtendWith(KnownGapsExtension::class)
class MoveJavaFileOnModPlanTest : MoveJavaFileTest() {
  override fun moveFile(file: PsiFile, targetDirectory: PsiDirectory) {
    ModPlanMoveTestSupport.moveOnModPlan(file.project, listOf(file), targetDirectory.virtualFile.url, ModPlanOptionValues.EMPTY)
  }
}