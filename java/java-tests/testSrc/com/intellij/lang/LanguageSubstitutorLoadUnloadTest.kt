// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang

import com.intellij.ide.plugins.loadExtensionWithText
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.LanguageSubstitutor
import com.intellij.psi.PsiManager
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class LanguageSubstitutorLoadUnloadTest : LightJavaCodeInsightFixtureTestCase() {
  fun testBefore() {
    myFixture.configureByText("dummy.txt", "package hello;")
  }

  fun testLoadUnload() {
    val beforeLoading = myFixture.configureByText("dummy.txt", "package hello;")
    UsefulTestCase.assertInstanceOf(beforeLoading.language, PlainTextLanguage::class.java)

    val virtualFile = beforeLoading.virtualFile
    val text = "<lang.substitutor language=\"TEXT\" implementationClass=\"${TextToJavaSubstitutor::class.java.name}\"/>"
    val disposable = loadExtensionWithText(text, javaClass.classLoader)
    val psiManager = PsiManager.getInstance(myFixture.project)
    val afterLoading = psiManager.findFile(virtualFile)
    UsefulTestCase.assertInstanceOf(afterLoading!!.language, JavaLanguage::class.java)

    Disposer.dispose(disposable)
    val afterUnloading = psiManager.findFile(virtualFile)
    UsefulTestCase.assertInstanceOf(afterUnloading!!.language, PlainTextLanguage::class.java)
  }

  private class TextToJavaSubstitutor : LanguageSubstitutor() {
    override fun getLanguage(file: VirtualFile,
                             project: Project): Language? {
      return if (file.name.startsWith("dummy")) JavaLanguage.INSTANCE else null
    }
  }
}