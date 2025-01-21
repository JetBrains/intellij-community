// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.samples.showcase.views

import androidx.compose.runtime.Composable
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.skiko.hostOs

@GenerateDataFunctions
public class KeyBinding(
    public val macOs: Set<String> = emptySet(),
    public val windows: Set<String> = emptySet(),
    public val linux: Set<String> = emptySet(),
)

public fun KeyBinding.forCurrentOs(): Set<String> =
    when {
        hostOs.isMacOS -> macOs
        hostOs.isLinux -> linux
        else -> windows
    }

@GenerateDataFunctions
public class ViewInfo(
    public val title: String,
    public val iconKey: IconKey,
    public val keyboardShortcut: KeyBinding? = null,
    public val content: @Composable () -> Unit,
)
