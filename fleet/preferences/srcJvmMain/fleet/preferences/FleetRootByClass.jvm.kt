// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.preferences

import fleet.util.multiplatform.Actual
import kotlinx.io.files.Path
import java.net.URL
import kotlin.io.path.absolutePathString
import kotlin.io.path.pathString
import kotlin.io.path.toPath
import kotlin.jvm.javaClass

@Actual("findFleetRootByClass")
internal fun findFleetRootByClassJvm(): Path? {
  val url: URL? = FleetFromSourcesPaths.javaClass.getResource("/${FleetFromSourcesPaths.javaClass.name.replace('.', '/')}.class")
  return when (url?.protocol) {
    "file" -> url.toURI().toPath()
    "jar" -> URL(url.file).toURI().toPath().pathString.split("!").firstOrNull()?.let { java.nio.file.Path.of(it) }
    else -> null
  }?.absolutePathString()?.let { Path(it) }
}