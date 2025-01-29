// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.todo

import com.intellij.codeInsight.highlighting.PsiHighlightedReference
import com.intellij.find.usages.api.*
import com.intellij.find.usages.symbol.SymbolSearchTargetFactory
import com.intellij.ide.todo.TodoView
import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.model.psi.PsiSymbolReferenceProvider
import com.intellij.model.search.SearchContext
import com.intellij.model.search.SearchRequest
import com.intellij.model.search.SearchService
import com.intellij.navigation.NavigatableSymbol
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.navigation.NavigationRequests
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.search.PsiTodoSearchHelperImpl
import com.intellij.psi.search.PsiTodoSearchHelper
import com.intellij.psi.search.TodoItem
import com.intellij.util.Query

internal class TodoSymbolReferenceProvider : PsiSymbolReferenceProvider {
  override fun getReferences(element: PsiExternalReferenceHost, hints: PsiSymbolReferenceHints): Collection<PsiSymbolReference> {
    if (!Registry.`is`("todo.navigation")) return emptyList()

    if (element !is PsiComment) return emptyList()

    if (!hints.referenceClass.isAssignableFrom(TodoSymbolReference::class.java)) return emptyList()

    val file = element.containingFile

    val helper = PsiTodoSearchHelper.getInstance(file.project)
    if (helper == null || !shouldHighlightTodos(helper, file)) {
      return emptyList()
    }

    val textRange = element.textRange

    val todoItems = helper.findTodoItems(file)
    val todoItem = todoItems.find { textRange.contains(it.textRange) } ?: return emptyList()

    val word = todoItem.pattern?.indexPattern?.wordToHighlight ?: return emptyList()
    val text = element.text
    val offset = StringUtil.indexOfIgnoreCase(text, word, 0).takeIf { it >= 0 } ?: return emptyList()

    val refTextRange = TextRange(offset, offset + word.length)
    val ref = TodoSymbolReference(element, refTextRange, todoItem)

    return listOf(ref)
  }

  override fun getSearchRequests(project: Project, target: Symbol): Collection<SearchRequest> {
    if (target !is TodoSymbol) return emptyList()
    val wordToHighlight = target.todoItem.pattern?.indexPattern?.wordToHighlight ?: return emptyList()
    return listOf(SearchRequest.of(wordToHighlight))
  }

  private fun shouldHighlightTodos(helper: PsiTodoSearchHelper, file: PsiFile): Boolean {
    return helper is PsiTodoSearchHelperImpl && helper.shouldHighlightInEditor(file)
  }
}

internal class TodoSymbolSearchTargetFactory : SymbolSearchTargetFactory<TodoSymbol> {
  override fun searchTarget(project: Project, symbol: TodoSymbol): SearchTarget? {
    if (!Registry.`is`("todo.navigation")) return null
    val word = symbol.todoItem.pattern?.indexPattern?.wordToHighlight ?: return null
    return TodoSearchTarget(word)
  }
}

private class TodoSymbolReference(
  private val element: PsiComment,
  private val textRangeInElement: TextRange,
  private val todoItem: TodoItem,
) : PsiHighlightedReference {

  // ----------- reference ------------------
  override fun getElement(): PsiElement = element
  override fun getRangeInElement(): TextRange = textRangeInElement
  override fun resolveReference(): Collection<Symbol> = listOf(TodoSymbol(todoItem))
}

internal class TodoSymbol(
  val todoItem: TodoItem,
) : NavigatableSymbol, Pointer<TodoSymbol> {
  override fun createPointer(): Pointer<TodoSymbol> = this
  override fun dereference(): TodoSymbol = this

  override fun getNavigationTargets(project: Project): Collection<NavigationTarget> =
    listOf(TodoNavigationTarget(todoItem))
}

private class TodoNavigationTarget(
  private val todoItem: TodoItem,
) : NavigationTarget, Pointer<TodoNavigationTarget> {
  override fun createPointer(): Pointer<TodoNavigationTarget> = this
  override fun dereference(): TodoNavigationTarget = this

  override fun computePresentation(): TargetPresentation {
    // todo
    return TargetPresentation.builder("todo item").presentation()
  }

  override fun navigationRequest(): NavigationRequest? =
    NavigationRequests.getInstance().rawNavigationRequest(TodoNavigatable(todoItem))
}

private class TodoNavigatable(
  private val todoItem: TodoItem,
) : Navigatable {
  override fun navigate(requestFocus: Boolean) {
    val runnable = Runnable {
      val todoView = todoItem.file.project.getService(TodoView::class.java)
      val currentFilePanel = todoView.currentFilePanel
      currentFilePanel.selectItem(todoItem)
    }

    if (requestFocus) {
      val windowManager = ToolWindowManager.getInstance(todoItem.file.project)
      val window = windowManager.getToolWindow(ToolWindowId.TODO_VIEW)
      // not all startup activities might have passed?
      window?.activate(runnable)
    }
    else {
      runnable.run()
    }
  }

  override fun canNavigate(): Boolean = true
}

private data class TodoSearchTarget(val wordToHighlight: String) : SearchTarget, Pointer<TodoSearchTarget> {
  override fun createPointer(): Pointer<TodoSearchTarget> = this
  override fun dereference(): TodoSearchTarget = this

  override fun presentation(): TargetPresentation {
    // todo
    return TargetPresentation.builder("todo \"$wordToHighlight\"").presentation()
  }

  override val usageHandler: UsageHandler
    get() = UsageHandler.createEmptyUsageHandler(wordToHighlight)

  override val textSearchRequests: Collection<SearchRequest>
    get() = super.textSearchRequests
}

internal class TodoSearcher : UsageSearcher {
  override fun collectSearchRequest(parameters: UsageSearchParameters): Query<out Usage>? {
    if (!Registry.`is`("todo.navigation")) return null

    val target = parameters.target as? TodoSearchTarget ?: return null

    return SearchService.getInstance()
      .searchWord(parameters.project, target.wordToHighlight)
      .caseSensitive(false)
      .inContexts(SearchContext.inComments())
      .inScope(parameters.searchScope)
      .buildQuery {
        listOf(TextUsage(it.scope.containingFile, it.start.textRange))
      }
  }
}

private class TextUsage(
  override val file: PsiFile,
  override val range: TextRange
) : PsiUsage {

  override fun createPointer(): Pointer<out TextUsage> {
    return Pointer.fileRangePointer(file, range, ::TextUsage)
  }

  override val declaration: Boolean get() = false
}
