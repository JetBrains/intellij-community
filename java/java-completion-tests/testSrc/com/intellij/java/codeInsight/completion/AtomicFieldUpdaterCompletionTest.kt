// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.completion

import com.intellij.codeInsight.completion.lightCompletionFixture
import com.intellij.codeInsight.completion.lightCompletionProjectFixture
import com.intellij.openapi.application.EDT
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate
import com.intellij.testFramework.NeedsIndex
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@NeedsIndex.Full
@TestApplication
@IndexingModeTemplate
class AtomicFieldUpdaterCompletionTest {
  private val completion by lightCompletionFixture(projectFixture)
  private val myFixture get() = completion.fixture

  @BeforeEach
  fun setUp(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {

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

  @Test
  fun testUpdateableIntFirst(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    doTest("updateableInt", 0, "updateableIntFirst", "updateableIntSecond")
  }

  @Test
  fun testUpdateableIntSecond(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    doTest("updateableIntS", 0, "updateableIntSecond")
  }

  @Test
  fun testUpdateableLong(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    doTest("updateableL", 0, "updateableLong")
  }

  @Test
  fun testUpdateableArray(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    doTest("updateableA", 0, "updateableArray")
  }

  @Test
  fun testUpdateableString(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    doTest("updateableS", 0, "updateableString", "updateableIntSecond", "nonUpdateableStringFirst", "nonUpdateableStringSecond")
  }

  @Test
  fun testUpdateableAll(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    doTest("updateable", 3,
           "updateableArray", "updateableIntFirst", "updateableIntSecond", "updateableLong", "updateableObject",
           "updateableString", "nonUpdateableStringFirst", "nonUpdateableStringSecond", "nonUpdateableType")
  }

  @Test
  fun testAll(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    doTest("", 5, "other",
           "updateableArray", "updateableIntFirst", "updateableIntSecond", "updateableLong", "updateableObject",
           "updateableString", "nonUpdateableStringFirst", "nonUpdateableStringSecond", "nonUpdateableType")
  }

  @Test
  fun testUpdateableBase(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
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
    completion.complete()
    val items = completion.items
    if (items == null) {
      assertEquals(expectedNames.size, 1, "Only one item should auto-complete immediately")
      myFixture.checkResult(getMainClassText(expectedName, updater))
      return
    }
    val lookupStrings = items.map { it.lookupString }
    assertEquals(expectedNames.asList(), lookupStrings, "Lookup strings")
    if (index < 0) return

    val lookupElement = items[index]
    completion.selectItem(lookupElement!!)
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
    private val projectFixture = lightCompletionProjectFixture()
    private const val INTEGER = "AtomicIntegerFieldUpdater"
    private const val LONG = "AtomicLongFieldUpdater"
    private const val REFERENCE = "AtomicReferenceFieldUpdater"
  }
}