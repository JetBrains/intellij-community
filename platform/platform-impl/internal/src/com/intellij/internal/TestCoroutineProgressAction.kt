// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.util.ProgressIndicatorWithDelayedPresentation.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.platform.ide.progress.*
import com.intellij.platform.util.coroutines.forEachConcurrent
import com.intellij.platform.util.coroutines.mapConcurrent
import com.intellij.platform.util.coroutines.transformConcurrent
import com.intellij.platform.util.progress.*
import com.intellij.ui.dsl.builder.panel
import kotlinx.coroutines.*
import javax.swing.JComponent

internal class TestCoroutineProgressAction : AnAction() {

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
                delay(DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS.toLong() + 10) // + epsilon
              }
            }
          }
        }
      }
    }.show()
  }

  private fun CoroutineScope.cancellableBGProgress(project: Project) {
    launch {
      val taskCancellation = TaskCancellation.cancellable()
        .withButtonText("Cancel Button Text")
        .withTooltipText("Cancel tooltip text")
      withBackgroundProgress(project, "Cancellable task title", taskCancellation) {
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
    delay(100 + (Math.random() * 1000).toLong())
  }
}
