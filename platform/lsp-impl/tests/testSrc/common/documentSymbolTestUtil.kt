// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.common

import com.intellij.codeInsight.breadcrumbs.FileBreadcrumbsCollector
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.navbar.NavBarItemPresentationData
import com.intellij.platform.navbar.testFramework.compatibilityNavBarChildObjects
import com.intellij.platform.navbar.testFramework.compatibilityNavBarItemPresentation
import com.intellij.platform.navbar.testFramework.dumpContextModel
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.ui.components.breadcrumbs.Crumb
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.jsonrpc.messages.Either
import javax.swing.Icon

/** A class `C` with a method `foo`, and the caret inside `foo`. [classWithMethodSymbols] are its document symbols. */
internal const val CLASS_WITH_METHOD_TEXT = "class C { fun f<caret>oo() }"

/** The document symbols of [CLASS_WITH_METHOD_TEXT]: class `C` with method `foo`. */
internal fun classWithMethodSymbols(): List<Either<SymbolInformation, DocumentSymbol>> {
  val method = DocumentSymbol("foo", SymbolKind.Method, lineZeroRange(10, 20), lineZeroRange(14, 17))
  val clazz = DocumentSymbol("C", SymbolKind.Class, lineZeroRange(0, 21), lineZeroRange(6, 7)).apply {
    children = mutableListOf(method)
  }
  return listOf(Either.forRight(clazz))
}

private fun lineZeroRange(startChar: Int, endChar: Int): Range = Range(Position(0, startChar), Position(0, endChar))

internal suspend fun CodeInsightTestFixture.moveCaret(offset: Int) {
  withContext(Dispatchers.EDT) {
    editor.caretModel.moveToOffset(offset)
  }
}

/** The breadcrumbs that the editor shows at its caret. The LSP collector sends its request synchronously, so this runs off the EDT. */
internal suspend fun CodeInsightTestFixture.crumbsAtCaret(): List<Crumb> = readAction { breadcrumbsAtCaret }

/** The breadcrumbs collector that the platform picks for [file]. */
internal suspend fun CodeInsightTestFixture.breadcrumbsCollector(file: VirtualFile): FileBreadcrumbsCollector = readAction {
  FileBreadcrumbsCollector.findBreadcrumbsCollector(project, file)
}

/** The members that the navigation bar shows under [parent], a file or a document symbol. */
@RequiresReadLock
internal fun navBarChildren(parent: PsiElement): List<PsiElement> = compatibilityNavBarChildObjects(parent).filterIsInstance<PsiElement>()

/** The icon of [element] in the navigation bar. */
@RequiresReadLock
internal fun navBarIcon(element: PsiElement): Icon? = (compatibilityNavBarItemPresentation(element) as? NavBarItemPresentationData)?.icon

/** The texts of the items that the navigation bar shows for the editor, from the project to the element at the caret. */
internal suspend fun CodeInsightTestFixture.navBarPath(): List<String> {
  val dataContext = withContext(Dispatchers.EDT) { EditorUtil.getEditorDataContext(editor) }
  return dumpContextModel(dataContext, project)
}
