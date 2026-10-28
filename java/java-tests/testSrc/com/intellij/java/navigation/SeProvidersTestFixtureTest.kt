// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.navigation

import com.intellij.openapi.application.EDT
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.testFramework.SeProvidersTestFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.minutes

private val TIMEOUT = 1.minutes

/**
 * Tests [SeProvidersTestFixture] with the goto providers of Java code.
 */
class SeProvidersTestFixtureTest : LightJavaCodeInsightFixtureTestCase() {
  /** The providers run off the EDT, as in production. A test changes the PSI in a narrow EDT block. */
  override fun runInDispatchThread(): Boolean = false

  fun `test the session has the frontend and the backend providers`() = timeoutRunBlocking(TIMEOUT) {
    val fixture = SeProvidersTestFixture.create(project, testRootDisposable)

    // The actions provider comes from the frontend module, and the files provider comes from the backend module.
    for (providerId in listOf(SeProviderIdUtils.ACTIONS_ID, SeProviderIdUtils.CLASSES_ID, SeProviderIdUtils.FILES_ID, SeProviderIdUtils.SYMBOLS_ID)) {
      assertEquals(providerId, fixture.getProvider(providerId).id)
    }
    assertThrows(AssertionError::class.java) { fixture.getProvider("NoSuchProvider") }
  }

  fun `test the classes provider finds a project class`() = timeoutRunBlocking(TIMEOUT) {
    val target = withContext(Dispatchers.EDT) {
      myFixture.addClass("package foo; public class Unrelated {}")
      myFixture.addClass("package foo; public class SeFixtureTarget {}")
    }

    SeProvidersTestFixture.forEachGotoImplementation(project) { fixture ->
      assertEquals(listOf(target), fixture.findElements(SeProviderIdUtils.CLASSES_ID, "SeFixtureTarget"))
    }
  }

  fun `test the everywhere search also finds a library class`() = timeoutRunBlocking(TIMEOUT) {
    val stringBuffer = withContext(Dispatchers.EDT) { myFixture.findClass("java.lang.StringBuffer") }

    SeProvidersTestFixture.forEachGotoImplementation(project) { fixture ->
      assertDoesntContain(fixture.findElements(SeProviderIdUtils.CLASSES_ID, "StringBuffer"), stringBuffer)
      assertContainsElements(fixture.findElements(SeProviderIdUtils.CLASSES_ID, "StringBuffer", isEverywhere = true), stringBuffer)
    }
  }

  fun `test the symbols provider finds a method`() = timeoutRunBlocking(TIMEOUT) {
    val method = withContext(Dispatchers.EDT) {
      myFixture.addClass("package foo; public class Holder { public void seFixtureMethod() {} }").methods.single()
    }

    SeProvidersTestFixture.forEachGotoImplementation(project) { fixture ->
      assertEquals(listOf(method), fixture.findElements(SeProviderIdUtils.SYMBOLS_ID, "seFixtureMethod"))
    }
  }

  fun `test the blocking functions work on the EDT`() = timeoutRunBlocking(TIMEOUT) {
    val target = withContext(Dispatchers.EDT) { myFixture.addClass("package foo; public class SeFixtureTarget {}") }

    val found = withContext(Dispatchers.EDT) {
      SeProvidersTestFixture.createBlocking(project, testRootDisposable)
        .findElementsBlocking(SeProviderIdUtils.CLASSES_ID, "SeFixtureTarget")
    }
    assertEquals(listOf(target), found)
  }
}
