// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.completion

import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase

class AtomicFieldUpdaterCompletionTest : LightFixtureCompletionTestCase() {
  override fun setUp() {
    super.setUp()

    myFixture.addClass("""package foo.bar;
class Base{
  public volatile int base;
}""")

    myFixture.addClass("""package foo.bar;
class Data extends Base {
  public volatile int updateableIntFirst;
  public volatile int updateableIntSecond;
  public volatile long updateableLong;
  public volatile int[] updateableArray;
  public volatile Object updateableObject;
  public volatile String updateableString;
  public String nonUpdateableStringFirst;
  @SuppressWarnings("StaticNonFinalField")
  public static volatile String nonUpdateableStringSecond;
  public volatile byte nonUpdateableType;
  public volatile Runnable other;
}""")
  }

  fun testUpdateableIntFirst() {
    doTest("updateableInt", 0, "updateableIntFirst", "updateableIntSecond")
  }

  fun testUpdateableIntSecond() {
    doTest("updateableIntS", 0, "updateableIntSecond")
  }

  fun testUpdateableLong() {
    doTest("updateableL", 0, "updateableLong")
  }

  fun testUpdateableArray() {
    doTest("updateableA", 0, "updateableArray")
  }

  fun testUpdateableString() {
    doTest("updateableS", 0, "updateableString", "updateableIntSecond", "nonUpdateableStringFirst", "nonUpdateableStringSecond")
  }

  fun testUpdateableAll() {
    doTest("updateable", 3,
           "updateableArray", "updateableIntFirst", "updateableIntSecond", "updateableLong", "updateableObject",
           "updateableString", "nonUpdateableStringFirst", "nonUpdateableType", "nonUpdateableStringSecond")
  }

  fun testAll() {
    doTest("", 5, "other",
           "updateableArray", "updateableIntFirst", "updateableIntSecond", "updateableLong", "updateableObject",
           "updateableString", "nonUpdateableStringFirst", "nonUpdateableType", "nonUpdateableStringSecond")
  }

  fun testUpdateableBase() {
    doTest("base", -1)
  }

  private fun doTest(prefix: String, index: Int, vararg expectedNames: String) {
    val expectedName = if (expectedNames.isNotEmpty()) expectedNames[index] else ""
    val updater = when {
      expectedName.contains("Int") -> INTEGER
      expectedName.contains("Long") -> LONG
      else -> REFERENCE
    }
    myFixture.configureByText("Main.java", getMainClassText(prefix, updater))
    complete()
    val items = myItems
    if (items == null) {
      assertEquals("Only one item should auto-complete immediately", expectedNames.size, 1)
      myFixture.checkResult(getMainClassText(expectedName, updater))
      return
    }
    val lookupStrings = items.map { it.lookupString }
    assertEquals("Lookup strings", expectedNames.asList(), lookupStrings)
    if (index < 0) return

    val lookupElement = items[index]
    selectItem(lookupElement!!)
    myFixture.checkResult(getMainClassText(expectedName, updater))
  }

  private fun getMainClassText(name: String, updater: String): String {
    val data = if (updater === REFERENCE) "String.class," else ""

    return """import foo.bar.*;
import java.util.concurrent.atomic.*;

class Main {
  void foo() {
    $updater.newUpdater(Data.class, $data "$name<caret>").get();
  }
}"""
  }

  companion object {
    private val INTEGER = "AtomicIntegerFieldUpdater"
    private val LONG = "AtomicLongFieldUpdater"
    private val REFERENCE = "AtomicReferenceFieldUpdater"
  }
}