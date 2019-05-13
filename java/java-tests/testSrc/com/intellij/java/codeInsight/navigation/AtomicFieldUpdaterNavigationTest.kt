// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.navigation

import com.intellij.psi.PsiMember
import com.intellij.psi.PsiReference
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

class AtomicFieldUpdaterNavigationTest : LightCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()

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

  fun testIntField() = doTest("pvi", INTEGER)

  fun testLongField() = doTest("pvl", LONG)

  fun testStringField() = doTest("pvs", REFERENCE)

  fun testStaticByteField() = doTest("sb", REFERENCE)

  fun testBaseField() = doTest("base", REFERENCE, false)

  private fun doTest(name: String, updater: String, shouldResolve: Boolean = true) {
    val mainClassText = getMainClassText(name, updater)
    myFixture.configureByText("Main.java", mainClassText)

    val reference = getReference()
    assertEquals("Reference text", name, reference.canonicalText)
    val resolved = reference.resolve()
    if (shouldResolve) {
      assertNotNull("Reference is not resolved: " + reference.canonicalText, resolved)
      assertTrue("Target is a member", resolved is PsiMember)
      assertEquals("Target name", name, (resolved as PsiMember?)?.name)
    }
    else {
      assertNull("Reference should not be resolved", resolved)
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
    assertNotNull("No reference at the caret", reference)
    return reference!!
  }
}