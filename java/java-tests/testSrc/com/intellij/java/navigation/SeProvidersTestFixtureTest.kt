// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.navigation

import com.intellij.ide.SearchTopHitProvider
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.testFramework.SeProvidersTestFixture
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.function.Consumer
import kotlin.time.Duration.Companion.minutes

private val TIMEOUT = 1.minutes

/**
 * Tests [SeProvidersTestFixture] with the goto providers of Java code.
 */
@TestApplication
class SeProvidersTestFixtureTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  /** The providers run off the EDT, as in production. A test changes the PSI in a narrow EDT block. */

  @Test
  fun `test the session has the frontend and the backend providers`(): Unit = timeoutRunBlocking(TIMEOUT) {
    val fixture = SeProvidersTestFixture.create(myFixture.project, myFixture.testRootDisposable)

    // The actions provider comes from the frontend module, and the files provider comes from the backend module.
    for (providerId in listOf(SeProviderIdUtils.ACTIONS_ID, SeProviderIdUtils.CLASSES_ID, SeProviderIdUtils.FILES_ID, SeProviderIdUtils.SYMBOLS_ID)) {
      assertEquals(providerId, fixture.getProvider(providerId).id)
    }
    assertThrows(AssertionError::class.java) { fixture.getProvider("NoSuchProvider") }
  }

  @Test
  fun `test the separate tab uses the provider of the All tab instead of the adapted legacy contributor`(): Unit = timeoutRunBlocking(TIMEOUT) {
    val fixture = SeProvidersTestFixture.create(myFixture.project, myFixture.testRootDisposable)

    // The actions provider has no provider for the separate tab, and the legacy actions contributor is shown in a separate tab.
    assertSame(fixture.getProvider(SeProviderIdUtils.ACTIONS_ID, isAllTab = true),
               fixture.getProvider(SeProviderIdUtils.ACTIONS_ID, isAllTab = false))
  }

  @Test
  fun `test the top hit provider finds a top hit without a legacy contributor`(): Unit = timeoutRunBlocking(TIMEOUT) {
    val topHit = "SeFixtureTopHit"
    val topHitProvider = object : SearchTopHitProvider {
      override fun consumeTopHits(pattern: String, collector: Consumer<Any>, project: Project?) {
        if (pattern == "needle") collector.accept(topHit)
      }
    }
    ExtensionTestUtil.maskExtensions(SearchTopHitProvider.EP_NAME, listOf(topHitProvider), myFixture.testRootDisposable)
    val fixture = SeProvidersTestFixture.create(myFixture.project, myFixture.testRootDisposable)

    // No legacy contributor has the id of the top hits, so the session must create the provider from its own factory.
    assertEquals(listOf<Any>(topHit), fixture.findElements(SeProviderIdUtils.TOP_HIT_HOST_ID, "needle", isAllTab = true))
  }

  @Test
  fun `test the classes provider finds a project class`(): Unit = timeoutRunBlocking(TIMEOUT) {
    val target = withContext(Dispatchers.EDT) {
      myFixture.addClass("package foo; public class Unrelated {}")
      myFixture.addClass("package foo; public class SeFixtureTarget {}")
    }

    SeProvidersTestFixture.forEachGotoImplementation(myFixture.project) { fixture ->
      assertEquals(listOf(target), fixture.findElements(SeProviderIdUtils.CLASSES_ID, "SeFixtureTarget"))
    }
  }

  @Test
  fun `test the everywhere search also finds a library class`(): Unit = timeoutRunBlocking(TIMEOUT) {
    val stringBuffer = withContext(Dispatchers.EDT) { myFixture.findClass("java.lang.StringBuffer") }

    SeProvidersTestFixture.forEachGotoImplementation(myFixture.project) { fixture ->
      assertThat(fixture.findElements(SeProviderIdUtils.CLASSES_ID, "StringBuffer")).doesNotContain(stringBuffer)
      assertThat(fixture.findElements(SeProviderIdUtils.CLASSES_ID, "StringBuffer", isEverywhere = true)).contains(stringBuffer)
    }
  }

  @Test
  fun `test the symbols provider finds a method`(): Unit = timeoutRunBlocking(TIMEOUT) {
    val method = withContext(Dispatchers.EDT) {
      myFixture.addClass("package foo; public class Holder { public void seFixtureMethod() {} }").methods.single()
    }

    SeProvidersTestFixture.forEachGotoImplementation(myFixture.project) { fixture ->
      assertEquals(listOf(method), fixture.findElements(SeProviderIdUtils.SYMBOLS_ID, "seFixtureMethod"))
    }
  }

  @Test
  fun `test the blocking functions work on the EDT`(): Unit = timeoutRunBlocking(TIMEOUT) {
    val target = withContext(Dispatchers.EDT) { myFixture.addClass("package foo; public class SeFixtureTarget {}") }

    val found = withContext(Dispatchers.EDT) {
      SeProvidersTestFixture.createBlocking(myFixture.project, myFixture.testRootDisposable)
        .findElementsBlocking(SeProviderIdUtils.CLASSES_ID, "SeFixtureTarget")
    }
    assertEquals(listOf(target), found)
  }
}
