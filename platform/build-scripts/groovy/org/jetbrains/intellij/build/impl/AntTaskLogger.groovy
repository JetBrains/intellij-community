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
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import org.apache.tools.ant.BuildEvent
import org.apache.tools.ant.BuildListener
import org.apache.tools.ant.Project
import org.apache.tools.ant.RuntimeConfigurable
import org.jetbrains.intellij.build.BuildMessages

/**
 * @author nik
 */
@CompileStatic
class AntTaskLogger implements BuildListener {
  BuildMessages defaultHandler
  private final Map<Thread, BuildMessages> threadHandlers = [:]
  private final Map<RuntimeConfigurable, BuildMessages> taskHandlers = [:]
  final Project antProject
  private final ThreadLocal<Boolean> processMessages = ThreadLocal.withInitial { true }

  AntTaskLogger(Project antProject) {
    this.antProject = antProject
  }

  void registerThreadHandler(Thread thread, BuildMessages messages) {
    threadHandlers[thread] = messages
  }

  void unregisterThreadHandler(Thread thread) {
    threadHandlers.remove(thread)
  }

  @Override
  void messageLogged(BuildEvent event) {
    if (!processMessages.get() || event.priority > Project.MSG_INFO) return

    String message
    if (event.task != null) {
      def taskName = "[$event.task.taskName]".padLeft(7)
      message = event.message.readLines().collect { "$taskName $it" }.join("\n")
    }
    else {
      message = event.message
    }

    BuildMessages handler = threadHandlers[Thread.currentThread()] ?: taskHandlers[event.task?.runtimeConfigurableWrapper] ?: defaultHandler
    switch (event.priority) {
      case Project.MSG_ERR:
        handler.error(message, event.exception)
        break
      case Project.MSG_WARN:
        handler.warning(message)
        break
      case Project.MSG_INFO:
        handler.info(message)
        break
    }
  }

  void logMessageToOtherLoggers(String message, int level) {
    try {
      processMessages.set(false)
      antProject.log(message, level)
    }
    finally {
      processMessages.set(true)
    }
  }

  @Override
  void buildStarted(BuildEvent event) {
  }

  @Override
  void buildFinished(BuildEvent event) {
  }

  @Override
  void targetStarted(BuildEvent event) {
  }

  @Override
  void targetFinished(BuildEvent event) {
  }

  @Override
  void taskStarted(BuildEvent event) {
    //'exec' task reads the process output in a separate thread so we need to store its runtimeConfigurableWrapper instance to delegate
    //  messages to proper handler (it isn't enough to store the task instance because it's UnknownElement when this method is invoked)
    def handler = threadHandlers[Thread.currentThread()]
    def runtime = event.task?.runtimeConfigurableWrapper
    if (handler != null && runtime != null) {
      taskHandlers[runtime] = handler
    }
  }

  @Override
  void taskFinished(BuildEvent event) {
    taskHandlers.remove(event.task)
  }
}