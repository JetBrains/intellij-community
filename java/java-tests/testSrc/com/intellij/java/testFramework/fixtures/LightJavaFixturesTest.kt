// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.testFramework.fixtures

import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.LanguageLevelModuleExtension
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_11
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_8
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.ProjectDescriptor
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder

@TestApplication
@TestMethodOrder(MethodOrderer.MethodName::class)
class LightJavaFixturesTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(JAVA_11)
    private var firstModule: Module? = null

    @AfterAll
    @JvmStatic
    fun forgetFirstModule() {
      firstModule = null
    }
  }

  private val lightProject by projectFixture
  private val myFixture by lightJavaCodeInsightFixture(projectFixture, { name -> descriptorFor(name) })

  private fun descriptorFor(testName: String): LightProjectDescriptor = if (testName == "3OtherDescriptor") JAVA_8 else JAVA_11

  @Test
  fun test1ChangeModuleAndAddFile(): Unit = timeoutRunBlocking {
    assertThat(myFixture.getTestName(false)).isEqualTo("1ChangeModuleAndAddFile")
    val module = myFixture.module
    firstModule = module
    assertThat(readAction { moduleLevel(module) }).isEqualTo(LanguageLevel.JDK_11)
    assertThat(LanguageLevelProjectExtension.getInstance(myFixture.project).languageLevel).isEqualTo(LanguageLevel.JDK_1_6)
    val file = myFixture.addClass("public class Foo {}").containingFile.virtualFile
    assertThat(file.name).isEqualTo("Foo.java")
    assertThat(file.parent).isEqualTo(lightProject.sourceRoot)
    IdeaTestUtil.setModuleLanguageLevel(module, LanguageLevel.JDK_1_8)
    IdeaTestUtil.setProjectLanguageLevel(myFixture.project, LanguageLevel.JDK_1_8)
  }

  @Test
  fun test2SeesCleanState(): Unit = timeoutRunBlocking {
    val module = myFixture.module
    assertThat(module).isNotSameAs(firstModule)
    assertThat(module.name).isEqualTo(LightProjectDescriptor.TEST_MODULE_NAME)
    assertThat(readAction { moduleLevel(module) }).isEqualTo(LanguageLevel.JDK_11)
    assertThat(LanguageLevelProjectExtension.getInstance(myFixture.project).languageLevel).isEqualTo(LanguageLevel.JDK_1_6)
    assertThat(readAction { lightProject.sourceRoot.children.toList() }).isEmpty()
    firstModule = module
  }

  @Test
  fun test3OtherDescriptor(): Unit = timeoutRunBlocking {
    val module = myFixture.module
    assertThat(module).isNotSameAs(firstModule)
    assertThat(readAction { moduleLevel(module) }).isEqualTo(LanguageLevel.JDK_1_8)
    assertThat(readAction { moduleSdkName(module) }).isEqualTo((JAVA_8 as ProjectDescriptor).sdkName)
    assertThat(ProjectRootManager.getInstance(myFixture.project).projectSdk).isNull()
  }

  @Test
  fun test4ClassDescriptorAgain(): Unit = timeoutRunBlocking {
    val module = myFixture.module
    assertThat(readAction { moduleLevel(module) }).isEqualTo(LanguageLevel.JDK_11)
    assertThat(readAction { moduleSdkName(module) }).isEqualTo((JAVA_11 as ProjectDescriptor).sdkName)
  }

  private fun moduleLevel(module: Module): LanguageLevel? =
    ModuleRootManager.getInstance(module).getModuleExtension(LanguageLevelModuleExtension::class.java)?.languageLevel

  private fun moduleSdkName(module: Module): String? = ModuleRootManager.getInstance(module).sdk?.name
}
