// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.client

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.util.io.BaseOutputReader

class MediatedProcessHandler(
  private val process: MediatedProcess,
  commandLine: GeneralCommandLine
) : KillableColoredProcessHandler(
  process,
  commandLine.commandLineString,
  commandLine.charset
) {
  // our ChannelInputStream unblocks read() on close()
  override fun readerOptions(): BaseOutputReader.Options = BaseOutputReader.Options.BLOCKING

  override fun canKillProcess(): Boolean = true

  override fun doDestroyProcess() {
    process.destroy(!shouldKillProcessSoftly(), destroyGroup = true)
  }

  override fun killProcess() {
    process.destroy(true, destroyGroup = true)
  }
}
