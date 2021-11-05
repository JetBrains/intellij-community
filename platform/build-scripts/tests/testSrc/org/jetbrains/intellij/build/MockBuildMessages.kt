// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import io.opentelemetry.api.trace.SpanBuilder
import junit.framework.AssertionFailedError
import java.util.*
import java.util.function.Supplier

class MockBuildMessages : BuildMessages {
  override fun getName() = ""

  override fun isLoggable(level: System.Logger.Level) = false

  override fun log(level: System.Logger.Level, bundle: ResourceBundle?, msg: String?, thrown: Throwable?) {
  }

  override fun log(level: System.Logger.Level?, bundle: ResourceBundle?, format: String?, vararg params: Any?) {
  }

  override fun info(message: String?) {
  }

  override fun debug(message: String?) {
  }

  override fun warning(message: String?) {
  }

  override fun error(message: String?) {
    throw AssertionFailedError(message)
  }

  override fun error(message: String?, cause: Throwable?) {
    throw AssertionFailedError(message)
  }

  override fun compilationError(compilerName: String?, message: String?) {
  }

  override fun compilationErrors(compilerName: String?, messages: MutableList<String>?) {
  }

  override fun progress(message: String?) {
  }

  override fun buildStatus(message: String?) {
  }

  override fun setParameter(parameterName: String?, value: String?) {
  }

  override fun <V : Any?> block(blockName: String, task: Supplier<V>) = task.get()

  override fun <V : Any?> block(spanBuilder: SpanBuilder, task: Supplier<V>) = task.get()

  override fun artifactBuilt(relativeArtifactPath: String?) {
  }

  override fun reportStatisticValue(key: String?, value: String?) {
  }

  override fun forkForParallelTask(taskName: String?): BuildMessages {
    throw UnsupportedOperationException()
  }

  override fun onAllForksFinished() {
  }

  override fun onForkStarted() {
  }

  override fun onForkFinished() {
  }
}