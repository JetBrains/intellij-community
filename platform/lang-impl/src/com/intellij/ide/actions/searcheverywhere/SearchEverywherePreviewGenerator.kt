// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.find.impl.SearchEverywhereItem
import com.intellij.find.impl.getUsageInfo
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.usageView.UsageInfo
import com.intellij.usages.UsageInfo2UsageAdapter
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.mapLatest
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.Consumer

@OptIn(ExperimentalCoroutinesApi::class)
internal class SearchEverywherePreviewGenerator(val project: Project,
                                                private val updatePreviewPanel: Consumer<List<UsageInfo>?>): Disposable {
  private val usagePreviewDisposableList = ConcurrentLinkedQueue<Disposable>()
  private val requestSharedFlow = MutableSharedFlow<Any>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val previewFetchingScope: CoroutineScope?

  init {
    val seCoroutineScope = project.serviceOrNull<SearchEverywhereCoroutineScopeService>()?.coroutineScope
    previewFetchingScope = seCoroutineScope?.childScope("SearchEverywherePreviewGenerator scope")

    previewFetchingScope?.launch {
      requestSharedFlow.mapLatest { selectedValue ->
        fetchPreview(selectedValue).ifEmpty { null }
      }.collectLatest { usageInfos ->
        withContext(Dispatchers.EDT) {
          updatePreviewPanel.accept(usageInfos)
        }
      }
    }
  }

  fun schedulePreview(selectedValue: Any) {
    project.serviceOrNull<SearchEverywhereCoroutineScopeService>()?.coroutineScope?.launch {
      requestSharedFlow.emit(selectedValue)
    }
  }

  private suspend fun fetchPreview(selectedValue: Any): List<UsageInfo> {
    val usageInfo = readAction {
      findFirstChild(selectedValue)
    }

    val usages: MutableList<UsageInfo2UsageAdapter> = ArrayList()
    if (usageInfo != null) {
      usages.add(UsageInfo2UsageAdapter(usageInfo))
    }
    else {
      if (selectedValue is UsageInfo2UsageAdapter) {
        usages.add(selectedValue)
      }
      else if (selectedValue is SearchEverywhereItem) {
        usages.add(selectedValue.usage)
      }
    }

    return try {
      getUsageInfo(usages)
    }
    catch (throwable: Throwable) {
      if (throwable is CancellationException) throw throwable
      Logger.getInstance(SearchEverywhereUI::class.java).error(throwable)
      emptyList()
    }
  }

  private fun findFirstChild(selectedValue: Any): UsageInfo? {
    val psiElement = PSIPresentationBgRendererWrapper.toPsi(selectedValue)
    if (psiElement == null || !psiElement.isValid) return null

    val psiFile = if (psiElement is PsiFile) psiElement else null
    if (psiFile == null) {
      if (psiElement is PsiFileSystemItem) {
        val vFile = psiElement.virtualFile
        val file = if (vFile == null) null else psiElement.getManager().findFile(vFile)
        if (file != null) {
          return UsageInfo(file, 0, 0, true)
        }
      }
      return UsageInfo(psiElement)
    }

    for (finder in SearchEverywherePreviewPrimaryUsageFinder.EP_NAME.extensionList) {
      val resultPair = finder.findPrimaryUsageInfo(psiFile)
      if (resultPair != null) {
        val usageInfo = resultPair.first
        val disposable = resultPair.second

        if (disposable != null) {
          usagePreviewDisposableList.add(Disposable { Disposer.dispose(disposable) })
        }

        return usageInfo
      }
    }
    return UsageInfo(psiFile)
  }

  override fun dispose() {
    previewFetchingScope?.cancel("SearchEverywherePreviewGenerator disposed")
    usagePreviewDisposableList.forEach { Disposer.dispose(it) }
  }
}