// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.find.impl.SearchEverywhereItem
import com.intellij.find.impl.getUsageInfo
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.usageView.UsageInfo
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.impl.UsagePreviewPanel.Companion.isOneAndOnlyOnePsiFileInUsages
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.launch
import java.util.function.Consumer

internal class SearchEverywherePreviewGenerator(val project: Project,
                                                private val updatePreviewPanel: Consumer<List<UsageInfo>?>,
                                                private val currentSelectedValueComputable: Computable<Any>): Disposable {
  private val usagePreviewDisposableList: MutableList<Disposable> = mutableListOf()

  fun schedulePreview(selectedValue: Any) {
    serviceOrNull<SearchEverywhereCoroutineScopeService>()?.coroutineScope?.launch {
      schedulePreviewSync(selectedValue)
    }
  }

  private suspend fun schedulePreviewSync(selectedValue: Any) {
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

    getUsageInfo(usages, project).thenAccept { infos: List<UsageInfo> ->
      val usageInfos: List<UsageInfo>? = if (!infos.isEmpty()) infos else null
      ReadAction.nonBlocking<Boolean> { isOneAndOnlyOnePsiFileInUsages(usageInfos) }
        .finishOnUiThread(ModalityState.nonModal()) { _: Boolean? ->
          if (currentSelectedValueComputable.compute() != selectedValue) return@finishOnUiThread
          updatePreviewPanel.accept(usageInfos)
        }
        .coalesceBy(this)
        .submit(AppExecutorUtil.getAppExecutorService())
    }.exceptionally { throwable: Throwable? ->
      Logger.getInstance(SearchEverywhereUI::class.java).error(throwable)
      null
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
    usagePreviewDisposableList.forEach { Disposer.dispose(it) }
  }
}