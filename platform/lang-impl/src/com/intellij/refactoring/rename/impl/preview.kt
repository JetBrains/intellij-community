// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.impl

import com.intellij.model.Pointer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.rename.api.PsiRenameUsage
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.refactoring.rename.api.RenameUsage
import com.intellij.refactoring.rename.ui.progressTitle
import com.intellij.refactoring.rename.ui.withBackgroundIndicator
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewUtil
import com.intellij.usages.*
import com.intellij.util.containers.toArray
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import java.awt.event.ActionEvent
import java.lang.Runnable
import javax.swing.AbstractAction
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

internal suspend fun showUsageView(
  project: Project,
  targetPointer: Pointer<out RenameTarget>,
  newName: String,
  usagePointers: Collection<UsagePointer>
): UsageView {
  require(!ApplicationManager.getApplication().isReadAccessAllowed)
  val usageViewUsages: List<Usage> = usagePointers.mapNotNull { pointer ->
    readAction {
      pointer.dereference()?.let { renameUsage ->
        asUsage(renameUsage, newName)
      }
    }
  }
  return withContext(CoroutineName("show UV") + Dispatchers.EDT) {
    UsageViewManager.getInstance(project).showUsages(
      arrayOf(RenameTarget2UsageTarget(targetPointer, newName)),
      usageViewUsages.toArray(Usage.EMPTY_ARRAY),
      usageViewPresentation()
    )
  }
}

internal fun CoroutineScope.appendUsages(
  usageView: UsageView,
  channel: ReceiveChannel<UsagePointer>,
  newName: String
) {
  Disposer.register(usageView, Disposable {
    channel.cancel()
  })
  launch(CoroutineName("appendUsages")) {
    for (pointer: UsagePointer in channel) {
      runReadAction {
        val renameUsage: RenameUsage = pointer.dereference() ?: return@runReadAction
        val usageViewUsage: Usage = asUsage(renameUsage, newName) ?: return@runReadAction
        usageView.appendUsage(usageViewUsage)
      }
    }
  }
}

internal fun CoroutineScope.previewRenameAsync(
  project: Project,
  targetPointer: Pointer<out RenameTarget>,
  newName: String,
  usageView: UsageView,
  rerunSearch: CoroutineScope.() -> ReceiveChannel<UsagePointer>
): Deferred<Collection<UsagePointer>> {
  val rerunAction: () -> Unit = {
    launch {
      val progressTitle: String = targetPointer.progressTitle() ?: return@launch
      val channel: ReceiveChannel<Pointer<out RenameUsage>> = rerunSearch()
      withBackgroundIndicator(project, progressTitle) {
        appendUsages(usageView, channel, newName)
      }
    }
  }

  return async(CoroutineName("selectedUsagesAsync") + Dispatchers.EDT) {
    suspendCancellableCoroutine { continuation: Continuation<Collection<UsagePointer>> ->
      customizeUsagesView(usageView, continuation, rerunAction)
    }
  }
}

private fun asUsage(renameUsage: RenameUsage, newName: String): Usage? {
  return when (renameUsage) {
    is PsiRenameUsage -> UsageInfo2UsageAdapter(PsiRenameUsage2UsageInfo(renameUsage, newName))
    else -> null
  }
}

private fun usageViewPresentation(): UsageViewPresentation {
  val presentation = UsageViewPresentation()
  presentation.tabText = RefactoringBundle.message("rename.preview.tab.title")
  presentation.isShowReadOnlyStatusAsRed = true
  presentation.isShowCancelButton = true
  return presentation
}

private fun customizeUsagesView(
  usageView: UsageView,
  continuation: Continuation<Collection<UsagePointer>>,
  rerunAction: () -> Unit
) {
  val refactoringRunnable = Runnable {
    ApplicationManager.getApplication().assertReadAccessAllowed()
    val usagesToRefactor: Collection<UsageInfo> = UsageViewUtil.getNotExcludedUsageInfos(usageView)
    val renameUsages: Collection<UsagePointer> = usagesToRefactor.mapNotNull {
      (it as? PsiRenameUsage2UsageInfo)?.renameUsage?.createPointer()
    }
    continuation.resume(renameUsages)
  }
  usageView.setRerunAction(object : AbstractAction() {
    override fun actionPerformed(e: ActionEvent?) {
      rerunAction()
    }
  })
  usageView.addPerformOperationAction(
    refactoringRunnable,
    null,
    RefactoringBundle.message("usageView.need.reRun"),
    RefactoringBundle.message("usageView.doAction")
  )
}
