package org.jetbrains.jewel.ui.component.banner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icon.IconKey

/** Defines the scope to configure icon-based actions for a banner. */
public interface BannerIconActionScope {
    /**
     * Adds an icon-based action in the banner component.
     *
     * @param icon The icon to be displayed, represented as an `IconKey`.
     * @param contentDescription A textual description for the icon, used for accessibility purposes. Pass `null` if no
     *   description is needed.
     * @param tooltipText Optional tooltip text to be displayed when the user hovers over the icon.
     * @param onClick The callback function to be invoked when the icon is clicked.
     */
    public fun iconAction(icon: IconKey, contentDescription: String?, tooltipText: String? = null, onClick: () -> Unit)
}

@Composable
internal fun BannerIconActionsRow(block: (BannerIconActionScope.() -> Unit)?) {
    val allActions by remember { derivedStateOf { block?.toList().orEmpty() } }
    for (iconAction in allActions) {
        if (!iconAction.tooltipText.isNullOrBlank()) {
            IconActionButton(
                key = iconAction.icon,
                contentDescription = iconAction.contentDescription,
                onClick = iconAction.onClick,
                tooltip = { Text(iconAction.tooltipText) },
            )
        } else {
            IconActionButton(
                key = iconAction.icon,
                contentDescription = iconAction.contentDescription,
                onClick = iconAction.onClick,
            )
        }
    }
}

private fun (BannerIconActionScope.() -> Unit).toList() = buildList {
    this@toList(
        object : BannerIconActionScope {
            override fun iconAction(
                icon: IconKey,
                contentDescription: String?,
                tooltipText: String?,
                onClick: () -> Unit,
            ) {
                add(BannerActionIcon(icon, contentDescription, tooltipText, onClick))
            }
        }
    )
}

@GenerateDataFunctions
private class BannerActionIcon(
    val icon: IconKey,
    val contentDescription: String?,
    val tooltipText: String?,
    val onClick: () -> Unit,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BannerActionIcon

        if (icon != other.icon) return false
        if (contentDescription != other.contentDescription) return false
        if (tooltipText != other.tooltipText) return false
        if (onClick != other.onClick) return false

        return true
    }

    override fun hashCode(): Int {
        var result = icon.hashCode()
        result = 31 * result + (contentDescription?.hashCode() ?: 0)
        result = 31 * result + tooltipText.hashCode()
        result = 31 * result + onClick.hashCode()
        return result
    }

    override fun toString(): String =
        "BannerActionIcon(icon=$icon, contentDescription=$contentDescription, tooltipText=$tooltipText, onClick=$onClick)"
}
