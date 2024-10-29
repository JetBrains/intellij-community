// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight

import com.intellij.JavaTestUtil
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.assertj.core.api.Assertions.assertThat
import java.awt.datatransfer.DataFlavor
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class CreateTBXReferenceTest : BasePlatformTestCase() {
  override fun getTestDataPath(): String = "${JavaTestUtil.getJavaTestDataPath()}/codeInsight/copyTBXReference/"

  fun testMethodReference() =
    doTest("fqn=C2#C2")

  fun testSelection() =
    doTest("fqn=X&selection=3:3-7:3")

  fun testMultipleSelections() =
    doTest("path=MultipleSelections.java:11:5&selection1=2:5-4:5&selection2=5:5-7:5&selection3=8:5-10:5&selection4=11:5-13:5")

  fun testGroovyConstantReference() =
    doTest("fqn=BuildOptions#USE_COMPILED_CLASSES_PROPERTY", fileName = getTestName(false) + ".groovy")

  fun testPathWithLocation() =
    doTest("path=PathWithLocation.java:4:13")

  fun testMultipleFiles() =
    doTest("path1=File1.java&path2=File2.java", fileName = null, psiElements = arrayOf(
      myFixture.configureByFile("File1.java"),
      myFixture.configureByFile("File2.java"))
    )

  private fun doTest(
    expectedParameters: String,
    fileName: String? = getTestName(false) + ".java",
    psiElements: Array<PsiElement>? = null
  ) {
    fileName?.let {
      myFixture.configureByFile(it)
    }

    val dataContext = SimpleDataContext.builder()
      .setParent(DataManager.getInstance().getDataContext(myFixture.editor.component))
      .apply {
        if (psiElements != null) {
          add(LangDataKeys.PSI_ELEMENT_ARRAY, psiElements)
        }
      }
      .build()

    val action = ActionManager.getInstance().getAction("CopyTBXReference")

    val presentation = Presentation()
    presentation.copyFrom(action.templatePresentation)
    action.actionPerformed(AnActionEvent(dataContext, presentation, ActionPlaces.MAIN_MENU, ActionUiKind.MAIN_MENU, null, 0, ActionManager.getInstance()))

    val content = CopyPasteManager.getInstance().contents?.getTransferData(DataFlavor.stringFlavor) as String
    val url = URLDecoder.decode(content, StandardCharsets.UTF_8)
    assertThat(url).startsWith("jetbrains://idea/navigate/reference?project=${project.name}")
    assertThat(url).endsWith("&${expectedParameters}")
  }
}
