// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.propertyBased

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.PsiDocumentManagerImpl
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodService
import com.intellij.refactoring.extractMethod.newImpl.MethodExtractor
import com.intellij.refactoring.inline.InlineMethodHandler
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.testFramework.SkipSlowTestLocally
import com.intellij.testFramework.propertyBased.MadTestingUtil
import com.intellij.testFramework.propertyBased.RandomActivityInterceptor
import com.intellij.testFramework.utils.coroutines.waitCoroutinesBlocking
import com.intellij.ui.UiInterceptors
import org.jetbrains.jetCheck.Generator
import org.jetbrains.jetCheck.ImperativeCommand
import org.jetbrains.jetCheck.PropertyChecker
import org.jetbrains.plugins.groovy.lang.psi.util.isWhiteSpaceOrNewLine

@SkipSlowTestLocally
class ExtractInlinedMethodPropertyTest : BaseUnivocityTest() {

  override fun setUp() {
    super.setUp()
    (PsiDocumentManager.getInstance(myProject) as PsiDocumentManagerImpl).disableBackgroundCommit(testRootDisposable)
    TemplateManagerImpl.setTemplateTesting(testRootDisposable)
  }

  fun testInlineExtractMethodCompilation() {
    initCompiler()
    myCompilerTester.rebuild()

    val fileGenerator = psiJavaFiles()
    PropertyChecker.customized()
      .withIterationCount(30)
      .checkScenarios { inlineExtractMethodCompilation(fileGenerator) }
  }

  private fun inlineExtractMethodCompilation(javaFiles: Generator<PsiJavaFile>) = ImperativeCommand { env ->
    var disposable = Disposer.newDisposable()
    try {
      UiInterceptors.register(RandomActivityInterceptor(env, disposable))
      val file = env.generateValue(javaFiles, null)

      env.logMessage("Open file in editor: ${file.virtualFile.path}")
      val editor = FileEditorManager.getInstance(myProject)
                     .openTextEditor(OpenFileDescriptor(myProject, file.virtualFile), true)
                   ?: return@ImperativeCommand

      val methodCalls = methodCalls(file) ?: return@ImperativeCommand
      val methodCall = env.generateValue(methodCalls, null)
      val method = methodCall.resolveMethod() ?: return@ImperativeCommand
      if (method.isConstructor) return@ImperativeCommand
      val parentStatement = PsiTreeUtil.getParentOfType(methodCall, PsiStatement::class.java) ?: return@ImperativeCommand
      val rangeToExtract = createGreedyMarker(editor.document, parentStatement)

      MadTestingUtil.changeAndRevert(myProject) {
        val numberOfMethods = countMethodsInsideFile(file)
        val caret = methodCall.methodExpression.textRange.endOffset
        val logicalPosition = editor.offsetToLogicalPosition(caret)
        env.logMessage("Move caret to ${logicalPosition.line + 1}:${logicalPosition.column + 1}")
        editor.caretModel.moveToOffset(caret)

        ignoreRefactoringErrorHints {
          env.logMessage("Inline method call: ${methodCall.text}")
          InlineMethodHandler.performInline(myProject, editor, method, true)
          PsiDocumentManager.getInstance(myProject).commitAllDocuments()

          val range = TextRange(rangeToExtract.startOffset, rangeToExtract.endOffset)
          env.logMessage("Extract inlined lines: ${editor.document.getText(range)}")
          MethodExtractor().doExtract(file, range)
          waitCoroutinesBlocking(ExtractMethodService.getInstance(file.project).scope)
          require(numberOfMethods != countMethodsInsideFile(file)) { "Method is not extracted" }
          PsiDocumentManager.getInstance(myProject).commitAllDocuments()

          checkCompiles(myCompilerTester.make())
        }
      }
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  private fun ignoreRefactoringErrorHints(runnable: Runnable) {
    try {
      runnable.run()
    } catch (_: CommonRefactoringUtil.RefactoringErrorHintException) {
    } catch (_: BaseRefactoringProcessor.ConflictsInTestsException) {
    }
  }

  private fun countMethodsInsideFile(file: PsiFile): Int {
    return PsiTreeUtil.findChildrenOfType(file, PsiMethod::class.java).size
  }

  private fun methodCalls(file: PsiFile): Generator<PsiMethodCallExpression>? {
    val methodCalls = PsiTreeUtil
      .findChildrenOfType(file, PsiMethodCallExpression::class.java)
      .filter { PsiTreeUtil.getParentOfType(it, PsiExpression::class.java) == null }
    if (methodCalls.isEmpty()) return null
    return Generator.sampledFrom(methodCalls)
  }

  private fun createGreedyMarker(document: Document, element: PsiElement): RangeMarker {
    val previousSibling = PsiTreeUtil.skipMatching(element, PsiElement::getPrevSibling, PsiElement::isWhiteSpaceOrNewLine)
    val nextSibling = PsiTreeUtil.skipMatching(element, PsiElement::getNextSibling, PsiElement::isWhiteSpaceOrNewLine)
    val start = (previousSibling ?: element.parent.firstChild).textRange.endOffset
    val end = (nextSibling ?: element.parent.lastChild).textRange.startOffset
    val rangeMarker = document.createRangeMarker(start, end)
    rangeMarker.isGreedyToLeft = true
    rangeMarker.isGreedyToRight = true
    return rangeMarker
  }

}