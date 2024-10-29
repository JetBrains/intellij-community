// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl

import com.intellij.platform.eel.EelExecApi

data class ExecuteProcessBuilderImpl(
  override val exe: String,
  override var args: List<String> = listOf(),
  override var env: Map<String, String> = mapOf(),
  override var pty: EelExecApi.Pty? = null,
  override var workingDirectory: String? = null,
) : EelExecApi.ExecuteProcessOptions, EelExecApi.ExecuteProcessOptions.Builder {
  init {
    require(exe.isNotEmpty()) { "Executable must be specified" }
  }

  override fun toString(): String =
    "GrpcExecuteProcessBuilder(" +
    "exe='$exe', " +
    "args=$args, " +
    "env=$env, " +
    "pty=$pty, " +
    "workingDirectory=$workingDirectory" +
    ")"

  override fun args(args: List<String>): ExecuteProcessBuilderImpl = apply {
    this.args = args
  }

  override fun env(env: Map<String, String>): ExecuteProcessBuilderImpl = apply {
    this.env = env
  }

  override fun pty(pty: EelExecApi.Pty?): ExecuteProcessBuilderImpl = apply {
    this.pty = pty
  }

  override fun workingDirectory(workingDirectory: String?): ExecuteProcessBuilderImpl = apply {
    this.workingDirectory = workingDirectory
  }

  override fun build(): EelExecApi.ExecuteProcessOptions {
    return copy()
  }
}