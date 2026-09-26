// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion.commands

import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.codeInsight.completion.command.CommandCompletionLookupElement
import com.intellij.codeInsight.completion.command.configuration.CommandCompletionSettingsService
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.impl.isSeparator
import com.intellij.codeInsight.template.postfix.completion.PostfixTemplateLookupElement
import com.intellij.codeInsight.template.postfix.settings.PostfixTemplatesSettings
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.NeedsIndex

@NeedsIndex.SmartMode(reason = "it requires highlighting")
class JavaCommandsCompletionOrderTest : LightFixtureCompletionTestCase() {

  override fun setUp() {
    super.setUp()
    Registry.get("ide.completion.command.enabled").setValue(false, getTestRootDisposable())
    Registry.get("ide.completion.group.mode.enabled").setValue(true, getTestRootDisposable())
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())

    val previousShowAsSeparateGroup = PostfixTemplatesSettings.getInstance().isShowAsSeparateGroup
    val completionSettingsService = CommandCompletionSettingsService.getInstance()
    val previousGroupEnabled = completionSettingsService.groupEnabled()

    PostfixTemplatesSettings.getInstance().isShowAsSeparateGroup = false
    completionSettingsService.groupEnabled(false)
    testRootDisposable.whenDisposed {
      PostfixTemplatesSettings.getInstance().isShowAsSeparateGroup = previousShowAsSeparateGroup
      completionSettingsService.groupEnabled(previousGroupEnabled)
    }
  }

  fun testGroupNoSeparator() {
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

  fun testCommandCompletionGroupSeparator() {
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

  fun testPostfixGroupSeparator() {
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

  fun testCommandAndPostfixGroupSeparator() {
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