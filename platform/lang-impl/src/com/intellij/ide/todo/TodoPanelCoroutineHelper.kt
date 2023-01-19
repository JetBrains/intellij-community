// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.usageView.UsageInfo
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
private class TodoPanelCoroutineHelper(private val panel: TodoPanel) : Disposable {

  private val scope = CoroutineScope(SupervisorJob())

  init {
    Disposer.register(panel, this)
  }

  override fun dispose() {
    scope.cancel()
  }

  fun schedulePreviewPanelLayoutUpdate() {
    scope.launch(Dispatchers.EDT) {
      if (!panel.usagePreviewPanel.isVisible) return@launch

      val lastUserObject = TreeUtil.getLastUserObject(panel.tree.selectionPath)
      val usageInfos = if (lastUserObject != null) {
        readAction {
          val pointer = panel.treeBuilder.getFirstPointerForElement(lastUserObject)

          if (pointer != null) {
            val value = pointer.value!!
            val psiFile = PsiDocumentManager.getInstance(panel.myProject).getPsiFile(value.document)

            if (psiFile != null) {
              val rangeMarker = value.rangeMarker
              val usageInfos = mutableListOf(
                UsageInfo(psiFile, rangeMarker.startOffset, rangeMarker.endOffset),
              )

              value.additionalRangeMarkers
                .filter { it.isValid }
                .mapTo(usageInfos) {
                  UsageInfo(psiFile, it.startOffset, it.endOffset)
                }
            }
            else {
              emptyList()
            }
          }
          else {
            emptyList()
          }
        }
      }
      else {
        emptyList()
      }

      panel.usagePreviewPanel.updateLayout(usageInfos.ifEmpty { null })
    }
  }
}