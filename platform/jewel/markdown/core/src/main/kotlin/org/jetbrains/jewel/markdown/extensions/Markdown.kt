package org.jetbrains.jewel.markdown.extensions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.markdown.MarkdownMode
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.jetbrains.jewel.markdown.rendering.MarkdownBlockRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling

@get:ApiStatus.Experimental
@ExperimentalJewelApi
public val LocalMarkdownStyling: ProvidableCompositionLocal<MarkdownStyling> = staticCompositionLocalOf {
    error("No MarkdownStyling defined, have you forgotten to provide it?")
}

@get:ApiStatus.Experimental
@ExperimentalJewelApi
public val JewelTheme.Companion.markdownStyling: MarkdownStyling
    @Composable get() = LocalMarkdownStyling.current

@get:ApiStatus.Experimental
@ExperimentalJewelApi
public val LocalMarkdownProcessor: ProvidableCompositionLocal<MarkdownProcessor> = staticCompositionLocalOf {
    error("No MarkdownProcessor defined, have you forgotten to provide it?")
}

@get:ApiStatus.Experimental
@ExperimentalJewelApi
public val JewelTheme.Companion.markdownProcessor: MarkdownProcessor
    @Composable get() = LocalMarkdownProcessor.current

@get:ApiStatus.Experimental
@ExperimentalJewelApi
public val LocalMarkdownBlockRenderer: ProvidableCompositionLocal<MarkdownBlockRenderer> = staticCompositionLocalOf {
    error("No MarkdownBlockRenderer defined, have you forgotten to provide it?")
}

@get:ApiStatus.Experimental
@ExperimentalJewelApi
public val JewelTheme.Companion.markdownBlockRenderer: MarkdownBlockRenderer
    @Composable get() = LocalMarkdownBlockRenderer.current

@get:ApiStatus.Experimental
@ExperimentalJewelApi
public val LocalMarkdownMode: ProvidableCompositionLocal<MarkdownMode> = staticCompositionLocalOf {
    MarkdownMode.Standalone
}

@get:ApiStatus.Experimental
@ExperimentalJewelApi
public val JewelTheme.Companion.markdownMode: MarkdownMode
    @Composable get() = LocalMarkdownMode.current
