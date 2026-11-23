// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring

import com.intellij.psi.PsiElement
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.testFramework.ModPlanRenameTestSupport.withEveryRenamer

/**
 * The tests of the legacy rename of methods in several files, run on the ModPlan engine. A test that fails here is a difference of the
 * ModPlan rename from the legacy one.
 */
class RenameMethodMultiOnModPlanTest : RenameMethodMultiTest() {
  override fun rename(element: PsiElement, newName: String, withRenamers: Boolean) {
    val options = if (withRenamers) { withEveryRenamer(ModPlanOptionValues.EMPTY) } else { ModPlanOptionValues.EMPTY }
    renameOnModPlanInTests(element.project, element, newName, options)
  }
}
