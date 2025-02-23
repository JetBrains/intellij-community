package org.jetbrains.jewel.ui.painter.hints

import androidx.compose.runtime.Immutable
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.painter.PainterHint
import org.jetbrains.jewel.ui.painter.PainterPathHint
import org.jetbrains.jewel.ui.painter.PainterProviderScope

@Immutable
@GenerateDataFunctions
private class PathOverrideImpl(private val overrides: Map<String, String>) : PainterPathHint {
    override fun PainterProviderScope.patch(): String = overrides[path] ?: path

    override fun toString(): String = "PathOverrideImpl(overrides=$overrides)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PathOverrideImpl

        return overrides == other.overrides
    }

    override fun hashCode(): Int = overrides.hashCode()
}

/**
 * A [PainterPathHint] that will override the paths passed as keys in the [overrides] map with the corresponding map
 * values.
 *
 * This is used, for example, to implement the
 * [New UI Icon Mapping](https://plugins.jetbrains.com/docs/intellij/icons.html#mapping-entries) when running in
 * standalone mode. In the IntelliJ Platform, this logic is delegated to the platform by
 * [org.jetbrains.jewel.bridge.BridgeOverride].
 */
@Suppress("FunctionName")
public fun PathOverride(overrides: Map<String, String>): PainterHint =
    if (overrides.isEmpty()) {
        PainterHint.None
    } else {
        PathOverrideImpl(overrides)
    }
