/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.codeInsight.daemon.quickFix

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInspection.InspectionsBundle.message
import com.intellij.codeInspection.reflectiveAccess.JavaLangInvokeHandleSignatureInspection
import com.intellij.codeInspection.reflectiveAccess.JavaLangInvokeHandleSignatureInspection.DEFAULT_SIGNATURE
import com.intellij.codeInspection.reflectiveAccess.JavaLangInvokeHandleSignatureInspection.POSSIBLE_SIGNATURES
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil.ReflectiveSignature
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

/**
 * @author Pavel.Dolgov
 */
class JavaLangInvokeMethodHandleSignatureFixTest : LightCodeInsightFixtureTestCase() {

  override fun getProjectDescriptor(): LightProjectDescriptor = JAVA_9

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(JavaLangInvokeHandleSignatureInspection())
  }

  override fun getTestDataPath(): String = JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/methodHandle"

  fun testConstructor() = doTestConstructor(INT)
  fun testConstructor2() = doTestConstructor(INT)
  fun testConstructor3() = doTestConstructor()
  fun testConstructor4() = doTestConstructor()

  fun testGenericMethod() = doTestMethod(OBJECT, OBJECT)
  fun testGenericMethod2() = doTestMethod(OBJECT, OBJECT, OBJECT_ARRAY)
  fun testGenericMethod3() = doTestMethod(OBJECT, OBJECT, STRING)
  fun testGenericMethod4() = doTestMethod(OBJECT, OBJECT)

  fun testStaticMethod() = doTestMethod(VOID)
  fun testStaticMethod2() = doTestMethod(STRING, STRING)
  fun testStaticMethod3() = doTestMethod(STRING, STRING, STRING_ARRAY)
  fun testStaticMethod4() = doTestReplace("findStatic")

  fun testVirtualMethod() = doTestMethod(VOID)
  fun testVirtualMethod2() = doTestMethod(STRING, STRING)
  fun testVirtualMethod3() = doTestMethod(STRING, STRING, STRING_ARRAY)
  fun testVirtualMethod4() = doTestReplace("findVirtual")


  fun doTestMethod(vararg withSignature: String) = doTest(USE_METHOD, *withSignature)
  fun doTestConstructor(vararg withSignature: String) = doTest(USE_CONSTRUCTOR, VOID, *withSignature)
  fun doTestReplace(replacement: String) = doTest(message("inspection.handle.signature.replace.with.fix.name", replacement))

  fun doTest(actionPrefix: String, vararg withSignature: String) {
    val testName = getTestName(false)
    val file = myFixture.configureByFile("before$testName.java")

    val action = findAction(actionPrefix)
    val signature = ReflectiveSignature.create(listOf(*withSignature)) ?: ReflectiveSignature.NO_ARGUMENT_CONSTRUCTOR_SIGNATURE

    val lookupElements = launchAction(action, signature)
    checkLookupElements(file, lookupElements)
    myFixture.checkResultByFile("after$testName.java")
  }

  private fun findAction(actionPrefix: String): IntentionAction {
    val actions = myFixture.filterAvailableIntentions(actionPrefix)
    if (actions.size == 1) {
      return actions[0]
    }
    assertEquals("Too many actions", 0, actions.size)

    val familyName = when (actionPrefix) {
      USE_CONSTRUCTOR -> message("inspection.handle.signature.use.constructor.fix.family.name")
      USE_METHOD -> message("inspection.handle.signature.use.method.fix.family.name")
      else -> {
        fail("Unexpected action " + actionPrefix); ""
      }
    }
    val familyActions = myFixture.filterAvailableIntentions(familyName)
    assertEquals("Family action", 1, familyActions.size)

    return familyActions[0]
  }

  private fun launchAction(action: IntentionAction, signature: ReflectiveSignature): List<LookupElement>? {
    myFixture.editor.putUserData(DEFAULT_SIGNATURE, signature)
    try {
      myFixture.launchAction(action)
      return myFixture.editor.getUserData(POSSIBLE_SIGNATURES)
    }
    finally {
      myFixture.editor.putUserData(DEFAULT_SIGNATURE, null)
      myFixture.editor.putUserData(POSSIBLE_SIGNATURES, null)
    }
  }

  private fun checkLookupElements(file: PsiFile?, lookupElements: List<LookupElement>?) {
    val expected = (file?.children ?: arrayOf())
      .filter { it is PsiComment }
      .map { it.text.removePrefix("//").trim() }

    val actual = (lookupElements ?: listOf())
      .map {
        val presentation = LookupElementPresentation()
        it.renderElement(presentation)
        val typeText = presentation.typeText
        if (typeText != null) "${typeText} ${presentation.itemText}" else presentation.itemText
      }
    assertEquals("Popup menu", expected, actual)
  }

  private val VOID = "void"
  private val INT = "int"
  private val OBJECT = "java.lang.Object"
  private val OBJECT_ARRAY = "java.lang.Object[]"
  private val STRING = "java.lang.String"
  private val STRING_ARRAY = "java.lang.String[]"

  private val USE_CONSTRUCTOR = "Use constructor"
  private val USE_METHOD = "Use method"
}
