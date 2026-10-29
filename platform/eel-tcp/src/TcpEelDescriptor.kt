// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.tcp

import com.intellij.platform.eel.EelDescriptorWithIsolatedWorkspace
import com.intellij.platform.eel.EelDescriptorWithoutNativeFileChooserSupport
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.EelPathBoundDescriptor
import com.intellij.platform.util.annotations.VisibleToClasses
import java.nio.file.Path

@VisibleToClasses(
  "com.intellij.platform.frontend.split.baseline.eel.RdEelDescriptor",
  "com.intellij.platform.frontend.split.baseline.eel.RdEelFileChooserContributor",
  "com.intellij.platform.frontend.split.baseline.eel.RdEelPathTest",
  "com.intellij.platform.frontend.split.baseline.eel.RdTcpEelPathParser",
  "com.intellij.platform.frontend.split.baseline.light.LightIjentRedeploymentKt",
  "com.intellij.platform.ijent.functional.RdEelHostMachineTest",
  "com.intellij.platform.ijent.functional.RdEelVfsRefreshTest",
  "com.intellij.platform.ijent.functional.fs.AutoExcludeOverIjentTest",
  "com.intellij.platform.ijent.functional.fs.GotoFileOverIjentTest",
  "com.intellij.rd.eel.frontend.VerifyRdEelAction",
)
abstract class TcpEelDescriptor(
  override val osFamily: EelOsFamily,
  internal val internalName: String,
) : EelDescriptorWithoutNativeFileChooserSupport,
    EelPathBoundDescriptor,
    EelDescriptorWithIsolatedWorkspace {
  abstract val rootPathString: String
  override val rootPath: Path
    get() = Path.of(rootPathString)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as TcpEelDescriptor
    return rootPathString == other.rootPathString
  }

  override fun hashCode(): Int = rootPathString.hashCode()

  override fun toString(): String = "${javaClass.simpleName}(osFamily=$osFamily, rootPathString='$rootPathString')"
}