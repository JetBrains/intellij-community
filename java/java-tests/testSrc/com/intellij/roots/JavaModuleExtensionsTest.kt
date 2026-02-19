// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.roots

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.LanguageLevelUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.roots.CompilerProjectExtension
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.rules.ProjectModelRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import kotlin.io.path.invariantSeparatorsPathString

class JavaModuleExtensionsTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @get:Rule
  val projectModel = ProjectModelRule()

  private fun setLanguageLevel(module: Module, newLevel: LanguageLevel) {
    IdeaTestUtil.setModuleLanguageLevel(module, newLevel)
    IndexingTestUtil.waitUntilIndexesAreReady(projectModel.project)
  }

  private fun setLanguageLevel(project: Project, newLevel: LanguageLevel) {
    IdeaTestUtil.setProjectLanguageLevel(project, newLevel)
  }

  @Test
  fun `change custom language level`() {
    val module = projectModel.createModule()
    setLanguageLevel(module, LanguageLevel.JDK_1_8)
    assertThat(LanguageLevelUtil.getCustomLanguageLevel(module)).isEqualTo(LanguageLevel.JDK_1_8)

    val listener = MyLanguageLevelListener()
    listener.subscribe(projectModel.project)
    setLanguageLevel(module, LanguageLevel.JDK_11)
    assertThat(LanguageLevelUtil.getCustomLanguageLevel(module)).isEqualTo(LanguageLevel.JDK_11)
    listener.assertInvoked()
  }

  @Test
  fun `change project language level`() {
    val module = projectModel.createModule()
    runWriteActionAndWait {
      setLanguageLevel(projectModel.project, LanguageLevel.JDK_1_8)
      assertThat(LanguageLevelUtil.getCustomLanguageLevel(module)).isNull()
      assertThat(LanguageLevelUtil.getEffectiveLanguageLevel(module)).isEqualTo(LanguageLevel.JDK_1_8)
    }

    val listener = MyLanguageLevelListener()
    listener.subscribe(projectModel.project)
    runWriteActionAndWait {
      setLanguageLevel(projectModel.project, LanguageLevel.JDK_11)
      assertThat(LanguageLevelUtil.getEffectiveLanguageLevel(module)).isEqualTo(LanguageLevel.JDK_11)
    }
    listener.assertInvoked()
  }

  @Test
  fun `change module output`() {
    val module = projectModel.createModule("foo")
    val outputRoot = projectModel.baseProjectDir.rootPath.resolve("out")
    val outputRootUrl = VfsUtilCore.pathToUrl(outputRoot.invariantSeparatorsPathString)
    CompilerProjectExtension.getInstance(projectModel.project)!!.apply {
      runWriteActionAndWait {
        compilerOutputUrl = outputRootUrl
      }
    }
    assertThat(CompilerModuleExtension.getInstance(module)!!.compilerOutputUrl).isEqualTo(VfsUtilCore.pathToUrl(
      outputRoot.resolve("production/foo").invariantSeparatorsPathString))
    assertThat(CompilerModuleExtension.getInstance(module)!!.compilerOutputUrlForTests).isEqualTo(VfsUtilCore.pathToUrl(
      outputRoot.resolve("test/foo").invariantSeparatorsPathString))

    val customOutputUrl = VfsUtilCore.pathToUrl(outputRoot.resolve("custom").invariantSeparatorsPathString)
    ModuleRootModificationUtil.updateModel(module) {
      it.getModuleExtension(CompilerModuleExtension::class.java).setCompilerOutputPath(customOutputUrl)
    }
    assertThat(CompilerModuleExtension.getInstance(module)!!.compilerOutputUrlForTests).isEqualTo(VfsUtilCore.pathToUrl(
      outputRoot.resolve("test/foo").invariantSeparatorsPathString))

    ModuleRootModificationUtil.updateModel(module) {
      it.getModuleExtension(CompilerModuleExtension::class.java).inheritCompilerOutputPath(false)
    }
    assertThat(CompilerModuleExtension.getInstance(module)!!.compilerOutputUrl).isEqualTo(customOutputUrl)
  }

  @Test
  fun `change project output`() {
    val module = projectModel.createModule("foo")
    val outputRoot = projectModel.baseProjectDir.rootPath.resolve("out")
    val compilerProjectExtension = CompilerProjectExtension.getInstance(projectModel.project)!!
    val outputRootUrl = VfsUtilCore.pathToUrl(outputRoot.invariantSeparatorsPathString)
    runWriteActionAndWait {
      compilerProjectExtension.compilerOutputUrl = outputRootUrl
    }
    assertThat(CompilerModuleExtension.getInstance(module)!!.compilerOutputUrl).isEqualTo(VfsUtilCore.pathToUrl(
      outputRoot.resolve("production/foo").invariantSeparatorsPathString))

    val newOutputRoot = projectModel.baseProjectDir.rootPath.resolve("out")
    val newOutputUrl = VfsUtilCore.pathToUrl(newOutputRoot.invariantSeparatorsPathString)
    runWriteActionAndWait {
      compilerProjectExtension.compilerOutputUrl = newOutputUrl
    }
    assertThat(CompilerModuleExtension.getInstance(module)!!.compilerOutputUrl).isEqualTo(VfsUtilCore.pathToUrl(
      newOutputRoot.resolve("production/foo").invariantSeparatorsPathString))
  }

  private class MyLanguageLevelListener : LanguageLevelProjectExtension.LanguageLevelChangeListener {
    private var invoked = false

    override fun onLanguageLevelsChanged() {
      invoked = true
    }

    fun subscribe(project: Project) {
      project.messageBus.connect().subscribe(LanguageLevelProjectExtension.LANGUAGE_LEVEL_CHANGED_TOPIC, this)
    }

    fun assertInvoked() {
      assertThat(invoked).isTrue
      invoked = false
    }
  }
}