/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.execution.runners

import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.fileEditor.FileDocumentManager

abstract class DefaultProgramRunner : GenericProgramRunner<RunnerSettings>() {
  @Throws(ExecutionException::class)
  override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
    return executeState(state, environment, this)
  }
}

internal fun executeState(state: RunProfileState, env: ExecutionEnvironment, runner: ProgramRunner<*>): RunContentDescriptor? {
  FileDocumentManager.getInstance().saveAllDocuments()
  return showRunContent(state.execute(env.executor, runner), env)
}

fun showRunContent(executionResult: ExecutionResult?, environment: ExecutionEnvironment): RunContentDescriptor? {
  return executionResult?.let { RunContentBuilder(it, environment).showRunContent(environment.contentToReuse) }
}
