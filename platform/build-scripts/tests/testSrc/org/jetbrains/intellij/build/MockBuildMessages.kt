// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import junit.framework.AssertionFailedError
import java.util.*

class MockBuildMessages : BuildMessages {
  override fun getName() = ""

  override fun isLoggable(level: System.Logger.Level) = false

  override fun log(level: System.Logger.Level, bundle: ResourceBundle?, msg: String?, thrown: Throwable?) {
  }

  override fun log(level: System.Logger.Level?, bundle: ResourceBundle?, format: String?, vararg params: Any?) {
  }

  override fun info(message: String) {
  }

  override fun debug(message: String) {
  }

  override fun warning(message: String) {
  }

  override fun error(message: String) {
    throw AssertionFailedError(message)
  }

  override fun error(message: String, cause: Throwable) {
    throw AssertionFailedError(message)
  }

  override fun compilationErrors(compilerName: String, messages: List<String>) {
  }

  override fun progress(message: String) {
  }

  override fun buildStatus(message: String) {
  }

  override fun setParameter(parameterName: String, value: String) {
  }

  override fun <V> block(blockName: String, task: () -> V): V = task()

  override fun artifactBuilt(relativeArtifactPath: String) {
  }

  override fun reportStatisticValue(key: String, value: String) {
  }
}