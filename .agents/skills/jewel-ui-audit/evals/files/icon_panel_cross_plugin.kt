/*
 * A tool-window row that shows a file's icon next to its name, rendered with Jewel in an IntelliJ plugin.
 * The file may be a KtFile (owned by the Kotlin plugin), which this plugin does NOT depend on.
 * Reviewers: focus on how the icon is obtained and whether it will ship correctly in a packaged build.
 */
package com.example.plugin.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intellij.psi.PsiFile
import org.jetbrains.jewel.bridge.icon.fromPlatformIcon
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icon.IntelliJIconKey
import org.jetbrains.jewel.ui.icon.PathIconKey
// This plugin does not depend on the Kotlin plugin, but references its icon holder anyway:
import org.jetbrains.kotlin.idea.KotlinIcons

@Composable
fun FileRow(file: PsiFile, modifier: Modifier = Modifier) {
  val iconKey =
    if (file.name.endsWith(".kt")) {
      // Reach straight for the Kotlin plugin's icon constant.
      PathIconKey("org/jetbrains/kotlin/idea/icons/kotlin_file.svg", KotlinIcons::class.java)
    } else {
      // For anything else, convert whatever PsiFile.getIcon returns.
      IntelliJIconKey.fromPlatformIcon(file.getIcon(0))
    }

  Row(modifier = modifier.padding(4.dp)) {
    Icon(key = iconKey, contentDescription = file.name)
    Text(text = file.name, modifier = Modifier.padding(start = 4.dp))
  }
}
