// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.environment.impl

import com.intellij.ide.CommandLineInspectionProgressReporter
import com.intellij.ide.CommandLineInspectionProjectConfigurator
import com.intellij.ide.environment.EnvironmentKey
import com.intellij.ide.warmup.WarmupConfigurator
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.rawProgressReporter
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path
import java.util.function.Predicate
import kotlin.coroutines.coroutineContext

class EnvironmentConfiguration(private val map: Map<EnvironmentKey, String>) {
  companion object {
    val EMPTY : EnvironmentConfiguration = EnvironmentConfiguration(emptyMap())
  }
  fun get(key: EnvironmentKey) : String? = map[key]
}

suspend fun produceConfigurationContext(projectDir: Path?): CommandLineInspectionProjectConfigurator.ConfiguratorContext {
  val reporter = coroutineContext.rawProgressReporter
  if (reporter == null) {
    logger<WarmupConfigurator>().warn("No ProgressReporter installed to the coroutine context. Message reporting is disabled")
  }
  return object : CommandLineInspectionProjectConfigurator.ConfiguratorContext {

    override fun getLogger(): CommandLineInspectionProgressReporter = object : CommandLineInspectionProgressReporter {
      override fun reportError(message: String?) = message?.let { logger<WarmupConfigurator>().warn("PROGRESS: $it") } ?: Unit

      override fun reportMessage(minVerboseLevel: Int, message: String?) = message?.let { logger<WarmupConfigurator>().info("PROGRESS: $it") } ?: Unit
    }

    /**
     * Copy-pasted from [com.intellij.openapi.progress.RawProgressReporterIndicator]. ProgressIndicator will be deprecated,
     * so this code should not be here for long (famous last words...).
     */
    override fun getProgressIndicator(): ProgressIndicator = object : EmptyProgressIndicator() {
      override fun setText(text: String?) {
        reporter?.text(text)
      }

      override fun setText2(text: String?) {
        reporter?.details(text)
      }

      override fun setFraction(fraction: Double) {
        reporter?.fraction(fraction)
      }

      override fun setIndeterminate(indeterminate: Boolean) {
        if (indeterminate) {
          reporter?.fraction(null)
        }
        else {
          reporter?.fraction(0.0)
        }
      }
    }

    override fun getProjectPath(): Path = projectDir ?: error("Something wrong with this project")

    override fun getFilesFilter(): Predicate<Path> = Predicate { true }

    override fun getVirtualFilesFilter(): Predicate<VirtualFile> = Predicate { true }
  }
}
