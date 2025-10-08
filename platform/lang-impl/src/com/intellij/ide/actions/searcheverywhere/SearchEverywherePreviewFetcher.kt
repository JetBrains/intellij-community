// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.find.impl.SearchEverywhereItem
import com.intellij.find.impl.getUsageInfo
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.usageView.UsageInfo
import com.intellij.usages.UsageInfo2UsageAdapter
import kotlinx.coroutines.coroutineScope
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.BiConsumer
import kotlin.time.Duration
import kotlin.time.measureTimedValue

@ApiStatus.Internal
class SearchEverywherePreviewFetcher(
  val project: Project,
  private val publishPreviewTime: BiConsumer<Any, Duration>,
  parentDisposable: Disposable,
) {
  private val usagePreviewDisposableList = ConcurrentLinkedQueue<Disposable>()

  init {
    Disposer.register(parentDisposable)  {
      usagePreviewDisposableList.forEach { Disposer.dispose(it) }
    }
  }

  suspend fun fetchPreview(selectedValue: Any?): List<UsageInfo>? = coroutineScope {
    val (collectedUsages, duration) = measureTimedValue {
      (selectedValue as? List<*>)?.filterIsInstance<UsageInfo>() ?: selectedValue?.let { doFetchPreview(it).ifEmpty { null } }
    }

    if (selectedValue != null) publishPreviewTime.accept(selectedValue, duration)

    return@coroutineScope collectedUsages
  }

  private suspend fun doFetchPreview(selectedValue: Any): List<UsageInfo> {
    val usageInfo = readAction {
      findFirstChild(selectedValue, project) {
        usagePreviewDisposableList.add(it)
      }
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

    return runCatching {
      getUsageInfo(usages)
    }.getOrLogException(logger<SearchEverywhereUI>()) ?: emptyList()
  }

  companion object {
    fun findFirstChild(selectedValue: Any, project: Project, disposableHandler: (Disposable) -> Unit = {}): UsageInfo? {
      val psiElement = PSIPresentationBgRendererWrapper.toPsi(selectedValue)
      if (psiElement == null || !psiElement.isValid) return null

      val psiFile = psiElement as? PsiFile
      if (psiFile == null) {
        if (psiElement is PsiFileSystemItem) {
          val vFile = psiElement.virtualFile
          val file = if (vFile == null) null else psiElement.getManager().findFile(vFile)
          if (file != null) {
            return UsageInfo(file, 0, 0, true)
          }
        }
        for (finder in SearchEverywherePreviewPrimaryUsageFinder.EP_NAME.extensionList) {
          val elementForUsageInfo = finder.tryFindPsiElementForUsageInfo(project, psiElement)
          if (elementForUsageInfo != null) return UsageInfo(elementForUsageInfo)
        }
        return UsageInfo(psiElement)
      }

      for (finder in SearchEverywherePreviewPrimaryUsageFinder.EP_NAME.extensionList) {
        val resultPair = finder.tryFindPrimaryUsageInfo(psiFile)
        if (resultPair != null) {
          val usageInfo = resultPair.first
          val disposable = resultPair.second

          if (disposable != null) {
            disposableHandler.invoke(disposable)
          }

          return usageInfo
        }
      }
      return UsageInfo(psiFile)
    }
  }
}