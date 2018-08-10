// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ide.actions

import com.intellij.ide.IdeView
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.MAIN
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiJavaModule
import com.intellij.psi.PsiManager
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.PlatformTestUtil

class CreateModuleInfoActionTest : LightJava9ModulesCodeInsightFixtureTestCase() {
  fun test() {
    val dir = PsiManager.getInstance(project).findDirectory(MAIN.root()!!)!!
    val ctx = MapDataContext(mapOf(LangDataKeys.IDE_VIEW to TestIdeView(dir), LangDataKeys.MODULE to myModule))
    val event = AnActionEvent.createFromDataContext("", null, ctx)
    ActionManager.getInstance().getAction("NewModuleInfo")!!.actionPerformed(event)

    val file = dir.findFile(PsiJavaModule.MODULE_INFO_FILE)!!
    val p = FileTemplateManager.getInstance(project).defaultProperties
    assertEquals("""
        /**
         * Created by ${p["USER"]} on ${p["DATE"]}.
         */
        module light.idea.test.case {
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