package org.jetbrains.jewel.markdown

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi

/**
 * A semantics property key used to store the raw Markdown content for a [Markdown] composable.
 *
 * Can be used in UI tests to assert a node contains a certain raw Markdown, since [SemanticsProperties.Text] is not a
 * viable way to assess a node's content.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public val RawMarkdown: SemanticsPropertyKey<String> = SemanticsPropertyKey("RawMarkdown")

/**
 * A semantics property used to store the raw Markdown content for a [Markdown] composable.
 *
 * Can be used in UI tests to assert a node contains a certain raw Markdown, since [androidx.compose.ui.semantics.text]
 * is not a viable way to assess a node's content.
 */
@get:ApiStatus.Experimental
@set:ApiStatus.Experimental
@ExperimentalJewelApi
public var SemanticsPropertyReceiver.rawMarkdown: String by RawMarkdown
