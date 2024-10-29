// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.impl.source.DummyHolderFactory
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.assertErrorLogged
import org.junit.Assert

class ProblemDescriptionUtilTest : LightPlatformTestCase() {
  fun testHtmlTagsInElementText() {
    doTest("Ref: #ref", "<b/>", "Ref: <b/>", "Ref: <b/>")
  }

  fun testHtmlMessage() {
    doTest("<html>Ref#treeend: <table/></html>", "xxx", "<html>Ref: <table/></html>", "Ref")
  }

  fun testHtmlMessageWithXmlCode() {
    doTest("<html><xml-code>&lt;foo&gt;</xml-code></html>", "xxx", "<html>&lt;foo&gt;</html>", "<foo>")
  }

  fun testEscapedXmlInHtmlMessage() {
    doTest("<html>&lt;foo&gt;</html>", "xxx", "<html>&lt;foo&gt;</html>", "<foo>")
  }

  fun testNoHtml() {
    doTest("Can be simplified to 'a < b'", "xxx", "Can be simplified to 'a < b'", "Can be simplified to 'a < b'")
  }
  
  fun testBinaryFile() {
    val file = VfsTestUtil.createFile(getSourceRoot(), "foo.bmp")
    val psiFile = PsiManager.getInstance(project).findFile(file)!!
    ProblemDescriptorBase(psiFile, psiFile, "", null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false, null, false, false)
    assertErrorLogged<Throwable> {
      ProblemDescriptorBase(psiFile, psiFile, "", null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false, TextRange.EMPTY_RANGE, false, false)
    }
  }

  internal fun doTest(message: String, element: String,
                      expectedEditorMessage: String, expectedTreeMessage: String) {
    val psiElement : FakePsiElement = object : FakePsiElement() {
      override fun getParent(): PsiElement {
        return DummyHolderFactory.createHolder(psiManager, null)
      }

      override fun getText(): String {
        return element
      }

      override fun isPhysical(): Boolean {
        return true // needed to avoid assertion in ProblemDescriptorBase which requires physical elements
      }

      override fun getTextRange(): TextRange {
        return TextRange(0, element.length)
      }
    }

    val descriptorBase = object : ProblemDescriptorBase(psiElement, psiElement, message, null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                               false, null, true, false){
    }
    
    Assert.assertEquals(expectedEditorMessage, ProblemDescriptorUtil.renderDescriptionMessage (descriptorBase, psiElement))
    Assert.assertEquals(expectedTreeMessage,   ProblemDescriptorUtil.renderDescriptionMessage (descriptorBase, psiElement, ProblemDescriptorUtil.TRIM_AT_TREE_END))
  }
} 