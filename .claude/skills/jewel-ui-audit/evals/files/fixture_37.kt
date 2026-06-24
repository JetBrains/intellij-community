import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.intellij.openapi.editor.colors.EditorColorsManager

@Composable
fun SearchHighlightBox() {
  val highlightColor =
    remember(EditorColorsManager.getInstance().globalScheme) {
      val scheme = EditorColorsManager.getInstance().globalScheme
      scheme.defaultBackground
    }
}
