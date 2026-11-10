// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.lang

import com.intellij.lang.Language
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.use
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.testFramework.loadExtensionWithText
import com.intellij.psi.LanguageSubstitutor
import com.intellij.psi.PsiManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

@TestApplication
class LanguageSubstitutorLoadUnloadTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun testBefore() {
    myFixture.configureByText("dummy.txt", "package hello;")
  }

  @Test
  fun testLoadUnload(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val beforeLoading = myFixture.configureByText("dummy.txt", "package hello;")
    assertThat(beforeLoading.language).isInstanceOf(PlainTextLanguage::class.java)

    val virtualFile = beforeLoading.virtualFile
    val text = "<lang.substitutor language=\"TEXT\" implementationClass=\"${TextToJavaSubstitutor::class.java.name}\"/>"
    withContext(Dispatchers.EDT) {
      loadExtensionWithText(text).use {
        val afterLoading = PsiManager.getInstance(myFixture.project).findFile(virtualFile)
        assertThat(afterLoading!!.language).isInstanceOf(JavaLanguage::class.java)
      }
    }
    val afterUnloading = readAction { PsiManager.getInstance(myFixture.project).findFile(virtualFile) }
    assertThat(afterUnloading!!.language).isInstanceOf(PlainTextLanguage::class.java)
  }
}

private class TextToJavaSubstitutor : LanguageSubstitutor() {
  override fun getLanguage(file: VirtualFile, project: Project): Language? {
    return if (file.name.startsWith("dummy")) JavaLanguage.INSTANCE else null
  }
}