package org.jetbrains.jewel.ui.painter

import androidx.compose.ui.unit.Density

public interface PainterProviderScope : Density {
    public val rawPath: String

    public val path: String

    public val acceptedHints: List<PainterHint>
}

public interface ResourcePainterProviderScope : PainterProviderScope {
    public val classLoaders: Set<ClassLoader>
}
