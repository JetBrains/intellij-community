// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang

import com.intellij.platform.testFramework.loadExtensionWithText
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.use
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.LanguageSubstitutor
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.assertj.core.api.Assertions.assertThat

class LanguageSubstitutorLoadUnloadTest : LightJavaCodeInsightFixtureTestCase() {
  fun testBefore() {
    myFixture.configureByText("dummy.txt", "package hello;")
  }

  fun testLoadUnload() {
    val beforeLoading = myFixture.configureByText("dummy.txt", "package hello;")
    assertThat(beforeLoading.language).isInstanceOf(PlainTextLanguage::class.java)

    val virtualFile = beforeLoading.virtualFile
    val text = "<lang.substitutor language=\"TEXT\" implementationClass=\"${TextToJavaSubstitutor::class.java.name}\"/>"
    loadExtensionWithText(text).use {
      val afterLoading = PsiManager.getInstance(myFixture.project).findFile(virtualFile)
      assertThat(afterLoading!!.language).isInstanceOf(JavaLanguage::class.java)
    }
    val afterUnloading = psiManager.findFile(virtualFile)
    assertThat(afterUnloading!!.language).isInstanceOf(PlainTextLanguage::class.java)
  }
}

private class TextToJavaSubstitutor : LanguageSubstitutor() {
  override fun getLanguage(file: VirtualFile, project: Project): Language? {
    return if (file.name.startsWith("dummy")) JavaLanguage.INSTANCE else null
  }
}