// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.resolve

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiJavaReference
import com.intellij.psi.impl.PsiFileEx
import com.intellij.testFramework.JavaProjectTestCase
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.utils.vfs.getPsiFile

class ResolveExcludedClassTest : JavaProjectTestCase() {
  fun testResolveExcludedClass() {
    val tempDir = createTempDirectory()
    val moduleDir = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDir))
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
    PsiTestUtil.addContentRoot(myModule, moduleDir)
    PsiTestUtil.addSourceRoot(myModule, srcDir)
    PsiTestUtil.addExcludedRoot(myModule, excludedClass)

    val psiFile = testClass.getPsiFile(project)
    // Needed for the bug in IDEA-358871 to reproduce in test
    psiFile.getViewProvider().getPsi(JavaLanguage.INSTANCE).putUserData(PsiFileEx.BATCH_REFERENCE_PROCESSING, true)

    val offset = psiFile.text.indexOf("Excluded excluded;")
    val ref = checkNotNull(psiFile.findReferenceAt(offset))
    val target = (ref as PsiJavaReference).advancedResolve(true)
    assertNull(target.element)
  }
}
