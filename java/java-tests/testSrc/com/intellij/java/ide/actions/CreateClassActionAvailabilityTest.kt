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
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadActionBlocking
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
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.javaModuleCodeInsightFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.annotations.NonNls
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.swing.Icon

@TestApplication
class CreateClassActionAvailabilityTest {
  private val myFixture by javaModuleCodeInsightFixture(PathManager.getHomePath())
  private val project get() = myFixture.project
  private val module get() = myFixture.module

  @Test
  fun testCreateClassActionAvailability(): Unit = timeoutRunBlocking {
    val srcRoot = myFixture.tempDirFixture.findOrCreateDir("newContentRoot/src")
    val contentRoot = srcRoot.parent
    edtWriteAction {
      ModuleRootManager.getInstance(myFixture.module).modifiableModel.apply {
        addContentEntry(contentRoot.url).addSourceFolder(srcRoot, false)
        commit()
      }
    }

    assertFalse(readAction { isEnabledAndVisibleFor(contentRoot) })
    assertTrue(readAction { isEnabledAndVisibleFor(srcRoot) })
  }

  @Test
  fun testCompactSourceClassAvailability(): Unit = timeoutRunBlocking {
    Registry.get("java.create.compact.source.file.separately").setValue(false, myFixture.testRootDisposable)
    val srcRoot = myFixture.tempDirFixture.findOrCreateDir("newContentRoot/src")
    val contentRoot = srcRoot.parent
    edtWriteAction {
      ModuleRootManager.getInstance(myFixture.module).modifiableModel.apply {
        addContentEntry(contentRoot.url).addSourceFolder(srcRoot, false)
        commit()
      }
    }
    val sourceLevel = myFixture.tempDirFixture.findOrCreateDir("")
    val sourceDirectory = readAction { PsiManager.getInstance(project).findDirectory(sourceLevel) }

    val dir = myFixture.tempDirFixture.findOrCreateDir("foo")
    val psiDirectory = readAction { PsiManager.getInstance(project).findDirectory(dir) }


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
      runReadActionBlocking { action.buildDialog(project, sourceDirectory!!, builder) }
      assertTrue(builder.templateNames.contains(JavaTemplateUtil.INTERNAL_SIMPLE_SOURCE_FILE))

      builder.templateNames.clear()
      runReadActionBlocking { action.buildDialog(project, psiDirectory!!, builder) }
      assertTrue(builder.templateNames.contains(JavaTemplateUtil.INTERNAL_SIMPLE_SOURCE_FILE))
    }

    IdeaTestUtil.withLevel(module, LanguageLevel.JDK_1_8) {
      builder.templateNames.clear()
      runReadActionBlocking { action.buildDialog(project, sourceDirectory!!, builder) }
      assertTrue(!builder.templateNames.contains(JavaTemplateUtil.INTERNAL_SIMPLE_SOURCE_FILE))
    }
  }

  @Test
  fun testCompactSourceClassAvailabilitySeparately(): Unit = timeoutRunBlocking {
    Registry.get("java.create.compact.source.file.separately").setValue(true, myFixture.testRootDisposable)
    val srcRoot = myFixture.tempDirFixture.findOrCreateDir("newContentRoot/src")
    val contentRoot = srcRoot.parent
    edtWriteAction {
      ModuleRootManager.getInstance(myFixture.module).modifiableModel.apply {
        addContentEntry(contentRoot.url).addSourceFolder(srcRoot, false)
        commit()
      }
    }
    val sourceLevel = myFixture.tempDirFixture.findOrCreateDir("")
    val sourceDirectory = readAction { PsiManager.getInstance(project).findDirectory(sourceLevel)!! }

    val dir = myFixture.tempDirFixture.findOrCreateDir("foo")
    val psiDirectory = readAction { PsiManager.getInstance(project).findDirectory(dir)!! }


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
      assertTrue(runReadActionBlocking { action.isAvailable(context) })
      assertFalse(runReadActionBlocking { action.isAvailable(contextWithoutRoot) })
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