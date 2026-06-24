/*
 * Renders a remote avatar + bio block for a hypothetical "Contributor" card in a Jewel panel.
 * The contributor's name/bio come from a bundled resource; the avatar is a remote URL.
 * Reviewers: evaluate async/loading/error states and media handling.
 */
package com.example.contributors.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.io.HttpRequests
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.ui.component.Text

@Composable
fun ContributorCard(name: String, bio: String, avatarUrl: String) {
  Column(modifier = Modifier.padding(8.dp)) {
    RemoteAvatar(avatarUrl)
    Text(name)
    Text(bio)
  }
}

@Composable
private fun RemoteAvatar(url: String) {
  var painter by remember(url) { mutableStateOf<Painter?>(null) }

  if (painter == null) {
    LaunchedEffect(url) {
      withContext(Dispatchers.IO) {
        try {
          val bytes = HttpRequests.request(url).readBytes(null)
          painter = decodeToPainter(bytes)
        } catch (e: Exception) {
          Logger.getInstance("ContributorCard").warn("avatar fetch failed: $url", e)
        }
      }
    }
    return
  }

  Image(painter = painter!!, contentDescription = null, modifier = Modifier.padding(4.dp))
}

private fun decodeToPainter(bytes: ByteArray): Painter = TODO("decode")
