// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.ui.actions.dashboard

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.DiskQueryRelay
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.platform.ijent.IjentCallerContext
import com.intellij.platform.ijent.community.impl.nio.fsBlocking
import com.intellij.platform.util.progress.reportProgressScope
import com.intellij.util.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal class EmulateIjentFreezeAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Duplicated {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    application.executeOnPooledThread {
      runBlockingMaybeCancellable {
        withContext(Dispatchers.Default) {
          repeat(3) {
            launch {
              project.getEelDescriptor().fsBlocking {
                doHeavyTask("pooled$it")
              }
            }
          }
          delay(1000.milliseconds)
          launch {
            withContext(Dispatchers.EDT) {
              project.getEelDescriptor().fsBlocking {
                doHeavyTask("edt")
              }
            }
          }
          launch {
            withContext(Dispatchers.EDT) {
              DiskQueryRelay.compute<Unit, Exception> {
                project.getEelDescriptor().fsBlocking {
                  doHeavyTask("edt+dqr")
                }
              }
            }
          }
          delay(1000.milliseconds)
          repeat(3) {
            launch {
              project.getEelDescriptor().fsBlocking {
                doHeavyTask("pooled$it")
              }
            }
          }
        }
      }
    }
  }

  private suspend fun doHeavyTask(@NlsSafe title: String) {
    checkNotNull(IjentCallerContext.getSaved()?.reconnectUi).withRequestedDialog { dialog ->
      repeat(3) { i ->
        delay(1.seconds)
        withContext(dialog.edtAndModality) {
          withModalProgress(ModalTaskOwner.component(dialog.component), title, TaskCancellation.nonCancellable()) {
            reportProgressScope(100) { reporter ->
              repeat(20) { j ->
                reporter.itemStep("$title: $i:$j") {
                  delay(100.milliseconds)
                }
              }
            }
          }
        }
      }
    }
  }
}