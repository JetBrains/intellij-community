package org.jetbrains.jewel.ui.painter.hints

import androidx.compose.runtime.Immutable
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.painter.PainterHint
import org.jetbrains.jewel.ui.painter.PainterPathHint
import org.jetbrains.jewel.ui.painter.PainterProviderScope

@Immutable
@GenerateDataFunctions
private class OverrideImpl(private val iconOverride: Map<String, String>) : PainterPathHint {

    override fun PainterProviderScope.patch(): String = iconOverride[path] ?: path
}

public fun Override(override: Map<String, String>): PainterHint =
    if (override.isEmpty()) {
        PainterHint.None
    } else {
        OverrideImpl(override)
    }
