// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.navigation

import com.intellij.openapi.application.readAction
import com.intellij.psi.PsiMember
import kotlin.time.Duration.Companion.minutes
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.psi.PsiReference
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class AtomicFieldUpdaterNavigationTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUp() {

    myFixture.addClass("""package foo.bar;
class Base {
  public volatile Object base;
} """)

    myFixture.addClass("""package foo.bar;
class Data extends Base {
  public volatile int pvi;
  public volatile long pvl;
  public volatile String pvs;
  static byte sb;
} """)
  }

  private val INTEGER = "AtomicIntegerFieldUpdater"
  private val LONG = "AtomicLongFieldUpdater"
  private val REFERENCE = "AtomicReferenceFieldUpdater"

  @Test
  fun testIntField(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("pvi", INTEGER) }

  @Test
  fun testLongField(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("pvl", LONG) }

  @Test
  fun testStringField(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("pvs", REFERENCE) }

  @Test
  fun testStaticByteField(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("sb", REFERENCE) }

  @Test
  fun testBaseField(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("base", REFERENCE, false) }

  private suspend fun doTest(name: String, updater: String, shouldResolve: Boolean = true) {
    val mainClassText = getMainClassText(name, updater)
    myFixture.configureByText("Main.java", mainClassText)

    readAction {
      val reference = getReference()
      assertEquals(name, reference.canonicalText, "Reference text")
      val resolved = reference.resolve()
      if (shouldResolve) {
        assertNotNull(resolved, "Reference is not resolved: " + reference.canonicalText)
        assertTrue(resolved is PsiMember, "Target is a member")
        assertEquals(name, (resolved as PsiMember?)?.name, "Target name")
      }
      else {
        assertNull(resolved, "Reference should not be resolved")
      }
    }
  }

  private fun getMainClassText(name: String, updater: String): String {
    val data = if (updater === REFERENCE) "String.class," else ""

    return """import foo.bar.*;
import java.util.concurrent.atomic.*;

class Main {
  void foo() {
    $updater.newUpdater(Data.class, $data "<caret>$name").get();
  }
}"""
  }

  private fun getReference(): PsiReference {
    val offset = myFixture.caretOffset
    val reference = myFixture.file.findReferenceAt(offset)
    assertNotNull(reference, "No reference at the caret")
    return reference!!
  }
}