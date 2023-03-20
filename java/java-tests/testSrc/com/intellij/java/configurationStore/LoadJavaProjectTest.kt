// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.configurationStore

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.LanguageLevelUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.LanguageLevelModuleExtensionImpl
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.loadProjectAndCheckResults
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Paths

class LoadJavaProjectTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @get:Rule
  val tempDirectory = TemporaryDirectory()

  @Test
  fun `load java project with custom language level`() = runBlocking {
    val projectPath = Paths.get(PathManagerEx.getCommunityHomePath()).resolve("java/java-tests/testData/configurationStore/java-module-with-custom-language-level")
    loadProjectAndCheckResults(listOf(projectPath), tempDirectory) { project ->
      val modules = ModuleManager.getInstance(project).modules.sortedBy { it.name }
      assertThat(modules).hasSize(2)
      val (bar, foo) = modules
      assertThat(foo.name).isEqualTo("foo")
      assertThat(bar.name).isEqualTo("bar")
      runReadAction {
        assertThat(LanguageLevelUtil.getEffectiveLanguageLevel(bar)).isEqualTo(LanguageLevel.JDK_11)
        assertThat(LanguageLevelUtil.getCustomLanguageLevel(bar)).isNull()
        assertThat(ModuleRootManager.getInstance(bar).getModuleExtension(LanguageLevelModuleExtensionImpl::class.java).languageLevel).isNull()
        assertThat(LanguageLevelUtil.getEffectiveLanguageLevel(foo)).isEqualTo(LanguageLevel.JDK_1_8)
        assertThat(LanguageLevelUtil.getCustomLanguageLevel(foo)).isEqualTo(LanguageLevel.JDK_1_8)
        assertThat(ModuleRootManager.getInstance(foo).getModuleExtension(LanguageLevelModuleExtensionImpl::class.java).languageLevel).isEqualTo(LanguageLevel.JDK_1_8)
      }
    }
  }
}