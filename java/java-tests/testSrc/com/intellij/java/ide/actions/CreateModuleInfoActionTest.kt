/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.ide.actions

import com.intellij.ide.IdeView
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiJavaModule
import com.intellij.psi.PsiManager
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.MAIN

class CreateModuleInfoActionTest : LightJava9ModulesCodeInsightFixtureTestCase() {
  fun test() {
    val dir = PsiManager.getInstance(project).findDirectory(MAIN.root())!!
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