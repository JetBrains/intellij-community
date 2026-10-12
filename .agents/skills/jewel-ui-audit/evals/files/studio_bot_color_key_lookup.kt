import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.intellij.ui.JBColor
import org.jetbrains.jewel.bridge.toComposeColor

private val MissingMarker = JBColor.marker("missing Studio Bot color")

@Composable
fun StudioBotSeverityChip(severityKey: String, text: String) {
    val colorKey = "StudioBot.Severity.$severityKey.background"

    val background = remember(colorKey) {
        val direct = JBColor.get(colorKey, MissingMarker)
        try {
            direct.toComposeColor()
        } catch (_: AssertionError) {
            existingPaletteFallback().toComposeColor()
        }
    }

    Chip(
        text = text,
        background = background,
    )
}

private fun existingPaletteFallback(): java.awt.Color =
    JBColor.get("ColorPalette.Gray12", MissingMarker)

@Composable
private fun Chip(text: String, background: Color) {
    // Imagine the usual Jewel Text + rounded background here; the bug under review is the color lookup above.
    @Suppress("UNUSED_VARIABLE")
    val rendered = text to background.toArgb()
}
