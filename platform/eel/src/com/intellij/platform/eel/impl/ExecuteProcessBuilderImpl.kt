// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl

import com.intellij.platform.eel.EelExecApi

data class ExecuteProcessBuilderImpl(override val exe: String) : EelExecApi.ExecuteProcessBuilder {
  init {
    require(exe.isNotEmpty()) { "Executable must be specified" }
  }

  override var args: List<String> = listOf()
    private set
  override var env: Map<String, String> = mapOf()
    private set
  override var pty: EelExecApi.Pty? = null
    private set
  override var workingDirectory: String? = null
    private set

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
}