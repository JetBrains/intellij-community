// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion.commands

import com.intellij.codeInsight.completion.lightCompletionFixture
import com.intellij.codeInsight.completion.lightCompletionProjectFixture
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate
import com.intellij.testFramework.NeedsIndex
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@NeedsIndex.SmartMode(reason = "it requires highlighting")
@TestApplication
@IndexingModeTemplate
class JavaCommandsCompletionTypeHierarchyTest {
  companion object {
    private val projectFixture = lightCompletionProjectFixture()
  }

  private val completion by lightCompletionFixture(projectFixture)
  private val myFixture get() = completion.fixture

  @BeforeEach
  fun setUp() {
    Registry.get("ide.completion.command.enabled").setValue(false, myFixture.testRootDisposable)
    Registry.get("ide.completion.command.force.enabled").setValue(true, myFixture.testRootDisposable)
  }

  @Test
  fun testTypeHierarchyClassIdentifierCommand(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A.<caret> { 
        void foo() {
          int y = 10;
        } 
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertNotNull(elements.firstOrNull { element -> element.lookupString.contains("Type Hierarchy", ignoreCase = true) })
  }

  @Test
  fun testTypeHierarchyPrimitiveCommand(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
        void foo() {
          int.<caret> y = 10;
        } 
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertNull(elements.firstOrNull { element -> element.lookupString.contains("Type Hierarchy", ignoreCase = true) })
  }

  @Test
  fun testTypeHierarchyTypeElementCommand(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
        void foo() {
          Integer.<caret> y = 10;
        } 
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertNotNull(elements.firstOrNull { element -> element.lookupString.contains("Type Hierarchy", ignoreCase = true) })
  }
}