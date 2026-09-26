package org.jetbrains.jewel.ui.painter

import androidx.compose.ui.unit.Density

/** Provides path, resolved path, and accepted hints to composables that load painters from a [PainterProvider]. */
public interface PainterProviderScope : Density {
    /** The original, unmodified resource path as provided by the caller. */
    public val rawPath: String

    /** The resolved resource path after applying any path transformations or hint overrides. */
    public val path: String

    /** The list of [PainterHint]s that were accepted and applied to this scope. */
    public val acceptedHints: List<PainterHint>
}

/** Extends [PainterProviderScope] with the set of [ClassLoader]s used to resolve resource-backed painters. */
public interface ResourcePainterProviderScope : PainterProviderScope {
    /** The set of [ClassLoader]s used to locate and load resource-backed painter assets. */
    public val classLoaders: Set<ClassLoader>
}
