// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight

import com.intellij.JavaTestUtil
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.assertj.core.api.Assertions.assertThat
import java.awt.datatransfer.DataFlavor

class CreateTBXReferenceTest : BasePlatformTestCase() {
  override fun getTestDataPath(): String {
    return "${JavaTestUtil.getJavaTestDataPath()}/codeInsight/copyTBXReference/"
  }

  fun testMethodReference() {
    doTest("fqn=C2#C2")
  }

  fun testSelection() {
    doTest("fqn=X&selection=3:3-7:3")
  }

  fun testMultipleSelections() {
    doTest("path=MultipleSelections.java:11:5&selection1=2:5-4:5&selection2=5:5-7:5&selection3=8:5-10:5&selection4=11:5-13:5")
  }

  fun testGroovyConstantReference() {
    doTest("fqn=BuildOptions#USE_COMPILED_CLASSES_PROPERTY", getTestName(false) + ".groovy")
  }

  fun testPathWithLocation() {
    doTest("path=PathWithLocation.java:4:13")
  }

  fun testMultipleFiles() {
    val file1 = myFixture.configureByFile("File1.java")
    val file2 = myFixture.configureByFile("File2.java")

    doTest("path1=File1.java&path2=File2.java",
           fileName = null,
           additionalDataContext = mapOf(LangDataKeys.PSI_ELEMENT_ARRAY.name to arrayOf(file1, file2)))
  }

  private fun doTest(expectedURL: String,
                     fileName: String? = getTestName(false) + ".java",
                     additionalDataContext: Map<String, Any> = hashMapOf()) {
    fileName?.let { myFixture.configureByFile(fileName) }

    var dataContext = DataManager.getInstance().getDataContext(myFixture.editor.component)
    dataContext = SimpleDataContext.getSimpleContext(additionalDataContext, dataContext)
    val action = ActionManager.getInstance().getAction("CopyTBXReference")

    action.actionPerformed(AnActionEvent(null, dataContext, ActionPlaces.MAIN_MENU, action.templatePresentation, ActionManager.getInstance(), 0))

    val content = CopyPasteManager.getInstance().contents?.getTransferData(DataFlavor.stringFlavor) as String
    assertThat(content).startsWith("jetbrains://idea/navigate/reference?project=light_temp")
    assertThat(content).endsWith("&$expectedURL")
  }
}
