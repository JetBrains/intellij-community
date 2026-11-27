// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.resolve

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture.Companion.createChildData
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture.Companion.createChildDirectory
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture.Companion.heavyJavaCodeInsightFixture
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture.Companion.setFileText
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.readAction
import com.intellij.psi.PsiJavaReference
import com.intellij.psi.impl.PsiFileEx
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.utils.vfs.getPsiFile
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@TestApplication
class ResolveExcludedClassTest {
  private val fixture by heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath())

  @Test
  fun testResolveExcludedClass(): Unit = timeoutRunBlocking {
    val moduleDir = fixture.createTempVirtualDir()
    val srcDir = createChildDirectory(moduleDir, "src")
    val testPackage = createChildDirectory(srcDir, "test")
    val testClass = createChildData(testPackage, "Test.java")
    setFileText(testClass, """
      package test;

      public class Test {
        Excluded excluded;
      }
    """.trimIndent())
    val excludedClass = createChildData(testPackage, "Excluded.java")
    setFileText(excludedClass, """
      package test;

      public class Excluded {
      }
    """.trimIndent())
    PsiTestUtil.addContentRoot(fixture.module, moduleDir)
    PsiTestUtil.addSourceRoot(fixture.module, srcDir)
    PsiTestUtil.addExcludedRoot(fixture.module, excludedClass)

    readAction {
      val psiFile = testClass.getPsiFile(fixture.project)
      // Needed for the bug in IDEA-358871 to reproduce in test
      psiFile.getViewProvider().getPsi(JavaLanguage.INSTANCE)!!.putUserData(PsiFileEx.BATCH_REFERENCE_PROCESSING, true)

      val offset = psiFile.text.indexOf("Excluded excluded;")
      val ref = checkNotNull(psiFile.findReferenceAt(offset))
      val target = (ref as PsiJavaReference).advancedResolve(true)
      assertNull(target.element)
    }
  }
}
