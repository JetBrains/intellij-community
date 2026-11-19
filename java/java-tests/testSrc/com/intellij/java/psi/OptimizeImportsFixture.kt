// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi

import com.intellij.JavaTestUtil
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection
import com.intellij.lang.ImportOptimizer
import com.intellij.lang.java.JavaImportOptimizer
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.junit.jupiter.api.Assertions.assertEquals

private val LOG = logger<OptimizeImportsFixture>()

/**
 * The optimize imports test of one test method, on [codeInsight].
 * The test data is in [OptimizeImportsFixture.BASE_PATH].
 */
class OptimizeImportsFixture internal constructor(val codeInsight: LightJavaCodeInsightTestFixture) {
  /**
   * Configures `<test name>.java` of the test data, optimizes its imports with [JavaImportOptimizer],
   * and compares the file with `<test name>_after.java`.
   * When [notification] is not null, the user notification of the optimizer must be equal to it.
   * The fixture configures the file outside of the write command, because the new file starts a dumb mode.
   * The other steps run in one write command on the EDT.
   */
  @JvmOverloads
  fun doTest(notification: String? = null) {
    val testName = codeInsight.getTestName(false)
    val file = codeInsight.configureByFile("$testName.java")
    val project = codeInsight.project
    WriteCommandAction.runWriteCommandAction(project) {
      try {
        val runnable = JavaImportOptimizer().processFile(file)
        runnable.run()
        PostprocessReformattingAspect.getInstance(project).doPostponedFormatting()
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        codeInsight.checkResultByFile("${testName}_after.java")
        PsiTestUtil.checkFileStructure(file)
        if (notification != null && runnable is ImportOptimizer.CollectingInfoRunnable) {
          assertEquals(notification, runnable.userNotificationInfo)
        }
      }
      catch (e: Exception) {
        LOG.error(e)
      }
    }
  }

  companion object {
    @JvmField
    val BASE_PATH: String = JavaTestUtil.getJavaTestDataPath() + "/psi/optimizeImports"
  }
}

/**
 * Creates an [OptimizeImportsFixture] for one test. Declare it as an instance field.
 *
 * The fixture sets the test data path to [OptimizeImportsFixture.BASE_PATH] and enables [UnusedDeclarationInspection].
 * After the test, it restores the import settings of [JavaCodeStyleSettings] that the tests change.
 */
fun optimizeImportsFixture(projectFixture: TestFixture<LightJavaProject>): TestFixture<OptimizeImportsFixture> =
  testFixture("optimizeImportsFixture") {
    val codeInsight = lightJavaCodeInsightFixture(projectFixture).init()
    codeInsight.testDataPath = OptimizeImportsFixture.BASE_PATH
    val javaSettings = JavaCodeStyleSettings.getInstance(codeInsight.project)
    val preserveModuleImports = javaSettings.isPreserveModuleImports
    val deleteUnusedModuleImports = javaSettings.isDeleteUnusedModuleImports
    val table = javaSettings.IMPORT_LAYOUT_TABLE
    val classOnDemand = javaSettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND
    val namesOnDemand = javaSettings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND
    Disposer.register(codeInsight.testRootDisposable) {
      javaSettings.isDeleteUnusedModuleImports = deleteUnusedModuleImports
      javaSettings.isPreserveModuleImports = preserveModuleImports
      javaSettings.IMPORT_LAYOUT_TABLE = table
      javaSettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = classOnDemand
      javaSettings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND = namesOnDemand
    }
    codeInsight.enableInspections(UnusedDeclarationInspection())
    initialized(OptimizeImportsFixture(codeInsight)) {}
  }
