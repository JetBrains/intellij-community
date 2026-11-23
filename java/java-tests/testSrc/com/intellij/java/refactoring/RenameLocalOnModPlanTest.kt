// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring

import com.intellij.psi.PsiElement
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.refactoring.modplan.rename.ModPlanRenameOptions
import com.intellij.testFramework.ModPlanRenameTestSupport.withEveryRenamer

/**
 * The tests of the legacy rename of local variables, run on the ModPlan engine. A test that fails here is a difference of the ModPlan rename
 * from the legacy one. The inplace tests do not reach the processor, so they run the legacy rename.
 */
class RenameLocalOnModPlanTest : RenameLocalTest() {
  override fun rename(element: PsiElement, newName: String, withRenamers: Boolean) {
    val options = ModPlanOptionValues.EMPTY.with(ModPlanRenameOptions.SEARCH_IN_COMMENTS, true).with(ModPlanRenameOptions.SEARCH_TEXT_OCCURRENCES, true)
    renameOnModPlanInTests(project, element, newName, if (withRenamers) { withEveryRenamer(options) } else { options })
  }
}
