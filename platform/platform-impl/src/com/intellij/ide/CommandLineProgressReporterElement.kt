// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * A temporary shim for logging from [com.intellij.ide.CommandLineInspectionProjectConfigurator] to [com.intellij.ide.warmup.WarmupConfigurator]
 * The logging framework for warmup is likely to be reworked later
 */
class CommandLineProgressReporterElement(val reporter: CommandLineInspectionProgressReporter) : AbstractCoroutineContextElement(CommandLineProgressReporterElement) {
  companion object Key : CoroutineContext.Key<CommandLineProgressReporterElement>
}