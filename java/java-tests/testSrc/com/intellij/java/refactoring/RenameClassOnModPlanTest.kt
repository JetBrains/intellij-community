// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring

import com.intellij.psi.PsiElement
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.testFramework.ModPlanRenameTestSupport.withEveryRenamer
import com.intellij.refactoring.modplan.rename.ModPlanRenameOptions

/**
 * The tests of the legacy rename of classes, run on the ModPlan engine. A test that fails here is a difference of the ModPlan rename from the
 * legacy one.
 */
class RenameClassOnModPlanTest : RenameClassTest() {
  override fun rename(element: PsiElement, newName: String, withRenamers: Boolean) {
    val options = ModPlanOptionValues.EMPTY.with(ModPlanRenameOptions.SEARCH_IN_COMMENTS, true).with(ModPlanRenameOptions.SEARCH_TEXT_OCCURRENCES, true)
    renameOnModPlanInTests(element.project, element, newName, if (withRenamers) { withEveryRenamer(options) } else { options })
  }
}
