// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ide.actions

import com.intellij.ide.IdeView
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.M2
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.MAIN
import com.intellij.java.testFramework.fixtures.lightJava9ModulesFixture
import com.intellij.java.testFramework.fixtures.lightJava9ModulesProjectFixture
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.module.ModuleManager
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiJavaModule
import com.intellij.psi.PsiManager
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class CreateModuleInfoActionTest {
  companion object {
    private val projectFixture = lightJava9ModulesProjectFixture()
  }

  private val java9 by lightJava9ModulesFixture(projectFixture)
  private val myFixture get() = java9.fixture

  @Test
  fun test(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val testModule = ModuleManager.getInstance(myFixture.project).findModuleByName(M2.moduleName)!!
      val dir = PsiManager.getInstance(myFixture.project).findDirectory(java9.sourceRoot(M2))!!
      val ctx = MapDataContext(mapOf(LangDataKeys.IDE_VIEW to TestIdeView(dir), PlatformCoreDataKeys.MODULE to testModule))
      val event = AnActionEvent.createFromDataContext("", null, ctx)
      ActionManager.getInstance().getAction("NewModuleInfo")!!.actionPerformed(event)

      val file = dir.findFile(PsiJavaModule.MODULE_INFO_FILE)!!
      val p = FileTemplateManager.getInstance(myFixture.project).defaultProperties
      assertEquals("""
          /**
           * Created by ${p["USER"]} on ${p["DATE"]}.
           */
          module light.idea.test.m2 {
          }""".trimIndent(), file.text)
    }
  }

  @Test
  fun testNotValidName(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      myFixture.module.name
      val dir = PsiManager.getInstance(myFixture.project).findDirectory(java9.sourceRoot(MAIN))!!
      val ctx = MapDataContext(mapOf(LangDataKeys.IDE_VIEW to TestIdeView(dir), PlatformCoreDataKeys.MODULE to myFixture.module))
      val event = AnActionEvent.createFromDataContext("", null, ctx)
      ActionManager.getInstance().getAction("NewModuleInfo")!!.actionPerformed(event)

      val file = dir.findFile(PsiJavaModule.MODULE_INFO_FILE)!!
      val p = FileTemplateManager.getInstance(myFixture.project).defaultProperties
      assertEquals("""
          /**
           * Created by ${p["USER"]} on ${p["DATE"]}.
           */
          module module_name {
          }""".trimIndent(), file.text)
    }
  }

  private class TestIdeView(private val dir: PsiDirectory) : IdeView {
    override fun getDirectories() = arrayOf(dir)
    override fun getOrChooseDirectory() = throw UnsupportedOperationException()
  }

  @BeforeEach
  fun setUp() {
    PlatformTestUtil.setLongMeaninglessFileIncludeTemplateTemporarilyFor(myFixture.project, myFixture.testRootDisposable)
  }
}
