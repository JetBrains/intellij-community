// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.refactoring.modplan.rename.ModPlanRenameOptions
import com.intellij.refactoring.rename.ModPlanJavaRenameOptions
import com.intellij.testFramework.ModPlanRenameTestSupport
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import java.lang.reflect.Method

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
 * Runs the tests of a Java twin on the ModPlan engine. A test of [KNOWN_GAPS] must fail with the failure of its gap, so it fails once the
 * gap is gone. A twin with a test in [KNOWN_GAPS] registers this extension with `@ExtendWith`.
 */
internal class KnownGapsExtension : InvocationInterceptor {
  override fun interceptTestMethod(
    invocation: InvocationInterceptor.Invocation<Void?>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ) {
    val testName = "${extensionContext.requiredTestClass.simpleName}.${extensionContext.requiredTestMethod.name}"
    val gap = KNOWN_GAPS.values.find { testName in it.testNames }
    if (gap == null) {
      invocation.proceed()
      return
    }
    val failure = runCatching { invocation.proceed() }.exceptionOrNull()
    assertNotNull(failure, "The known gap is gone: remove $testName from KNOWN_GAPS")
    assertTrue(failure!!.message.orEmpty().startsWith(gap.failure), failure.message)
  }
}

/** The failure that the tests of a known gap give, as the start of the message, and the names of the tests as `Class.testName`. */
private class KnownGap(val failure: String, val testNames: List<String>)

private val KNOWN_GAPS: Map<String, KnownGap> = mapOf(
  "The package is the prefix of a source root. Its rename changes the module model, so the ModPlan rename refuses it, and the interactive rename runs the legacy one (ModPlanJavaPackageRenameProvider.isLegacyOnly)" to
    KnownGap("refused: NOT_SUPPORTED", listOf("RenamePackageOnModPlanTest.testRenameInPackagePrefix")),
  "A directory of a package with several directories. The ModPlan move refuses it, " +
    "as the rename refuses it (RenamePsiDirectoryProcessor.directoryOrItsPackage)" to
    KnownGap("refused: NOT_SUPPORTED", listOf(
      "MovePackageAsDirectoryOnModPlanTest.testMovePackage",
      "MovePackageAsDirectoryOnModPlanTest.testMovePackageWithTxtFilesInside",
      "MovePackageAsDirectoryOnModPlanTest.testMultipleClassesInOneFile",
      "MovePackageAsDirectoryOnModPlanTest.testRemoveUnresolvedImports",
      "MovePackageAsDirectoryOnModPlanTest.testXmlDirRefs"
    )),
  "No Java move provider owns the subject, so the engine refuses it." to
    KnownGap("refused: NOT_SUPPORTED", listOf(
      "MoveClassOnModPlanTest.testWithoutReference",
      "MoveClassOnModPlanTest.testConflictingNames",
      "MoveClassOnModPlanTest.testLocalClass",
      "MoveClassOnModPlanTest.testRefInPropertiesFile",
      "MoveClassOnModPlanTest.testIdeadev27996",
      "MoveClassOnModPlanTest.testMoveMultiple1",
      "MoveClassOnModPlanTest.testContextChange2",
      "MoveClassOnModPlanTest.testImplicitClass",
      "MoveClassOnModPlanTest.testContextChange1",
      "MoveClassOnModPlanTest.testNonJava",
      "MoveClassOnModPlanTest.testSecondaryClass",
      "MoveClassOnModPlanTest.testStringsAndComments2",
      "MoveClassOnModPlanTest.testToDefaultPackage",
      "MoveClassOnModPlanTest.testQualifiedRef",
      "MoveClassOnModPlanTest.testClassAndSecondary",
      "MoveClassOnModPlanTest.testUnusedImport",
      "MoveClassOnModPlanTest.testStringsAndComments",
      "MovePackageOnModPlanTest.testMoveSingle",
      "MovePackageOnModPlanTest.testQualifiedRef",
      "MovePackageOnModPlanTest.testInsidePackage",
      "MovePackageOnModPlanTest.testPackageAndReferencedClass",
      "MovePackageMultirootOnModPlanTest.testMovePackage",
      "MovePackageMultirootOnModPlanTest.testMoveFilesToEmptyPkg1",
      "MovePackageMultirootOnModPlanTest.testMoveFilesToEmptyPkg2",
      "MovePackageMultirootOnModPlanTest.testMoveFilesToNonEmptyPkg1",
      "MovePackageMultirootOnModPlanTest.testMoveFilesToNonEmptyPkg2",
      "MovePackageMultirootOnModPlanTest.testMoveFilesToNonEmptyPkg3",
      "MoveJavaFileOnModPlanTest.testPackageInfo",
      "MoveJavaFileOnModPlanTest.testConflict"
    )),
)
