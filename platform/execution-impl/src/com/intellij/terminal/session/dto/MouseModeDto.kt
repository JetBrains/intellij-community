// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.session.dto

import com.jediterm.terminal.emulator.mouse.MouseMode
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
enum class MouseModeDto {
  MOUSE_REPORTING_NONE,
  MOUSE_REPORTING_NORMAL,
  MOUSE_REPORTING_HILITE,
  MOUSE_REPORTING_BUTTON_MOTION,
  MOUSE_REPORTING_ALL_MOTION,
  MOUSE_REPORTING_FOCUS;
}

@ApiStatus.Internal
fun MouseMode.toDto(): MouseModeDto {
  return when (this) {
    MouseMode.MOUSE_REPORTING_NONE -> MouseModeDto.MOUSE_REPORTING_NONE
    MouseMode.MOUSE_REPORTING_NORMAL -> MouseModeDto.MOUSE_REPORTING_NORMAL
    MouseMode.MOUSE_REPORTING_HILITE -> MouseModeDto.MOUSE_REPORTING_HILITE
    MouseMode.MOUSE_REPORTING_BUTTON_MOTION -> MouseModeDto.MOUSE_REPORTING_BUTTON_MOTION
    MouseMode.MOUSE_REPORTING_ALL_MOTION -> MouseModeDto.MOUSE_REPORTING_ALL_MOTION
    MouseMode.MOUSE_REPORTING_FOCUS -> MouseModeDto.MOUSE_REPORTING_FOCUS
  }
}

@ApiStatus.Internal
fun MouseModeDto.toMouseMode(): MouseMode {
  return when (this) {
    MouseModeDto.MOUSE_REPORTING_NONE -> MouseMode.MOUSE_REPORTING_NONE
    MouseModeDto.MOUSE_REPORTING_NORMAL -> MouseMode.MOUSE_REPORTING_NORMAL
    MouseModeDto.MOUSE_REPORTING_HILITE -> MouseMode.MOUSE_REPORTING_HILITE
    MouseModeDto.MOUSE_REPORTING_BUTTON_MOTION -> MouseMode.MOUSE_REPORTING_BUTTON_MOTION
    MouseModeDto.MOUSE_REPORTING_ALL_MOTION -> MouseMode.MOUSE_REPORTING_ALL_MOTION
    MouseModeDto.MOUSE_REPORTING_FOCUS -> MouseMode.MOUSE_REPORTING_FOCUS
  }
}