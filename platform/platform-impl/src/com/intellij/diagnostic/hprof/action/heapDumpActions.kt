/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diagnostic.hprof.action

import com.intellij.diagnostic.hprof.action.HeapDumpSnapshotRunnable.AnalysisOption.IMMEDIATE
import com.intellij.diagnostic.hprof.action.HeapDumpSnapshotRunnable.AnalysisOption.SCHEDULE_ON_NEXT_START
import com.intellij.diagnostic.report.HeapReportProperties
import com.intellij.diagnostic.report.MemoryReportReason
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction

class InternalUserInvokedFullAnalysisAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    HeapDumpSnapshotRunnable(MemoryReportReason.InternalUserInvoked, IMMEDIATE).run()
  }
}

class InternalNonuserInvokedHeapDumpSnapshotAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    HeapDumpSnapshotRunnable(MemoryReportReason.InternalUserInvoked, SCHEDULE_ON_NEXT_START).run()
  }
}

class AnalyzeReportAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val fileChooser = FileChooserFactory.getInstance().createFileChooser(
      FileChooserDescriptorFactory.createSingleFileDescriptor(),
      e.project, null)
    val files = fileChooser.choose(e.project, null)
    if (files.isEmpty()) return
    AnalysisRunnable(files[0].toNioPath(), HeapReportProperties(MemoryReportReason.InternalUserInvoked, ""), false).run()
  }
}