// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.session.dto

import com.jediterm.core.util.TermSize
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class TerminalSizeDto(val columns: Int, val rows: Int)

@ApiStatus.Internal
fun TermSize.toDto(): TerminalSizeDto = TerminalSizeDto(columns, rows)

@ApiStatus.Internal
fun TerminalSizeDto.toTermSize(): TermSize = TermSize(columns, rows)