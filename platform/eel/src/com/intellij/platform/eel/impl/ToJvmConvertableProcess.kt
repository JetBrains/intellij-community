// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl

import com.intellij.platform.eel.EelInternalApi
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.channels.EelDelicateApi

/**
 * This interface is an implementation detail and should never be used directrly.
 *
 * Every eel process must implement it
 */
@EelDelicateApi
interface ToJvmConvertableProcess {

  @EelInternalApi
  fun convertToJVMProcess(): Process
}

internal fun EelProcess.convertToJavaProcessImpl(): Process = (this as ToJvmConvertableProcess).convertToJVMProcess()
