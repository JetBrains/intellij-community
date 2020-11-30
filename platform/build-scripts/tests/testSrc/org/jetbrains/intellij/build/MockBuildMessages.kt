// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import junit.framework.AssertionFailedError
import java.util.function.Supplier

class MockBuildMessages : BuildMessages {
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

  override fun <V : Any?> block(blockName: String, body: Supplier<V>): V {
    return body.get()
  }

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