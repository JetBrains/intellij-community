// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SdkEelMatching")

package com.intellij.execution.target

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.eel.provider.LocalEelMachine
import com.intellij.platform.eel.provider.ownsPath
import org.jetbrains.annotations.ApiStatus
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * Returns `true` if [sdk] can be used on [eelMachine].
 * A target-based SDK always matches. An SDK without a home path never matches.
 * Other SDKs match if [eelMachine] owns their home path.
 */
@ApiStatus.Internal
fun sdkMatchesEel(eelMachine: EelMachine, sdk: Sdk): Boolean {
  if (sdk.sdkAdditionalData is TargetBasedSdkAdditionalData) {
    return true
  }
  val sdkHomePath = sdk.homePath ?: return false
  return sdkMatchesEel(eelMachine, sdkHomePath)
}

/**
 * Returns `true` if [eelMachine] owns [sdkHomePath].
 * If [sdkHomePath] is not a valid path, returns `true` only for the local machine.
 */
@ApiStatus.Internal
fun sdkMatchesEel(eelMachine: EelMachine, sdkHomePath: String): Boolean {
  return try {
    eelMachine.ownsPath(Path.of(sdkHomePath))
  }
  catch (_: InvalidPathException) {
    eelMachine == LocalEelMachine
  }
}
