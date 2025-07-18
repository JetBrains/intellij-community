package org.jetbrains.jewel.markdown.extensions.github.alerts

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.MarkdownBlock

@ApiStatus.Experimental
@ExperimentalJewelApi
public sealed interface GitHubAlert : MarkdownBlock.CustomBlock {
    public val content: List<MarkdownBlock>

    public data class Note(override val content: List<MarkdownBlock>) : GitHubAlert

    public data class Tip(override val content: List<MarkdownBlock>) : GitHubAlert

    public data class Important(override val content: List<MarkdownBlock>) : GitHubAlert

    public data class Warning(override val content: List<MarkdownBlock>) : GitHubAlert

    public data class Caution(override val content: List<MarkdownBlock>) : GitHubAlert
}
