// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ide.actions

import com.intellij.ide.IdeView
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.M2
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.MAIN
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.module.ModuleManager
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiJavaModule
import com.intellij.psi.PsiManager
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.PlatformTestUtil

class CreateModuleInfoActionTest : LightJava9ModulesCodeInsightFixtureTestCase() {
  fun test() {
    val testModule = ModuleManager.getInstance(project).findModuleByName(M2.moduleName)!!
    val dir = PsiManager.getInstance(project).findDirectory(M2.sourceRoot()!!)!!
    val ctx = MapDataContext(mapOf(LangDataKeys.IDE_VIEW to TestIdeView(dir), PlatformCoreDataKeys.MODULE to testModule))
    val event = AnActionEvent.createFromDataContext("", null, ctx)
    ActionManager.getInstance().getAction("NewModuleInfo")!!.actionPerformed(event)

    val file = dir.findFile(PsiJavaModule.MODULE_INFO_FILE)!!
    val p = FileTemplateManager.getInstance(project).defaultProperties
    assertEquals("""
        /**
         * Created by ${p["USER"]} on ${p["DATE"]}.
         */
        module light.idea.test.m2 {
        }""".trimIndent(), file.text)
  }

  fun testNotValidName() {
    module.name
    val dir = PsiManager.getInstance(project).findDirectory(MAIN.sourceRoot()!!)!!
    val ctx = MapDataContext(mapOf(LangDataKeys.IDE_VIEW to TestIdeView(dir), PlatformCoreDataKeys.MODULE to module))
    val event = AnActionEvent.createFromDataContext("", null, ctx)
    ActionManager.getInstance().getAction("NewModuleInfo")!!.actionPerformed(event)

    val file = dir.findFile(PsiJavaModule.MODULE_INFO_FILE)!!
    val p = FileTemplateManager.getInstance(project).defaultProperties
    assertEquals("""
        /**
         * Created by ${p["USER"]} on ${p["DATE"]}.
         */
        module module_name {
        }""".trimIndent(), file.text)
  }

  private class TestIdeView(private val dir: PsiDirectory) : IdeView {
    override fun getDirectories() = arrayOf(dir)
    override fun getOrChooseDirectory() = throw UnsupportedOperationException()
  }

  override fun setUp() {
    super.setUp()
    PlatformTestUtil.setLongMeaninglessFileIncludeTemplateTemporarilyFor(project, testRootDisposable)
  }
}
