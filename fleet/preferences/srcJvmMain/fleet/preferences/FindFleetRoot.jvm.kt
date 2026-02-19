// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.preferences

import fleet.util.multiplatform.Actual
import kotlinx.io.IOException
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import java.net.URL
import kotlin.io.path.absolutePathString
import kotlin.io.path.pathString
import kotlin.io.path.toPath

@Actual
internal fun findRepositoryRootJvm(): String? {
  val url: URL? = FleetFromSourcesPaths.javaClass.getResource("/${FleetFromSourcesPaths.javaClass.name.replace('.', '/')}.class")
  var directory = when (url?.protocol) {
    "file" -> url.toURI().toPath()
    "jar" -> URL(url.file).toURI().toPath().pathString.split("!").firstOrNull()?.let { java.nio.file.Path.of(it) }
    else -> null
  }?.absolutePathString()?.let { Path(it) }
  while (directory != null) {
    if (directory.name != "community") {
      try {
        val children = SystemFileSystem.list(directory).map(Path::name).toSet()
        if (children.contains(".idea") && children.contains("fleet")) {
          return directory.toString()
        }
      }
      catch (_: IOException) {
      }
    }
    directory = directory.parent
  }
  return null
}