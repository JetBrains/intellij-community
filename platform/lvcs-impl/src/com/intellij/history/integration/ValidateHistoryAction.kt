/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.history.integration

import com.intellij.history.core.changes.ChangeSet
import com.intellij.history.core.changes.ChangeVisitor
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.util.ExceptionUtil

@Suppress("HardCodedStringLiteral")
class ValidateHistoryAction : AnAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = ApplicationManager.getApplication().isInternal
  }

  override fun actionPerformed(e: AnActionEvent) {
    object : Task.Modal(e.project, "Checking local history storage", true) {
      override fun run(indicator: ProgressIndicator) {
        val t = System.currentTimeMillis()

        try {
          LocalHistoryImpl.getInstanceImpl().facade?.accept(object : ChangeVisitor() {
            private var count = 0

            override fun end(c: ChangeSet) {
              indicator.checkCanceled()
              if (++count % 10 == 0) {
                indicator.text = "${count} records checked"
              }
            }

            override fun finished() {
              val message = "Local history storage seems to be OK (checked ${count} records in ${System.currentTimeMillis() - t} ms)"
              ApplicationManager.getApplication().invokeLater { Messages.showInfoMessage(e.project, message, "Local History Validation") }
            }
          })
        }
        catch(ex: ProcessCanceledException) { throw ex }
        catch(ex: Exception) {
          Messages.showErrorDialog(e.project, ExceptionUtil.getThrowableText(ex), "Local History Validation Error")
        }
      }
    }.queue()
  }
}