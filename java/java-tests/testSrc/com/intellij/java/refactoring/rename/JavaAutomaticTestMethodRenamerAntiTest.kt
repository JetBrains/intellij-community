// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring.rename

import com.intellij.JavaTestUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.PathUtil
import junit.framework.TestCase
import org.junit.jupiter.api.Nested
import java.io.File


class JavaAutomaticTestMethodRenamerAntiTest : LightJavaCodeInsightFixtureTestCase() {
  override fun getTestDataPath(): String = JavaTestUtil.getJavaTestDataPath() + "/refactoring/renameTestMethod"

  override fun getProjectDescriptor(): LightProjectDescriptor =
    object : DefaultLightProjectDescriptor() {
      override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
        super.configureModule(module, model, contentEntry)
        val jar1 = File(PathUtil.getJarPathForClass(Nested::class.java))
        PsiTestUtil.addLibrary(model, "JUnit", jar1.parent, jar1.name)
        contentEntry.addSourceFolder(contentEntry.url + "/${getTestName(true)}/test_src", true)
      }
    }

  fun testTestMethod() = doTest("ClassTest.java")

  fun testHelperMethod() = doTest("ClassTest.java")

  fun testHelperClass() = doTest("TestUtil.java")

  private fun doTest(filename: String) {
    val filePath = "${getTestName(true)}/test_src/$filename"
    myFixture.configureByFile(filePath)
    val element = myFixture.elementAtCaret
    AutomaticRenamerFactory.EP_NAME.extensionList.forEach {
      TestCase.assertFalse(it.isApplicable(element))
    }
  }
}