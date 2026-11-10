// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInspection.CommonQuickFixBundle
import com.intellij.codeInspection.reflectiveAccess.JavaLangInvokeHandleSignatureInspection
import com.intellij.codeInspection.reflectiveAccess.JavaLangInvokeHandleSignatureInspection.DEFAULT_SIGNATURE
import com.intellij.codeInspection.reflectiveAccess.JavaLangInvokeHandleSignatureInspection.POSSIBLE_SIGNATURES
import com.intellij.java.JavaBundle.message
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil.ReflectiveSignature
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_9
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import kotlin.time.Duration.Companion.minutes

@TestApplication
class JavaLangInvokeMethodHandleSignatureFixTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(JAVA_9)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUpTestDataPath() {
    myFixture.testDataPath = JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/methodHandle"
  }

  @BeforeEach
  fun setUp() {
    myFixture.enableInspections(JavaLangInvokeHandleSignatureInspection())
  }

  @Test
  fun testConstructor(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTestConstructor(INT) }
  @Test
  fun testConstructor2(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTestConstructor(INT) }
  @Test
  fun testConstructor3(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTestConstructor() }
  @Test
  fun testConstructor4(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTestConstructor() }

  @Test
  fun testGenericMethod(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTestMethod(OBJECT, OBJECT) }
  @Test
  fun testGenericMethod2(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTestMethod(OBJECT, OBJECT, OBJECT_ARRAY) }
  @Test
  fun testGenericMethod3(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTestMethod(OBJECT, OBJECT, STRING) }
  @Test
  fun testGenericMethod4(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTestMethod(OBJECT, OBJECT) }

  @Test
  fun testStaticMethod(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTestMethod(VOID) }
  @Test
  fun testStaticMethod2(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTestMethod(STRING, STRING) }
  @Test
  fun testStaticMethod3(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTestMethod(STRING, STRING, STRING_ARRAY) }
  @Test
  fun testStaticMethod4(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTestReplace("findStatic") }

  @Test
  fun testVirtualMethod(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTestMethod(VOID) }
  @Test
  fun testVirtualMethod2(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTestMethod(STRING, STRING) }
  @Test
  fun testVirtualMethod3(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTestMethod(STRING, STRING, STRING_ARRAY) }
  @Test
  fun testVirtualMethod4(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTestReplace("findVirtual") }

  private suspend fun doTestMethod(vararg withSignature: String) = doTest(USE_METHOD, *withSignature)
  private suspend fun doTestConstructor(vararg withSignature: String) = doTest(USE_CONSTRUCTOR, VOID, *withSignature)
  private suspend fun doTestReplace(replacement: String) = doTest(CommonQuickFixBundle.message("fix.replace.with.x", replacement))

  suspend fun doTest(actionPrefix: String, vararg withSignature: String) {
    val testName = myFixture.getTestName(false)
    val file = myFixture.configureByFile("before$testName.java")

    val action = findAction(actionPrefix)
    val signature = ReflectiveSignature.create(listOf(*withSignature)) ?: ReflectiveSignature.NO_ARGUMENT_CONSTRUCTOR_SIGNATURE

    withContext(Dispatchers.EDT) {
      val lookupElements = launchAction(action, signature)
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
      checkLookupElements(file, lookupElements)
      myFixture.checkResultByFile("after$testName.java")
    }
  }

  private fun findAction(actionPrefix: String): IntentionAction {
    val actions = myFixture.filterAvailableIntentions(actionPrefix)
    if (actions.size == 1) {
      return actions[0]
    }
    assertEquals(0, actions.size, "Too many actions")

    val familyName = when (actionPrefix) {
      USE_CONSTRUCTOR -> message("inspection.handle.signature.use.constructor.fix.family.name")
      USE_METHOD -> message("inspection.handle.signature.use.method.fix.family.name")
      else -> {
        fail("Unexpected action $actionPrefix"); ""
      }
    }
    val familyActions = myFixture.filterAvailableIntentions(familyName)
    assertEquals(1, familyActions.size, "Family action")

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
    val expected = (myFixture.file?.children ?: arrayOf())
      .filterIsInstance<PsiComment>()
      .map { it.text.removePrefix("//").trim() }

    val actual = (lookupElements ?: listOf())
      .map {
        val presentation = LookupElementPresentation()
        it.renderElement(presentation)
        val typeText = presentation.typeText
        if (typeText != null) "${typeText} ${presentation.itemText}" else presentation.itemText
      }
    assertEquals(expected, actual, "Popup menu")
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
