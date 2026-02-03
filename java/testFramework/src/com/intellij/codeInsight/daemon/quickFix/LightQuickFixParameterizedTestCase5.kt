// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.quickFix

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Comparing
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.PsiUtilBase
import com.intellij.testFramework.FileBasedArgumentProvider
import com.intellij.testFramework.FileBasedTestCaseHelperEx
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.UIUtil
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ArgumentsSource

abstract class LightQuickFixParameterizedTestCase5(projectDescriptor: LightProjectDescriptor? = null)
  : LightJavaCodeInsightFixtureTestCase5(projectDescriptor), FileBasedTestCaseHelperEx {
  protected abstract val basePath: String

  override fun getRelativePath(): String {
    return super.getRelativePath() + basePath
  }

  override fun getRelativeBasePath(): String {
    return basePath
  }

  override fun getFileSuffix(fileName: String): String? {
    return if (!fileName.startsWith(LightQuickFixTestCase.BEFORE_PREFIX)) null
    else fileName.substring(LightQuickFixTestCase.BEFORE_PREFIX.length)
  }

  override fun getBaseName(fileAfterSuffix: String): String? {
    return if (!fileAfterSuffix.startsWith(LightQuickFixTestCase.AFTER_PREFIX)) null
    else fileAfterSuffix.substring(LightQuickFixTestCase.AFTER_PREFIX.length)
  }

  @ParameterizedTest(name = ParameterizedTest.ARGUMENTS_WITH_NAMES_PLACEHOLDER)
  @ArgumentsSource(FileBasedArgumentProvider::class)
  @Throws(Throwable::class)
  fun parameterized(fileName: String) {
    val filePath = "/" + LightQuickFixTestCase.BEFORE_PREFIX + fileName
    val file = fixture.configureByFile(filePath)
    val (hint, context) = runReadAction { ActionHint.parse(file, file.text) to fixture.actionContext }
    val action = hint.findAndCheck(fixture.availableIntentions, context) {
      """
             Test: ${getRelativePath() + filePath}
             Language level: ${PsiUtil.getLanguageLevel(fixture.project)}
             SDK: ${ModuleRootManager.getInstance(ModuleUtilCore.findModuleForFile(fixture.file)!!).sdk}
             Infos: ${LightQuickFixTestCase.getCurrentHighlightingInfo(fixture.doHighlighting())}
             """.trimIndent()
    }
    if (action != null) {
      val text = action.text
      val element = runReadAction { PsiUtilBase.getElementAtCaret(fixture.editor) }
      fixture.launchAction(action)

      runInEdtAndWait { UIUtil.dispatchAllInvocationEvents() }

      val intentions = fixture.availableIntentions
      val afterAction = CodeInsightTestUtil.findIntentionByText(intentions, text)
      if (afterAction != null && Comparing.equal(element, runReadAction { PsiUtilBase.getElementAtCaret(fixture.editor) })) {
        fail("Action '$text' is still available after its invocation in test $filePath")
      }
      fixture.checkResultByFile("/" + LightQuickFixTestCase.AFTER_PREFIX + fileName)
    }
  }

}