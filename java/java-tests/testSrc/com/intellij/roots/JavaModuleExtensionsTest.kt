// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.roots

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.LanguageLevelUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.roots.CompilerProjectExtension
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.util.io.systemIndependentPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class JavaModuleExtensionsTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @get:Rule
  val projectModel = ProjectModelRule()

  @Test
  fun `change custom language level`() {
    val module = projectModel.createModule()
    IdeaTestUtil.setModuleLanguageLevel(module, LanguageLevel.JDK_1_8)
    assertThat(LanguageLevelUtil.getCustomLanguageLevel(module)).isEqualTo(LanguageLevel.JDK_1_8)

    val listener = MyLanguageLevelListener()
    listener.subscribe(projectModel.project)
    IdeaTestUtil.setModuleLanguageLevel(module, LanguageLevel.JDK_11)
    assertThat(LanguageLevelUtil.getCustomLanguageLevel(module)).isEqualTo(LanguageLevel.JDK_11)
    listener.assertInvoked()
  }

  @Test
  fun `change project language level`() {
    val module = projectModel.createModule()
    runWriteActionAndWait {
      LanguageLevelProjectExtension.getInstance(projectModel.project).languageLevel = LanguageLevel.JDK_1_8
      assertThat(LanguageLevelUtil.getCustomLanguageLevel(module)).isNull()
      assertThat(LanguageLevelUtil.getEffectiveLanguageLevel(module)).isEqualTo(LanguageLevel.JDK_1_8)
    }

    val listener = MyLanguageLevelListener()
    listener.subscribe(projectModel.project)
    runWriteActionAndWait {
      LanguageLevelProjectExtension.getInstance(projectModel.project).languageLevel = LanguageLevel.JDK_11
      assertThat(LanguageLevelUtil.getEffectiveLanguageLevel(module)).isEqualTo(LanguageLevel.JDK_11)
    }
    listener.assertInvoked()
  }

  @Test
  fun `change module output`() {
    val module = projectModel.createModule("foo")
    val outputRoot = projectModel.baseProjectDir.rootPath.resolve("out")
    CompilerProjectExtension.getInstance(projectModel.project)!!.compilerOutputUrl = VfsUtilCore.pathToUrl(outputRoot.systemIndependentPath)
    assertThat(CompilerModuleExtension.getInstance(module)!!.compilerOutputUrl).isEqualTo(VfsUtilCore.pathToUrl(outputRoot.resolve("production/foo").systemIndependentPath))
    assertThat(CompilerModuleExtension.getInstance(module)!!.compilerOutputUrlForTests).isEqualTo(VfsUtilCore.pathToUrl(outputRoot.resolve("test/foo").systemIndependentPath))

    val customOutputUrl = VfsUtilCore.pathToUrl(outputRoot.resolve("custom").systemIndependentPath)
    ModuleRootModificationUtil.updateModel(module) {
      it.getModuleExtension(CompilerModuleExtension::class.java).setCompilerOutputPath(customOutputUrl)
    }
    assertThat(CompilerModuleExtension.getInstance(module)!!.compilerOutputUrlForTests).isEqualTo(VfsUtilCore.pathToUrl(outputRoot.resolve("test/foo").systemIndependentPath))

    ModuleRootModificationUtil.updateModel(module) {
      it.getModuleExtension(CompilerModuleExtension::class.java).inheritCompilerOutputPath(false)
    }
    assertThat(CompilerModuleExtension.getInstance(module)!!.compilerOutputUrl).isEqualTo(customOutputUrl)
  }

  @Test
  fun `change project output`() {
    val module = projectModel.createModule("foo")
    val outputRoot = projectModel.baseProjectDir.rootPath.resolve("out")
    CompilerProjectExtension.getInstance(projectModel.project)!!.compilerOutputUrl = VfsUtilCore.pathToUrl(outputRoot.systemIndependentPath)
    assertThat(CompilerModuleExtension.getInstance(module)!!.compilerOutputUrl).isEqualTo(VfsUtilCore.pathToUrl(outputRoot.resolve("production/foo").systemIndependentPath))

    val newOutputRoot = projectModel.baseProjectDir.rootPath.resolve("out")
    val newOutputUrl = VfsUtilCore.pathToUrl(newOutputRoot.systemIndependentPath)
    CompilerProjectExtension.getInstance(projectModel.project)!!.compilerOutputUrl = newOutputUrl
    assertThat(CompilerModuleExtension.getInstance(module)!!.compilerOutputUrl).isEqualTo(VfsUtilCore.pathToUrl(newOutputRoot.resolve("production/foo").systemIndependentPath))
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