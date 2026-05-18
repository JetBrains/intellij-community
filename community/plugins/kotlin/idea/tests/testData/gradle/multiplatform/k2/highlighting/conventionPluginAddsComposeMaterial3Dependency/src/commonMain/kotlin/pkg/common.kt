//region Test configuration
// - hidden: line markers
//endregion
package pkg

import androidx.compose.runtime.Composable
import androidx.compose.material3.*

@Composable
fun usesMaterial3() {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {
            TextButton(onClick = {}) {
                Text("OK")
            }
        },
        text = {
            Text("Body")
        },
    )
}
