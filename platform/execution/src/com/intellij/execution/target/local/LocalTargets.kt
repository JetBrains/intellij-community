// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("LocalTargets")

package com.intellij.execution.target.local

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.ExecutionException
import com.intellij.execution.process.LocalPtyOptions
import com.intellij.execution.target.PtyOptions
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.EelPathException
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.equalsMayBeUsingMachine
import com.intellij.platform.eel.provider.getEelDescriptor
import java.nio.file.InvalidPathException
import java.nio.file.Path

internal fun PtyOptions.toLocalPtyOptions(): LocalPtyOptions =
  when (this) {
    is LocalTargetPtyOptions -> localPtyOptions
    else -> {
      LocalPtyOptions.defaults().builder()
        .initialRows(initialRows)
        .initialColumns(initialColumns)
        .build()
    }
  }

/**
 * Converts [workDir] to a [Path] on [eel].
 *
 * The [String] does not tell the format of [workDir], so this function tries two formats in this sequence:
 * 1. A path that the IDE uses to access [eel], for example `\\wsl.localhost\Ubuntu\home\user`.
 * 2. A path on [eel], for example `/home/user`.
 *
 * @throws ExecutionException if [workDir] has neither format
 */
@Throws(ExecutionException::class)
internal fun convertWorkDirToEel(workDir: String, eel: EelDescriptor): Path =
  try {
    Path.of(workDir).takeIf { it.getEelDescriptor().equalsMayBeUsingMachine(eel) }
  }
  catch (_: InvalidPathException) {
    null
  } ?: try {
    EelPath.parse(workDir, eel).asNioPath()
  }
  catch (_: EelPathException) {
    throw ExecutionException(ExecutionBundle.message("dialog.message.working.directory.doesn.t.exist", workDir))
  }
