// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.splitApi

import com.intellij.execution.RunContentDescriptorIdImpl
import com.intellij.execution.dashboard.RunDashboardServiceId
import com.intellij.ide.ui.icons.IconId
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
sealed interface RunDashboardServiceDto {
  val uuid: RunDashboardServiceId

  val name: String
  val iconId: IconId?
  val typeId: String
  val typeDisplayName: String
  val typeIconId: IconId
  val folderName: String?

  val contentId: RunContentDescriptorIdImpl?

  val isRemovable: Boolean
  val serviceViewId: String

  val isStored: Boolean
  val isActivateToolWindowBeforeRun: Boolean
  val isFocusToolWindowBeforeRun: Boolean
}

@ApiStatus.Internal
@Serializable
data class RunDashboardMainServiceDto(
  override val uuid: RunDashboardServiceId,
  override val name: String,
  override val iconId: IconId?,
  override val typeId: String,
  override val typeDisplayName: String,
  override val typeIconId: IconId,
  override val folderName: String?,
  override val contentId: RunContentDescriptorIdImpl?,
  override val isRemovable: Boolean,
  override val serviceViewId: String,
  override val isStored: Boolean,
  override val isActivateToolWindowBeforeRun: Boolean,
  override val isFocusToolWindowBeforeRun: Boolean,
) : RunDashboardServiceDto

@ApiStatus.Internal
@Serializable
data class RunDashboardAdditionalServiceDto(
  override val uuid: RunDashboardServiceId,
  override val name: String,
  override val iconId: IconId?,
  override val typeId: String,
  override val typeDisplayName: String,
  override val typeIconId: IconId,
  override val folderName: String?,
  override val contentId: RunContentDescriptorIdImpl?,
  override val isRemovable: Boolean,
  override val serviceViewId: String,
  override val isStored: Boolean,
  override val isActivateToolWindowBeforeRun: Boolean,
  override val isFocusToolWindowBeforeRun: Boolean,
) : RunDashboardServiceDto

@ApiStatus.Internal
fun RunDashboardMainServiceDto.toAdditionalServiceDto(contentId: RunContentDescriptorIdImpl): RunDashboardAdditionalServiceDto {
  return RunDashboardAdditionalServiceDto(
    uuid = uuid,
    name = name,
    iconId = iconId,
    typeId = typeId,
    typeDisplayName = typeDisplayName,
    typeIconId = typeIconId,
    folderName = folderName,
    contentId = contentId,
    isRemovable = isRemovable,
    serviceViewId = serviceViewId,
    isStored = isStored,
    isActivateToolWindowBeforeRun = isActivateToolWindowBeforeRun,
    isFocusToolWindowBeforeRun = isFocusToolWindowBeforeRun,
  )
}