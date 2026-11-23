// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon

import com.intellij.codeInsight.daemon.DaemonAnalyzerFixture
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.junit5.fixture.TestFixture
import java.util.function.Function
import java.util.function.Supplier

/**
 * A [DaemonAnalyzerFixture] with the JDK [jdk] for each module and the language level [languageLevel] for the project,
 * as a JUnit 3 test that overrides `getTestProjectJdk()` and `getProjectLanguageLevel()`.
 *
 * Create it with [jdkDaemonAnalyzerFixture].
 */
class JdkDaemonAnalyzerFixture(
  context: Context,
  testDataPath: String,
  localInspectionTools: Array<LocalInspectionTool>,
  private val jdk: Supplier<Sdk>,
  private val languageLevel: LanguageLevel,
) : DaemonAnalyzerFixture(context, testDataPath, localInspectionTools) {
  override fun getTestProjectJdk(): Sdk = jdk.get()

  override fun getProjectLanguageLevel(): LanguageLevel = languageLevel

  companion object {
    /**
     * Creates a [JdkDaemonAnalyzerFixture] with a new project for each test. Declare it as an instance field.
     * See [DaemonAnalyzerFixture.daemonAnalyzerFixture] for the other parameters.
     */
    @JvmStatic
    @JvmOverloads
    fun jdkDaemonAnalyzerFixture(
      testDataPath: String,
      jdk: Supplier<Sdk>,
      languageLevel: LanguageLevel = LanguageLevel.JDK_1_6,
      localInspectionTools: Supplier<Array<LocalInspectionTool>> = Supplier { LocalInspectionTool.EMPTY_ARRAY },
      testDataProjectDir: Function<String, String>? = null,
    ): TestFixture<JdkDaemonAnalyzerFixture> =
      heavyJavaCodeInsightSubFixture("jdkDaemonAnalyzerFixture", testDataProjectDir) { context ->
        JdkDaemonAnalyzerFixture(context, testDataPath, localInspectionTools.get(), jdk, languageLevel)
      }
  }
}
