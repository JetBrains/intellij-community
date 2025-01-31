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
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KeyBinding

        if (macOs != other.macOs) return false
        if (windows != other.windows) return false
        if (linux != other.linux) return false

        return true
    }

    override fun hashCode(): Int {
        var result = macOs.hashCode()
        result = 31 * result + windows.hashCode()
        result = 31 * result + linux.hashCode()
        return result
    }

    override fun toString(): String = "KeyBinding(macOs=$macOs, windows=$windows, linux=$linux)"

    public companion object
}

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
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ViewInfo

        if (title != other.title) return false
        if (iconKey != other.iconKey) return false
        if (keyboardShortcut != other.keyboardShortcut) return false
        if (content != other.content) return false

        return true
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + iconKey.hashCode()
        result = 31 * result + (keyboardShortcut?.hashCode() ?: 0)
        result = 31 * result + content.hashCode()
        return result
    }

    override fun toString(): String {
        return "ViewInfo(" +
            "title='$title', " +
            "iconKey=$iconKey, " +
            "keyboardShortcut=$keyboardShortcut, " +
            "content=$content" +
            ")"
    }

    public companion object
}
