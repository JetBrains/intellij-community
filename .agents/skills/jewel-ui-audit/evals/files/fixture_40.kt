import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ColorFilter
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.icon.PathIconKey
import org.jetbrains.jewel.ui.theme.JewelTheme

@Composable
fun FilledIconBadge() {
  Icon(
    key = PathIconKey("icons/filled_status_badge.svg", FilledIconBadge::class.java),
    contentDescription = "Status",
    colorFilter = ColorFilter.tint(JewelTheme.globalColors.borders.normal),
  )
}
