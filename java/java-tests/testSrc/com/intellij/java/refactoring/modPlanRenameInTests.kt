// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.refactoring.modplan.rename.ModPlanRenameOptions
import com.intellij.refactoring.rename.ModPlanJavaRenameOptions
import com.intellij.testFramework.ModPlanRenameTestSupport
import junit.framework.TestCase
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue

/**
 * Renames [target] on the ModPlan engine as the legacy Java test does, see [ModPlanRenameTestSupport.renameOnModPlan]: a legacy test gives
 * the element to RenameProcessor, which renames it without the question about its base method, and runs the automatic renamers.
 */
internal fun renameOnModPlanInTests(project: Project, target: PsiElement, newName: String, options: ModPlanOptionValues) {
  // RenameProcessor runs the factories with no checkbox always.
  val withRenamers = if (options.has(ModPlanRenameOptions.RENAMERS)) {
    options
  }
  else {
    ModPlanRenameOptions.withRenamers(options, ModPlanRenameOptions.renamerIds { false })
  }
  val alone = withRenamers.with(ModPlanJavaRenameOptions.RENAME_BASE_METHOD, false)
  ModPlanRenameTestSupport.renameOnModPlan(project, target, newName, alone)
}

/**
 * Runs [test] of a Java twin on the ModPlan engine. A test of [KNOWN_GAPS] must fail with the failure of its gap, so it fails once the
 * gap is gone. A twin with a test in [KNOWN_GAPS] calls this function from `runTestRunnable`.
 */
internal fun runWithKnownGaps(testCase: TestCase, test: () -> Unit) {
  val testName = "${testCase.javaClass.simpleName}.${testCase.name}"
  val gap = KNOWN_GAPS.values.find { testName in it.testNames }
  if (gap == null) {
    test()
    return
  }
  val failure = runCatching(test).exceptionOrNull()
  assertNotNull("The known gap is gone: remove $testName from KNOWN_GAPS", failure)
  assertTrue(failure!!.message, failure.message.orEmpty().startsWith(gap.failure))
}

/** The failure that the tests of a known gap give, as the start of the message, and the names of the tests as `Class.testName`. */
private class KnownGap(val failure: String, val testNames: List<String>)

private val KNOWN_GAPS: Map<String, KnownGap> = mapOf(
  "The package is the prefix of a source root. Its rename changes the module model, so the ModPlan rename refuses it, and the interactive rename runs the legacy one (ModPlanJavaPackageRenameProvider.isLegacyOnly)" to
    KnownGap("refused: NOT_SUPPORTED", listOf("RenamePackageOnModPlanTest.testRenameInPackagePrefix")),
)
