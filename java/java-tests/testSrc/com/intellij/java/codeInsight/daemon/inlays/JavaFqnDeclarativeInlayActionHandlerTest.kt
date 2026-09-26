// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.inlays

import com.intellij.codeInsight.hints.declarative.StringInlayActionPayload
import com.intellij.codeInsight.hints.findNavigationElement
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase

class JavaFqnDeclarativeInlayActionHandlerTest : JavaCodeInsightFixtureTestCase() {

  fun `test finds class within own module`() {
    myFixture.addClass("""
      package pkg;
      public class MyClass {}
    """.trimIndent())

    val file = myFixture.configureByText("Test.java", """
      package test;
      class Test {}
    """.trimIndent())

    val result = findNavigationElement(project, file, StringInlayActionPayload("pkg.MyClass"))
    assertNotNull("Class within the same module should be found", result)
    assertEquals("pkg.MyClass", result!!.qualifiedName)
  }

  fun `test does not find class from unrelated module`() {
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

    val result = findNavigationElement(project, file, StringInlayActionPayload("pkg.Unreachable"))
    assertNull("Class from an unrelated module (no dependency) should not be found", result)
  }

  fun `test finds class from dependent module`() {
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

    val result = findNavigationElement(project, file, StringInlayActionPayload("pkg.Reachable"))
    assertNotNull("Class from a dependent module should be found", result)
    assertEquals("pkg.Reachable", result!!.qualifiedName)
  }
}
