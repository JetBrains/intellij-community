// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion.commands

import com.intellij.codeInsight.completion.command.CommandCompletionLookupElement
import com.intellij.codeInsight.completion.command.configuration.CommandCompletionSettingsService
import com.intellij.codeInsight.completion.lightCompletionFixture
import com.intellij.codeInsight.completion.lightCompletionProjectFixture
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.impl.isSeparator
import com.intellij.codeInsight.template.postfix.completion.PostfixTemplateLookupElement
import com.intellij.codeInsight.template.postfix.settings.PostfixTemplatesSettings
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate
import com.intellij.testFramework.NeedsIndex
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

@NeedsIndex.SmartMode(reason = "it requires highlighting")
@TestApplication
@IndexingModeTemplate
class JavaCommandsCompletionOrderTest {
  companion object {
    private val projectFixture = lightCompletionProjectFixture()
  }

  private val completion by lightCompletionFixture(projectFixture)
  private val myFixture get() = completion.fixture

  @BeforeEach
  fun setUp() {
    Registry.get("ide.completion.command.enabled").setValue(false, myFixture.testRootDisposable)
    Registry.get("ide.completion.group.mode.enabled").setValue(true, myFixture.testRootDisposable)
    Registry.get("ide.completion.command.force.enabled").setValue(true, myFixture.testRootDisposable)

    val previousShowAsSeparateGroup = PostfixTemplatesSettings.getInstance().isShowAsSeparateGroup
    val completionSettingsService = CommandCompletionSettingsService.getInstance()
    val previousGroupEnabled = completionSettingsService.groupEnabled()

    PostfixTemplatesSettings.getInstance().isShowAsSeparateGroup = false
    completionSettingsService.groupEnabled(false)
    myFixture.testRootDisposable.whenDisposed {
      PostfixTemplatesSettings.getInstance().isShowAsSeparateGroup = previousShowAsSeparateGroup
      completionSettingsService.groupEnabled(previousGroupEnabled)
    }
  }

  @Test
  fun testGroupNoSeparator(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
        void foo() {
          Integer a = 1;
          a.<caret>
        } 
      }
      """.trimIndent())
    myFixture.doHighlighting()
    val elements = myFixture.completeBasic()
    checkSeparators(elements, hashSetOf())
  }

  @Test
  fun testCommandCompletionGroupSeparator(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val completionSettingsService = CommandCompletionSettingsService.getInstance()
    completionSettingsService.groupEnabled(true)
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
        void foo() {
          Integer a = 1;
          a.<caret>
        } 
      }
      """.trimIndent())
    myFixture.doHighlighting()
    val elements = myFixture.completeBasic()
    checkSeparators(elements, hashSetOf(CommandCompletionLookupElement::class.java))
  }

  @Test
  fun testPostfixGroupSeparator(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    PostfixTemplatesSettings.getInstance().isShowAsSeparateGroup = true
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
        void foo() {
          Integer a = 1;
          a.<caret>
        } 
      }
      """.trimIndent())
    myFixture.doHighlighting()
    val elements = myFixture.completeBasic()
    checkSeparators(elements, hashSetOf(PostfixTemplateLookupElement::class.java))
  }

  @Test
  fun testCommandAndPostfixGroupSeparator(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    PostfixTemplatesSettings.getInstance().isShowAsSeparateGroup = true
    val completionSettingsService = CommandCompletionSettingsService.getInstance()
    completionSettingsService.groupEnabled(true)

    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
        void foo() {
          Integer a = 1;
          a.<caret>
        } 
      }
      """.trimIndent())
    myFixture.doHighlighting()
    val elements = myFixture.completeBasic()
    checkSeparators(elements, hashSetOf(PostfixTemplateLookupElement::class.java, CommandCompletionLookupElement::class.java))
  }

  private fun checkSeparators(elements: Array<LookupElement>, expectedClasses: HashSet<Class<*>>) {
    val separatorCounts = 1.coerceAtMost(expectedClasses.size) //at least now, in the future, we can have more than one separator
    val actualSeparatorCounts = elements.count { element -> element.isSeparator() }
    assertEquals(separatorCounts, actualSeparatorCounts)
    if (separatorCounts == 0) return
    var previousExpectedClass: Class<*>? = null
    for ((index, element) in elements.withIndex()) {
      if (element.isSeparator()) {
        val nextElement = elements[index + 1]
        val clazz = findClass(nextElement, expectedClasses)
        if (clazz == null) {
          fail("No class found for ${nextElement.lookupString}")
        }
        previousExpectedClass = clazz
        continue
      }

      val clazz = findClass(element, expectedClasses)

      if (previousExpectedClass == null) {
        if (clazz != null) {
          fail("No separator found before ${element.lookupString}")
        }
        continue
      }
    }
  }

  private fun findClass(nextElement: LookupElement, expectedClasses: HashSet<Class<*>>): Class<*>? {
    for (klass in expectedClasses) {
      val currentClass = nextElement.`as`(klass)
      if (currentClass != null) return klass
    }
    return null
  }
}