// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.completion.CompletionItemLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor.setShowTemplatesInTests
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.postfix.completion.PostfixTemplateModCompletionItemProvider
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import java.util.Locale

/**
 * The fixture of a Java postfix template test. Create it with [postfixTemplateFixture].
 *
 * The test data of a test is `<test name>.java` and `<test name>_after.java`, with the first letter of the test name in lower case.
 * With `modCommand`, the members expand the template through the postfix completion items of the ModCommand API.
 * Call the members on the EDT.
 */
class PostfixTemplateFixture internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
  private val textCall: String,
) {
  /**
   * Returns the name of the test method without the `test` prefix, as [LightJavaCodeInsightTestFixture.getTestName] does.
   */
  fun getTestName(lowercaseFirstLetter: Boolean): String = fixture.getTestName(lowercaseFirstLetter)

  /**
   * Expands the template in the test file with [expandTemplate], and then calls [checkAfterFile].
   */
  @JvmOverloads
  fun doTest(modCommand: Boolean = false) {
    expandTemplate(modCommand)
    checkAfterFile()
  }

  /**
   * Opens the test file and types the text call of the fixture at the caret.
   * With [modCommand], it first selects the postfix completion item of the template key before the caret.
   */
  @JvmOverloads
  fun expandTemplate(modCommand: Boolean = false) {
    if (!modCommand) {
      // Only enable template testing when the test types into template fields (not just Tab)
      // Without it, templates auto-advance past fields with defaults (needed for Cast)
      // and stay on always-stop fields (needed for Field)
      if ("\t" != textCall) {
        TemplateManagerImpl.setTemplateTesting(fixture.testRootDisposable)
      }
      fixture.configureByFile(getTestName(true) + ".java")
      fixture.type(textCall)
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    }
    else {
      TemplateManagerImpl.setTemplateTesting(fixture.testRootDisposable)
      Registry.get("postfix.template.mod.completion.enabled").setValue(true, fixture.testRootDisposable)
      fixture.configureByFile(getTestName(true) + ".java")
      val elements = fixture.completeBasic()
      val substring = fixture.file.fileDocument.text.substring(0, fixture.caretOffset)
      val list = elements.filter {
        it is CompletionItemLookupElement &&
        (it.item() as? PostfixTemplateModCompletionItemProvider.PostfixModCompletionItem)
          ?.let { item -> substring.endsWith((item.contextObject() as PostfixTemplate).key) } == true
      }
      if (list.size == 1) {
        fixture.lookup.currentItem = list.first()
        fixture.type(textCall)
      }
      else if (list.isEmpty()) {
        LookupManager.getInstance(fixture.project).hideActiveLookup()
        fixture.type(textCall)
      }
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
      finishTemplate()
    }
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
  }

  /**
   * Opens the test file and runs the basic completion with the live templates shown.
   * It selects the first item whose text or type text contains [text], ignoring the case, and types the text call of the fixture.
   * Then it finishes the template and calls [checkAfterFile].
   * With [modCommand], only the postfix completion items of the ModCommand API can be selected.
   */
  @JvmOverloads
  fun doTestCompletion(text: String?, modCommand: Boolean = false) {
    setShowTemplatesInTests(true, fixture.testRootDisposable)
    fixture.configureByFile(getTestName(true) + ".java")
    val elements = fixture.completeBasic()
    if (text != null) {
      val candidates = if (!modCommand) elements.toList()
      else elements.filter {
        it is CompletionItemLookupElement && it.item() is PostfixTemplateModCompletionItemProvider.PostfixModCompletionItem
      }
      val lowerText = text.lowercase(Locale.ROOT)
      for (element in candidates) {
        if (matches(element, lowerText)) {
          fixture.lookup.currentItem = element
          break
        }
      }
    }
    fixture.type(textCall)
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    finishTemplate()
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    checkAfterFile()
  }

  /**
   * Checks the editor against `<test name>_after.java`, with the selection and the caret.
   */
  fun checkAfterFile() {
    fixture.checkResultByFile(getTestName(true) + "_after.java", true)
  }

  private fun finishTemplate() {
    val state = TemplateManagerImpl.getTemplateState(fixture.editor) ?: return
    WriteCommandAction.runWriteCommandAction(fixture.project) { state.gotoEnd(false) }
  }

  private fun matches(element: LookupElement, lowerText: String): Boolean {
    val presentation = LookupElementPresentation()
    element.renderElement(presentation)
    return presentation.itemText!!.lowercase(Locale.ROOT).contains(lowerText) ||
           presentation.typeText!!.lowercase(Locale.ROOT).contains(lowerText)
  }
}

/**
 * Creates a [PostfixTemplateFixture] for one test. Declare it as an instance field.
 *
 * The test data path is `codeInsight/template/postfix/templates/<suffix>` of the Java test data.
 * [textCall] is the text that the fixture types to expand the template.
 */
@JvmOverloads
fun postfixTemplateFixture(
  projectFixture: TestFixture<LightJavaProject>,
  suffix: String,
  textCall: String = "\t",
): TestFixture<PostfixTemplateFixture> = testFixture("postfixTemplateFixture") {
  val fixture = lightJavaCodeInsightFixture(projectFixture).init()
  fixture.testDataPath = JavaTestUtil.getJavaTestDataPath() + "/codeInsight/template/postfix/templates/" + suffix
  initialized(PostfixTemplateFixture(fixture, textCall)) {}
}
