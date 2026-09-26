// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ide.actions

import com.intellij.ide.IdeView
import com.intellij.ide.actions.CreateClassAction
import com.intellij.ide.actions.CreateCompactSourceFileAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.ide.actions.TestDialogBuilder
import com.intellij.ide.fileTemplates.JavaTemplateUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.java.JavaFeature
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

class CreateClassActionAvailabilityTest : JavaCodeInsightFixtureTestCase() {

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

  fun testCompactSourceClassAvailability() {
    Registry.get("java.create.compact.source.file.separately").setValue(false, testRootDisposable)
    val srcRoot = myFixture.tempDirFixture.findOrCreateDir("newContentRoot/src")
    val contentRoot = srcRoot.parent
    ApplicationManager.getApplication().runWriteAction {
      ModuleRootManager.getInstance(myFixture.module).modifiableModel.apply {
        addContentEntry(contentRoot.url).addSourceFolder(srcRoot, false)
        commit()
      }
    }
    val sourceLevel = myFixture.tempDirFixture.findOrCreateDir("")
    val sourceDirectory = PsiManager.getInstance(project).findDirectory(sourceLevel)

    val dir = myFixture.tempDirFixture.findOrCreateDir("foo")
    val psiDirectory = PsiManager.getInstance(project).findDirectory(dir)


    val builder = object : CreateFileFromTemplateDialog.Builder by TestDialogBuilder(TestDialogBuilder.TestAnswers(null, null)) {
      val templateNames = hashSetOf<String>()
      override fun addKind(kind: @NlsContexts.ListItem String, icon: Icon?, templateName: @NonNls String): CreateFileFromTemplateDialog.Builder? {
        templateNames.add(templateName)
        return super.addKind(kind, icon, templateName)
      }
    }

    val action = object : CreateClassAction() {
      public override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
        super.buildDialog(project, directory, builder)
      }
    }

    IdeaTestUtil.withLevel(module, JavaFeature.IMPLICIT_CLASSES.minimumLevel) {

      builder.templateNames.clear()
      action.buildDialog(project, sourceDirectory!!, builder)
      assertTrue(builder.templateNames.contains(JavaTemplateUtil.INTERNAL_SIMPLE_SOURCE_FILE))

      builder.templateNames.clear()
      action.buildDialog(project, psiDirectory!!, builder)
      assertTrue(builder.templateNames.contains(JavaTemplateUtil.INTERNAL_SIMPLE_SOURCE_FILE))
    }

    IdeaTestUtil.withLevel(module, LanguageLevel.JDK_1_8) {
      builder.templateNames.clear()
      action.buildDialog(project, sourceDirectory!!, builder)
      assertTrue(!builder.templateNames.contains(JavaTemplateUtil.INTERNAL_SIMPLE_SOURCE_FILE))
    }
  }

  fun testCompactSourceClassAvailabilitySeparately() {
    Registry.get("java.create.compact.source.file.separately").setValue(true, testRootDisposable)
    val srcRoot = myFixture.tempDirFixture.findOrCreateDir("newContentRoot/src")
    val contentRoot = srcRoot.parent
    ApplicationManager.getApplication().runWriteAction {
      ModuleRootManager.getInstance(myFixture.module).modifiableModel.apply {
        addContentEntry(contentRoot.url).addSourceFolder(srcRoot, false)
        commit()
      }
    }
    val sourceLevel = myFixture.tempDirFixture.findOrCreateDir("")
    val sourceDirectory = PsiManager.getInstance(project).findDirectory(sourceLevel)!!

    val dir = myFixture.tempDirFixture.findOrCreateDir("foo")
    val psiDirectory = PsiManager.getInstance(project).findDirectory(dir)!!


    val builder = object : CreateFileFromTemplateDialog.Builder by TestDialogBuilder(TestDialogBuilder.TestAnswers(null, null)) {
      val templateNames = hashSetOf<String>()
      override fun addKind(kind: @NlsContexts.ListItem String, icon: Icon?, templateName: @NonNls String): CreateFileFromTemplateDialog.Builder? {
        templateNames.add(templateName)
        return super.addKind(kind, icon, templateName)
      }
    }

    val action = object : CreateCompactSourceFileAction() {
      public override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
        super.buildDialog(project, directory, builder)
      }
    }

    IdeaTestUtil.withLevel(module, JavaFeature.IMPLICIT_CLASSES.minimumLevel) {

      builder.templateNames.clear()
      val context = SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, project)
        .add(LangDataKeys.IDE_VIEW, object : IdeView {
          override fun getDirectories(): Array<out PsiDirectory> {
            return arrayOf(sourceDirectory)
          }

          override fun getOrChooseDirectory(): PsiDirectory {
            throw UnsupportedOperationException()
          }
        })
        .build()
      val contextWithoutRoot = SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, project)
        .add(LangDataKeys.IDE_VIEW, object : IdeView {
          override fun getDirectories(): Array<out PsiDirectory> {
            return arrayOf(psiDirectory)
          }

          override fun getOrChooseDirectory(): PsiDirectory {
            throw UnsupportedOperationException()
          }
        })
        .build()
      assertTrue(action.isAvailable(context))
      assertFalse(action.isAvailable(contextWithoutRoot))
    }
  }

  private fun isEnabledAndVisibleFor(baseDir: VirtualFile): Boolean {
    val projectDir = PsiManager.getInstance(project).findDirectory(baseDir)!!
    val action = CreateClassAction()
    val e: AnActionEvent = TestActionEvent.createTestEvent(context(projectDir))
    action.update(e)
    return e.presentation.isEnabledAndVisible
  }

  private fun context(projectDir: PsiDirectory): DataContext {
    return SimpleDataContext.builder().add(LangDataKeys.IDE_VIEW, object : IdeView {
      override fun getDirectories(): Array<out PsiDirectory> = arrayOf(projectDir)
      override fun getOrChooseDirectory(): PsiDirectory = projectDir
    }).add(LangDataKeys.PROJECT, this.project).build()
  }
}