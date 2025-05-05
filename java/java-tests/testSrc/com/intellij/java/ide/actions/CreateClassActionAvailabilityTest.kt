// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ide.actions

import com.intellij.ide.IdeView
import com.intellij.ide.actions.CreateClassAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil.updateAction
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase

class CreateClassActionAvailabilityTest: JavaCodeInsightFixtureTestCase() {

  fun testCreateClassActionAvailability() {
    val srcRoot = myFixture.tempDirFixture.findOrCreateDir("newContentRoot/src")
    val contentRoot = srcRoot.parent
    ApplicationManager.getApplication().runWriteAction {
      ModuleRootManager.getInstance(myFixture.module).modifiableModel.apply {
        addContentEntry(contentRoot.url).addSourceFolder(srcRoot, false)
        commit()
      }
    }

    assertFalse(isEnabledAndVisibleFor(contentRoot))
    assertTrue(isEnabledAndVisibleFor(srcRoot))
  }

  private fun isEnabledAndVisibleFor(baseDir: VirtualFile): Boolean {
    val projectDir = PsiManager.getInstance(project).findDirectory(baseDir)!!
    val action = CreateClassAction()
    val e: AnActionEvent = TestActionEvent.createTestEvent(context(projectDir))
    updateAction(action, e)
    val enabledAndVisible = e.presentation.isEnabledAndVisible
    return enabledAndVisible
  }

  private fun context(projectDir: PsiDirectory): DataContext {
    return SimpleDataContext.builder().add(LangDataKeys.IDE_VIEW, object : IdeView {
      override fun getDirectories(): Array<out PsiDirectory> = arrayOf(projectDir)
      override fun getOrChooseDirectory(): PsiDirectory? = projectDir
    }).add(LangDataKeys.PROJECT, this.project).build()
  }
}