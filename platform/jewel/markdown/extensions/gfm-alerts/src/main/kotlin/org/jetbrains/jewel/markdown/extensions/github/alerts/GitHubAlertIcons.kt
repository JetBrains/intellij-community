package org.jetbrains.jewel.markdown.extensions.github.alerts

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icon.PathIconKey

@ApiStatus.Experimental
@ExperimentalJewelApi
public object GitHubAlertIcons {
    public val Note: IconKey =
        PathIconKey(
            path = "icons/markdown/extensions/github/alerts/alert-note.svg",
            iconClass = GitHubAlertIcons::class.java,
        )
    public val Tip: IconKey =
        PathIconKey(
            path = "icons/markdown/extensions/github/alerts/alert-tip.svg",
            iconClass = GitHubAlertIcons::class.java,
        )
    public val Important: IconKey =
        PathIconKey(
            path = "icons/markdown/extensions/github/alerts/alert-important.svg",
            iconClass = GitHubAlertIcons::class.java,
        )
    public val Warning: IconKey =
        PathIconKey(
            path = "icons/markdown/extensions/github/alerts/alert-warning.svg",
            iconClass = GitHubAlertIcons::class.java,
        )
    public val Caution: IconKey =
        PathIconKey(
            path = "icons/markdown/extensions/github/alerts/alert-caution.svg",
            iconClass = GitHubAlertIcons::class.java,
        )
}
