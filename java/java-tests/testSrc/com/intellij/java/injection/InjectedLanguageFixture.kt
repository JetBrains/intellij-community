// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.injection

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.Companion.lightDaemonAnalyzerFixture
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerOptions
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.impl.source.tree.injected.MyTestInjector
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.TestOnly
import java.util.function.Supplier

/**
 * Creates a [LightDaemonAnalyzerFixture] for a test of injected languages.
 * Declare it as an instance field.
 *
 * After the setup of [lightDaemonAnalyzerFixture], the fixture registers the injections of [MyTestInjector] until the end of the test.
 * The registration runs without the internal invariant checks of [DebugUtil].
 * The arguments are the same as in [lightDaemonAnalyzerFixture].
 *
 * Put [InjectedLanguageInvariantsExtension] on the test class. It runs each test with the internal invariant checks.
 * [InjectedLanguageTestUtil.toParseTreeText] gives the PSI tree of a file and of its injected files.
 */
@TestOnly
@JvmOverloads
fun injectedLanguageFixture(
  projectFixture: TestFixture<LightJavaProject>,
  localInspectionTools: Supplier<out List<InspectionProfileEntry>> = Supplier { emptyList() },
  options: LightDaemonAnalyzerOptions = LightDaemonAnalyzerOptions.DEFAULT,
): TestFixture<LightDaemonAnalyzerFixture> = testFixture("injectedLanguageFixture") {
  val daemonFixture = lightDaemonAnalyzerFixture(projectFixture, localInspectionTools, options).init()
  val fixture = daemonFixture.fixture
  DebugUtil.runWithCheckInternalInvariantsDisabled {
    MyTestInjector(PsiManager.getInstance(fixture.project)).injectAll(fixture.testRootDisposable)
  }
  initialized(daemonFixture) {}
}
