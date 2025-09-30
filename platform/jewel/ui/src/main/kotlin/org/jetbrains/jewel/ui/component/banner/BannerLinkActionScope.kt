package org.jetbrains.jewel.ui.component.banner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.component.DropdownLink
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.items
import org.jetbrains.jewel.ui.util.LocalMessageResourceResolverProvider

/** Defines the scope to configure link actions for a banner. */
public interface BannerLinkActionScope {
    /**
     * Adds a link action in the banner component.
     *
     * @param text The text label to display for the action.
     * @param onClick The callback function to invoke when the action is clicked.
     */
    public fun action(text: String, onClick: () -> Unit)
}

@Composable
internal fun BannerActionsRow(spaceBetweenItems: Dp, block: (BannerLinkActionScope.() -> Unit)?) {
    var visibleItems by remember { mutableIntStateOf(BANNER_MAX_LINK_ACTIONS) }
    val allActions by remember { derivedStateOf { block?.toList().orEmpty() } }

    val messageProvider = LocalMessageResourceResolverProvider.current

    Layout(
        content = {
            // Only taking the first few items to display directly
            for (action in allActions.take(BANNER_MAX_LINK_ACTIONS + 1)) {
                Link(action.text, action.onClick)
            }

            DropdownLink(messageProvider.resolveIdeBundleMessage("action.text.more")) {
                items(allActions.drop(visibleItems), isSelected = { false }, onItemClick = { it.onClick() }) {
                    Text(text = it.text)
                }
            }
        },
        measurePolicy =
            BannerActionLayoutMeasurePolicy(spaceBetweenItems, BANNER_MAX_LINK_ACTIONS) { visibleItems = it },
        modifier = Modifier.semantics { isTraversalGroup = true },
    )
}

private fun (BannerLinkActionScope.() -> Unit).toList() = buildList {
    this@toList(
        object : BannerLinkActionScope {
            override fun action(text: String, onClick: () -> Unit) {
                add(BannerActionEntry(text, onClick))
            }
        }
    )
}

@GenerateDataFunctions
private class BannerActionEntry(val text: String, val onClick: () -> Unit) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BannerActionEntry

        if (text != other.text) return false
        if (onClick != other.onClick) return false

        return true
    }

    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + onClick.hashCode()
        return result
    }

    override fun toString(): String = "BannerActionEntry(text='$text', onClick=$onClick)"
}

private class BannerActionLayoutMeasurePolicy(
    private val spaceBetweenItems: Dp,
    private val maxVisibleItems: Int,
    private val onMaxVisibleItemsChanged: (Int) -> Unit,
) : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        if (measurables.isEmpty()) return layout(0, 0) {}

        // Making sure we give as much space as possible to measure the real view size
        val relaxedConstraints = constraints.copy(maxWidth = Constraints.Infinity, maxHeight = Constraints.Infinity)

        // Separate regular items from the "More" dropdown
        val regularItems = measurables.take(measurables.size - 1)
        val moreAction = measurables.last()

        // Always measure the "More" dropdown first to ensure we have space for it
        val moreActionPlaceable = moreAction.measure(relaxedConstraints)
        val moreActionWidth = moreActionPlaceable.width

        // Determine how many items can fit in the available space
        var visibleItemsCount = 0
        var currentWidth = 0
        val itemSpacing = spaceBetweenItems.roundToPx()

        // Measure each item to see how many will fit
        val measuredItems = regularItems.map { it.measure(relaxedConstraints) }
        var showMoreAction = regularItems.size > maxVisibleItems

        // Calculate available width for regular items
        val availableWidthForItems = constraints.maxWidth - (if (showMoreAction) moreActionWidth + itemSpacing else 0)

        for (i in measuredItems.indices) {
            if (showMoreAction && visibleItemsCount >= (maxVisibleItems - 1)) {
                // We can only have up to maxVisibleItems. If there are more than that,
                // we show maxVisibleItems - 1 items, plus the "more" dropdown link.
                // E.g., if maxVisibleItems is 3, and we have 4 items, we show the first
                // two + "more", and the others go in the overflow.
                break
            }
            val item = measuredItems[i]

            val itemWidthWithSpacing = item.width + (if (visibleItemsCount > 0 || showMoreAction) itemSpacing else 0)
            if (currentWidth + itemWidthWithSpacing <= availableWidthForItems) {
                currentWidth += itemWidthWithSpacing
                visibleItemsCount++
            } else {
                // If the show more action is not yet estimated, we need to check if we can fit it
                // with the current items. Otherwise, start removing items until we can fit the "More" action
                while (
                    !showMoreAction &&
                        visibleItemsCount > 0 &&
                        currentWidth + moreActionWidth + itemSpacing > availableWidthForItems
                ) {
                    // If we can't fit the next item, we need to stop and remove until we can fit the "More" action
                    visibleItemsCount -= 1
                    currentWidth -= (measuredItems[visibleItemsCount].width + itemSpacing)
                }

                showMoreAction = true
                break
            }
        }

        // Update the visible items count
        onMaxVisibleItemsChanged(visibleItemsCount)

        // Get the placeables for the visible items
        val placeables = measuredItems.take(visibleItemsCount)

        // Calculate total width and height
        val totalWidth =
            placeables.sumOf { it.width } +
                (if (placeables.isNotEmpty()) (placeables.size - 1) * itemSpacing else 0) +
                (if (showMoreAction) moreActionWidth + itemSpacing else 0)
        val maxHeight = (placeables.maxOfOrNull { it.height } ?: 0).coerceAtLeast(moreActionPlaceable.height)

        return layout(totalWidth, maxHeight) {
            var xPosition = 0

            // Place visible regular items
            placeables.forEachIndexed { index, placeable ->
                placeable.placeRelative(xPosition, maxHeight - placeable.height)
                xPosition += placeable.width
                if (index < placeables.lastIndex || showMoreAction) {
                    xPosition += itemSpacing
                }
            }

            // Place "More" dropdown if needed
            if (showMoreAction) {
                moreActionPlaceable.placeRelative(xPosition, 0)
            }
        }
    }
}

private const val BANNER_MAX_LINK_ACTIONS = 3
