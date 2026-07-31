package org.jetbrains.jewel.markdown.extensions.github.alerts

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icon.PathIconKey

/** Icon keys for the five GitHub Flavored Markdown alert types. */
@ApiStatus.Experimental
@ExperimentalJewelApi
public object GitHubAlertIcons {
    /** Icon for the NOTE alert type. */
    public val Note: IconKey =
        PathIconKey(
            path = "icons/markdown/extensions/github/alerts/alert-note.svg",
            iconClass = GitHubAlertIcons::class.java,
        )

    /** Icon for the TIP alert type. */
    public val Tip: IconKey =
        PathIconKey(
            path = "icons/markdown/extensions/github/alerts/alert-tip.svg",
            iconClass = GitHubAlertIcons::class.java,
        )

    /** Icon for the IMPORTANT alert type. */
    public val Important: IconKey =
        PathIconKey(
            path = "icons/markdown/extensions/github/alerts/alert-important.svg",
            iconClass = GitHubAlertIcons::class.java,
        )

    /** Icon for the WARNING alert type. */
    public val Warning: IconKey =
        PathIconKey(
            path = "icons/markdown/extensions/github/alerts/alert-warning.svg",
            iconClass = GitHubAlertIcons::class.java,
        )

    /** Icon for the CAUTION alert type. */
    public val Caution: IconKey =
        PathIconKey(
            path = "icons/markdown/extensions/github/alerts/alert-caution.svg",
            iconClass = GitHubAlertIcons::class.java,
        )
}
