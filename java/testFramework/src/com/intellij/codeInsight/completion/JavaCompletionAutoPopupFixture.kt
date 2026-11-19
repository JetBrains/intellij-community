// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.TestModeFlags
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.TestOnly

/**
 * The fixture of a Java completion test that types with the real completion auto-popup. Create it with [javaCompletionAutoPopupFixture].
 *
 * The auto-popup is on from the start of the test to its end.
 * Call the members from the test thread, not from the EDT.
 * [type] and the `join*` methods of [tester] wait for the EDT, so a call from the EDT hangs.
 */
@TestOnly
class JavaCompletionAutoPopupFixture internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
) {
  /**
   * The auto-popup tester on [fixture]. Use it for `joinAutopopup`, `joinCompletion` and `joinCommit`.
   */
  val tester: CompletionAutoPopupTester = CompletionAutoPopupTester(fixture)

  /**
   * Types [s] one character at a time. After each character, it waits for the auto-popup and the completion.
   */
  fun type(s: String) {
    tester.typeWithPauses(s)
  }

  /**
   * Returns the active lookup, or `null` when no lookup shows.
   */
  fun getLookup(): LookupImpl? = fixture.lookup as LookupImpl?
}

/**
 * Creates a [JavaCompletionAutoPopupFixture] for one test. Declare it as an instance field.
 *
 * The fixture turns on [CompletionAutoPopupHandler.ourTestingAutopopup] after the code insight fixture is set up.
 * After the test, it sets the modification stamp of the editor document to 0, so that a pending auto-popup request cancels itself.
 * Then it turns the flag off, before the code insight fixture tears down.
 */
@TestOnly
@JvmOverloads
fun javaCompletionAutoPopupFixture(
  projectFixture: TestFixture<LightJavaProject>,
  basePath: String = "",
): TestFixture<JavaCompletionAutoPopupFixture> = testFixture("javaCompletionAutoPopupFixture") {
  val fixture = lightJavaCodeInsightFixture(projectFixture, basePath).init()
  val flagDisposable = Disposer.newDisposable("javaCompletionAutoPopupFixture")
  TestModeFlags.set(CompletionAutoPopupHandler.ourTestingAutopopup, true, flagDisposable)
  initialized(JavaCompletionAutoPopupFixture(fixture)) {
    try {
      (fixture.editor?.document as DocumentEx?)?.modificationStamp = 0
    }
    finally {
      Disposer.dispose(flagDisposable)
    }
  }
}
