// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.inlays

import com.intellij.codeInsight.hints.declarative.StringInlayActionPayload
import com.intellij.codeInsight.hints.findNavigationElement
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.javaModuleCodeInsightFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@TestApplication
class JavaFqnDeclarativeInlayActionHandlerTest {
  private val myFixture by javaModuleCodeInsightFixture(PathManager.getHomePath())
  private val project get() = myFixture.project
  private val module get() = myFixture.module

  @Test
  fun `test finds class within own module`(): Unit = timeoutRunBlocking {
    myFixture.addClass("""
      package pkg;
      public class MyClass {}
    """.trimIndent())

    val file = myFixture.configureByText("Test.java", """
      package test;
      class Test {}
    """.trimIndent())

    val result = readAction { findNavigationElement(project, file, StringInlayActionPayload("pkg.MyClass")) }
    assertNotNull(result, "Class within the same module should be found")
    assertEquals("pkg.MyClass", result!!.qualifiedName)
  }

  @Test
  fun `test does not find class from unrelated module`(): Unit = timeoutRunBlocking {
    val unrelatedDir = myFixture.tempDirFixture.findOrCreateDir("unrelated")
    PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "unrelated", unrelatedDir)

    myFixture.addFileToProject("unrelated/pkg/Unreachable.java", """
      package pkg;
      public class Unreachable {}
    """.trimIndent())

    val file = myFixture.configureByText("Test.java", """
      package test;
      class Test {}
    """.trimIndent())

    val result = readAction { findNavigationElement(project, file, StringInlayActionPayload("pkg.Unreachable")) }
    assertNull(result, "Class from an unrelated module (no dependency) should not be found")
  }

  @Test
  fun `test finds class from dependent module`(): Unit = timeoutRunBlocking {
    val depDir = myFixture.tempDirFixture.findOrCreateDir("dep")
    val depModule = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "dep", depDir)
    ModuleRootModificationUtil.addDependency(module, depModule)

    myFixture.addFileToProject("dep/pkg/Reachable.java", """
      package pkg;
      public class Reachable {}
    """.trimIndent())

    val file = myFixture.configureByText("Test.java", """
      package test;
      class Test {}
    """.trimIndent())

    val result = readAction { findNavigationElement(project, file, StringInlayActionPayload("pkg.Reachable")) }
    assertNotNull(result, "Class from a dependent module should be found")
    assertEquals("pkg.Reachable", result!!.qualifiedName)
  }
}
