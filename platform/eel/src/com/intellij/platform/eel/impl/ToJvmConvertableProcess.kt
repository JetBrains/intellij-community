// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl

import com.intellij.platform.eel.EelProcess
import com.intellij.platform.util.annotations.VisibleToClasses

/**
 * This interface is an implementation detail and should never be used directrly.
 *
 * Every eel process must implement it
 */
@VisibleToClasses(
  "com.intellij.platform.eel.impl.local.DestroyAwareOutput",
  "com.intellij.platform.eel.impl.local.LocalEelPosixProcess",
  "com.intellij.platform.eel.impl.local.LocalEelWindowsProcess",
  "com.intellij.platform.ijent.impl.base.GrpcIjentChildPosixProcess",
  "com.intellij.platform.ijent.impl.base.GrpcIjentChildWindowsProcess",
  "com.intellij.platform.ijent.impl.base.PosixLoginShellSpawn",
)
interface ToJvmConvertableProcess {
  fun convertToJVMProcess(): Process
}

internal fun EelProcess.convertToJavaProcessImpl(): Process = (this as ToJvmConvertableProcess).convertToJVMProcess()
