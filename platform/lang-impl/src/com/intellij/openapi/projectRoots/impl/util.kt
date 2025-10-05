// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SdkUtils")
@file:ApiStatus.Internal

package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.LocalEelMachine
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

fun findClashingSdk(sdkName: String, sdk: Sdk): SdkEntity? {
  val machine = sdk.homePath?.let { Path.of(it) }?.getEelDescriptor()?.machine ?: LocalEelMachine
  val relevantSnapshot = GlobalWorkspaceModel.getInstance(machine).currentSnapshot
  return relevantSnapshot.entities(SdkEntity::class.java).find { it.name == sdkName }
}