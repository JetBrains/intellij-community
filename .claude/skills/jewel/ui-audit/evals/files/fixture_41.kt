import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.theme.JewelTheme

@Composable
fun FileLocationLabel(fileName: String, lineNum: Int) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text("File: ", style = JewelTheme.defaultTextStyle)
    Text(fileName, style = JewelTheme.editorTextStyle)
    Text(" line $lineNum", style = JewelTheme.defaultTextStyle)
  }
}
