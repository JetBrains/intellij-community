/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide.actions

import com.intellij.ide.IdeView
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaModule
import com.intellij.psi.PsiManager
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.MAIN
import org.assertj.core.api.Assertions.assertThat

class CreateModuleInfoActionTest : LightJava9ModulesCodeInsightFixtureTestCase() {
  fun test() {
    val dir = PsiManager.getInstance(project).findDirectory(MAIN.root())!!

    val ctx = MapDataContext()
    ctx.put(LangDataKeys.IDE_VIEW, object : IdeView {
      override fun selectElement(element: PsiElement?) = Unit
      override fun getDirectories() = arrayOf(dir)
      override fun getOrChooseDirectory() = throw UnsupportedOperationException()
    })
    ctx.put(LangDataKeys.MODULE, myModule)

    val event = AnActionEvent.createFromDataContext("", null, ctx)
    ActionManager.getInstance().getAction("NewModuleInfo")!!.actionPerformed(event)

    val file = dir.findFile(PsiJavaModule.MODULE_INFO_FILE)!!
    assertThat(file.text).isEqualTo("""
        /**
         * Created by ${'$'}{USER} on ${'$'}{DATE}.
         */
        module light_idea_test_case {
        }""".trimIndent())
  }
}