// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.spi

import com.intellij.java.refactoring.renameOnModPlanInTests
import com.intellij.psi.PsiElement
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.refactoring.modplan.rename.ModPlanRenameOptions

/**
 * The tests of the legacy rename of the implementations in the service provider files, run on the ModPlan engine. A test that fails here is a difference of the ModPlan rename from
 * the legacy one.
 */
class SPIRenameOnModPlanTest : SPIRenameTest() {
  override fun rename(element: PsiElement, newName: String) {
    renameOnModPlanInTests(element.project, element, newName, ModPlanOptionValues.EMPTY.with(ModPlanRenameOptions.SEARCH_IN_COMMENTS, true).with(ModPlanRenameOptions.SEARCH_TEXT_OCCURRENCES, true))
  }
}
