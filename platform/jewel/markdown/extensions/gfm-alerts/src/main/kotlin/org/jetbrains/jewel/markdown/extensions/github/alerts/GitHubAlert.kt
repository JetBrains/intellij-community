package org.jetbrains.jewel.markdown.extensions.github.alerts

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.MarkdownBlock

/**
 * A GitHub-style alert, such as `[!NOTE]`.
 *
 * See the
 * [GitHub documentation](https://docs.github.com/en/get-started/writing-on-github/getting-started-with-writing-and-formatting-on-github).
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public sealed interface GitHubAlert : MarkdownBlock.CustomBlock {
    /** The content of the alert. */
    public val content: List<MarkdownBlock>

    /** A `[!NOTE]` alert. */
    public data class Note(override val content: List<MarkdownBlock>) : GitHubAlert

    /** A `[!TIP]` alert. */
    public data class Tip(override val content: List<MarkdownBlock>) : GitHubAlert

    /** An `[!IMPORTANT]` alert. */
    public data class Important(override val content: List<MarkdownBlock>) : GitHubAlert

    /** A `[!WARNING]` alert. */
    public data class Warning(override val content: List<MarkdownBlock>) : GitHubAlert

    /** A `[!CAUTION]` alert. */
    public data class Caution(override val content: List<MarkdownBlock>) : GitHubAlert
}
