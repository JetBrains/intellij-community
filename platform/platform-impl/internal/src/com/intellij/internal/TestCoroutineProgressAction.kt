// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.TaskInfo
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.progress.impl.ProgressSuspender
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.wm.ex.IdeFrameEx
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl
import com.intellij.platform.ide.progress.*
import com.intellij.platform.ide.progress.suspender.TaskSuspender
import com.intellij.platform.util.coroutines.forEachConcurrent
import com.intellij.platform.util.coroutines.mapConcurrent
import com.intellij.platform.util.coroutines.transformConcurrent
import com.intellij.platform.util.progress.*
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.progress.ProgressUIUtil
import com.intellij.util.TimeoutUtil
import kotlinx.coroutines.*
import java.lang.ref.WeakReference
import javax.swing.JComponent

private class TestCoroutineProgressAction : AnAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    try {
      runWithModalProgressBlocking(ModalTaskOwner.guess(), "Synchronous never-ending modal progress") {
        awaitCancellation()
      }
    }
    catch (_: ProcessCanceledException) {
    }

    val project = e.project ?: return
    object : DialogWrapper(project, false, IdeModalityType.MODELESS) {

      init {
        init()
      }

      private val cs = CoroutineScope(SupervisorJob())

      override fun dispose() {
        cs.cancel()
        super.dispose()
      }

      override fun createCenterPanel(): JComponent = panel {
        row {
          button("Cancellable BG Progress") {
            cs.cancellableBGProgress(project)
          }
          button("Non-Cancellable BG Progress") {
            cs.nonCancellableBGProgress(project)
          }
          button("Old Cancellable BG Progress") {
            cs.oldBGProgress(ProgressIndicatorBase(), project, "Old Cancellable BG Progress", cancellable = true)
          }
        }
        row {
          button("Cancellable Modal Progress") {
            cs.modalProgress(project)
          }
          button("Non-Cancellable Modal Progress") {
            cs.nonCancellableModalProgress(project)
          }
        }
        row {
          button("Cancellable Synchronous Modal Progress") {
            runWithModalProgressBlocking(project, "Cancellable synchronous modal progress") {
              doStuff()
            }
          }
          button("Non-Cancellable Synchronous Modal Progress") {
            runWithModalProgressBlocking(
              ModalTaskOwner.project(project),
              "Non-cancellable synchronous modal progress",
              TaskCancellation.nonCancellable(),
            ) {
              doStuff()
            }
          }
        }
        row {
          button("Delayed Completion BG Progress") {
            cs.launch {
              withBackgroundProgress(project, "Delayed completion BG progress") {
                withContext(NonCancellable) {
                  stage(parallel = true)
                }
              }
            }
          }
          button("Delayed Completion Modal Progress") {
            cs.launch {
              withModalProgress(project, "Delayed completion modal progress") {
                withContext(NonCancellable) {
                  stage(parallel = true)
                }
              }
            }
          }
        }
        row {
          button("300ms BG Progress") {
            cs.launch {
              withBackgroundProgress(project, "300ms background progress") {
                delay(ProgressUIUtil.DEFAULT_PROGRESS_DELAY_MILLIS + 10) // + epsilon
              }
            }
          }
        }
        row {
          val suspenders = mutableListOf<WeakReference<TaskSuspender>>()
          button("2 Suspendable BG Progresses") {
            val suspender = TaskSuspender.suspendable("Task suspended")
            suspenders.add(WeakReference(suspender))
            cs.suspendableBGProgress(suspender, project)
          }
          button("Pause/Resume") {
            suspenders.forEach {
              val suspender = it.get() ?: return@forEach
              if (suspender.isPaused()) suspender.resume() else suspender.pause("Suspended by test action")
            }
          }
        }
        row {
          val indicators = mutableListOf<WeakReference<ProgressIndicatorEx>>()
          button("Old Suspendable BG Progress") {
            val indicator = ProgressIndicatorBase()
            indicators.add(WeakReference(indicator))
            cs.oldBGProgress(indicator, project, "Old Suspendable BG Progress", suspendable = true)
          }
          button("Pause/Resume") {
            indicators.forEach {
              val indicator = it.get() ?: return@forEach
              val suspender = ProgressSuspender.getSuspender(indicator) ?: return@forEach
              if (suspender.isSuspended) suspender.resumeProcess() else suspender.suspendProcess("Suspended by test action")
            }
          }
        }
        row {
          button("Cancellable BG Progress, Invisible in Status Bar") {
            cs.cancellableBGProgress(project, visibleInStatusBar = false)
          }
        }
      }
    }.show()
  }

  private fun CoroutineScope.cancellableBGProgress(project: Project, visibleInStatusBar: Boolean = true) {
    launch {
      val taskCancellation = TaskCancellation.cancellable()
        .withButtonText("Cancel Button Text")
        .withTooltipText("Cancel tooltip text")
      val title = if (visibleInStatusBar) "Cancellable task title" else "Cancellable invisible in status bar task title"
      withBackgroundProgress(project, title, taskCancellation, null, visibleInStatusBar = visibleInStatusBar) {
        doStuff()
      }
    }
  }

  private fun CoroutineScope.suspendableBGProgress(suspender: TaskSuspender, project: Project) {
    // Check that both tasks are paused simultaneously as they use the same suspender
    launch {
      withBackgroundProgress(project, "Suspendable task title 1", suspender) {
        doStuff()
      }
    }
    launch {
      withBackgroundProgress(project, "Suspendable task title 2", suspender) {
        doStuff()
      }
    }
  }

  private fun CoroutineScope.nonCancellableBGProgress(project: Project) {
    launch {
      withBackgroundProgress(project, "Non cancellable task title", cancellable = false) {
        doStuff()
      }
    }
  }

  private fun CoroutineScope.modalProgress(project: Project) {
    launch {
      withModalProgress(project, "Modal progress") {
        doStuff()
      }
    }
  }

  private fun CoroutineScope.nonCancellableModalProgress(project: Project) {
    launch {
      withModalProgress(
        ModalTaskOwner.project(project),
        "Modal progress",
        TaskCancellation.nonCancellable(),
      ) {
        doStuff()
      }
    }
  }

  private suspend fun doStuff(): Unit = reportSequentialProgress { reporter ->
    reporter.indeterminateStep("Indeterminate stage") {
      randomDelay()
    }
    reporter.nextStep(endFraction = 25, "Sequential stage") {
      stage(parallel = false)
    }
    reporter.nextStep(endFraction = 50) {
      stage(parallel = false)
    }
    reporter.nextStep(endFraction = 75, "Parallel stage") {
      stage(parallel = true)
    }
    reporter.nextStep(endFraction = 100) {
      stage(parallel = true)
    }
  }

  private suspend fun stage(parallel: Boolean) = reportSequentialProgress { reporter ->
    reporter.indeterminateStep("Prepare parallel $parallel")
    randomDelay()
    val items = (1..if (parallel) 100 else 5).toList()
    val transformed = reporter.nextStep(endFraction = 10) {
      transformExample(parallel, items)
    }
    val mapped = reporter.nextStep(endFraction = 30) {
      mapExample(parallel, transformed)
    }
    reporter.nextStep(endFraction = 100) {
      forEachExample(parallel, mapped)
    }
  }

  private suspend fun transformExample(parallel: Boolean, items: List<Int>): Collection<Int> {
    return if (parallel) {
      reportProgress(items.size) { reporter ->
        items.transformConcurrent { item ->
          checkCanceled()
          reporter.itemStep("Transforming $item") {
            if (Math.random() < 0.5) {
              out(item)
            }
          }
        }
      }
    }
    else {
      buildList {
        items.forEachWithProgress { item ->
          checkCanceled()
          withProgressText("Transforming $item") {
            if (Math.random() < 0.5) {
              add(item)
            }
          }
        }
      }
    }
  }

  private suspend fun mapExample(parallel: Boolean, filtered: Collection<Int>): Collection<Int> {
    return if (parallel) {
      reportProgress(filtered.size) { reporter ->
        filtered.mapConcurrent { item ->
          reporter.itemStep("Mapping $item") {
            randomDelay()
            item * 2
          }
        }
      }
    }
    else {
      filtered.mapWithProgress { item ->
        withProgressText(text = "Mapping $item") {
          randomDelay()
          item * 2
        }
      }
    }
  }

  private suspend fun forEachExample(parallel: Boolean, mapped: Collection<Int>) {
    if (parallel) {
      reportProgress(mapped.size) { reporter ->
        mapped.forEachConcurrent { item ->
          reporter.itemStep {
            handleItem(item)
          }
        }
      }
    }
    else {
      mapped.forEachWithProgress { item ->
        handleItem(item)
      }
    }
  }

  private suspend fun handleItem(item: Int): Unit = reportSequentialProgress { reporter ->
    reporter.indeterminateStep("Prepare $item")
    randomDelay()
    reporter.nextStep(endFraction = 100, "Processing $item") {
      reportSequentialProgress { innerReporter ->
        innerReporter.nextStep(endFraction = 50, "Processing $item step 1")
        randomDelay()
        innerReporter.nextStep(endFraction = 100, "Processing $item step 2")
        randomDelay()
      }
    }
  }

  private suspend fun randomDelay() {
    checkCanceled()
    delay(100 + (Math.random() * 1000).toLong())
  }

  @Suppress("UsagesOfObsoleteApi")
  private fun CoroutineScope.oldBGProgress(
    indicator: ProgressIndicatorBase,
    project: Project,
    title: String,
    cancellable: Boolean = false,
    suspendable: Boolean = false,
  ) {
    launch {
      val taskInfo = object : TaskInfo {
        override fun getTitle(): String = title
        override fun isCancellable(): Boolean = cancellable
        override fun getCancelText(): String? = null
        override fun getCancelTooltipText(): String? = null
      }

      withContext(Dispatchers.EDT) {
        showProgressIndicator(project, taskInfo, indicator)
      }

      try {
        ProgressManager.getInstance().runProcess(
          {
            if (suspendable) {
              TimeoutUtil.sleep(1000) // Imitate suspender appearing mid-execution
              ProgressSuspender.markSuspendable(indicator, "Suspended by test action")
            }
            doStuff(indicator)
          }, indicator)
      }
      finally {
        indicator.finish(taskInfo)
      }
    }
  }

  private fun showProgressIndicator(project: Project, taskInfo: TaskInfo, indicator: ProgressIndicatorEx) {
    val frameEx: IdeFrameEx = WindowManagerEx.getInstanceEx().findFrameHelper(project) ?: return
    val statusBar = frameEx.statusBar as? IdeStatusBarImpl ?: return
    statusBar.addProgress(indicator, taskInfo)
  }

  private fun doStuff(indicator: ProgressIndicator) {
    val iterations = 100
    for (i in 1..iterations) {
      TimeoutUtil.sleep(100 + (Math.random() * 1000).toLong())
      indicator.setFraction(i.toDouble() / iterations.toDouble())
      indicator.setText("Processing $i")
      ProgressManager.checkCanceled()
    }
  }
}
