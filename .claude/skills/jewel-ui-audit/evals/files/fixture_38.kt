import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.theme.JewelTheme

@Composable
fun CustomFilterMenu(options: List<String>, onSelect: (String) -> Unit) {
  Column {
    options.forEach { option ->
      var isHovered by remember { mutableStateOf(false) }
      val bg =
        if (isHovered) {
          JewelTheme.simpleListItemStyle.colors.backgroundActive
        } else {
          JewelTheme.globalColors.paneBackground
        }
      Row(modifier = Modifier.background(bg)) { Text(option) }
    }
  }
}
