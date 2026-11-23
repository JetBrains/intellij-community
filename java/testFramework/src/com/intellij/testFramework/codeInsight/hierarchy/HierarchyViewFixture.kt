// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.codeInsight.hierarchy

import com.intellij.codeInsight.daemon.DaemonAnalyzerFixture
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.ExpectedHighlightingData
import com.intellij.testFramework.junit5.fixture.TestFixture
import org.jetbrains.annotations.TestOnly
import java.io.File
import java.util.function.Supplier

/**
 * A [DaemonAnalyzerFixture] that checks the tree structure of a Type Hierarchy (Ctrl+H), a Call Hierarchy (Ctrl+Alt+H),
 * or a Method Hierarchy (Ctrl+Shift+H).
 *
 * The test data of a test is in `<testDataPath>/<basePath>/<test name>`, where the test name has no `test` prefix.
 * This directory has the source files and `verification.xml`, the expected tree structure.
 *
 * Create it with [hierarchyViewFixture].
 */
@TestOnly
class HierarchyViewFixture(
  context: Context,
  testDataPath: String,
  private val basePath: String,
  private val jdk: Supplier<Sdk>,
  private val languageLevel: LanguageLevel,
) : DaemonAnalyzerFixture(context, testDataPath, LocalInspectionTool.EMPTY_ARRAY) {
  override fun getTestProjectJdk(): Sdk = jdk.get()

  override fun getProjectLanguageLevel(): LanguageLevel = languageLevel

  /**
   * The test data directory of the test, relative to [testDataPath].
   */
  val testBasePath: String
    get() = basePath + "/" + getTestName(false)

  /**
   * Configures [fileNames] of [testBasePath], checks that they have no errors, and checks the tree of [treeStructure]
   * with `verification.xml`. The fixture gets the tree and checks it in a read action.
   */
  fun doHierarchyTest(
    treeStructure: Supplier<out HierarchyTreeStructure>,
    comparator: Comparator<in NodeDescriptor<*>>?,
    vararg fileNames: String,
  ) {
    configureByFiles(null, *fileNames.map { "/$testBasePath/$it" }.toTypedArray())
    // syntax errors can break the hierarchy
    checkHighlighting(ExpectedHighlightingData(editor!!.document, false, false, false))
    val verificationFile = File("$testDataPath/$testBasePath/verification.xml")
    ReadAction.computeBlocking<Unit, Exception> {
      HierarchyViewTestFixture.doHierarchyTest(treeStructure.get(), comparator, verificationFile)
    }
  }

  companion object {
    /**
     * Creates a [HierarchyViewFixture] with a new project for each test. Declare it as an instance field.
     * [basePath] is the directory of the test data directories, relative to [testDataPath].
     * The fixture sets [jdk] for each module and [languageLevel] for the project.
     */
    @TestOnly
    @JvmStatic
    @JvmOverloads
    fun hierarchyViewFixture(
      testDataPath: String,
      basePath: String,
      jdk: Supplier<Sdk>,
      languageLevel: LanguageLevel = LanguageLevel.JDK_1_6,
    ): TestFixture<HierarchyViewFixture> =
      heavyJavaCodeInsightSubFixture("hierarchyViewFixture") { context ->
        HierarchyViewFixture(context, testDataPath, basePath, jdk, languageLevel)
      }
  }
}
