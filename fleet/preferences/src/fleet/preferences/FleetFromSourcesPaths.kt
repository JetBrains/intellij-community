// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.preferences

import java.io.IOException
import java.io.UncheckedIOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.io.path.absolute
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.io.path.toPath

// Paths for resources while running Fleet from sources
// None of its methods should be called on production call path
object FleetFromSourcesPaths {
  val isRunningFromSources: Boolean by lazy {
    !isFleetDistributionMode && findRepositoryRoot() != null
  }

  val intellijProjectRoot: Path by lazy {
    requireNotNull(findRepositoryRoot()) {
      "Cannot find IntelliJ repository root"
    }.absolute().normalize()
  }

  val projectRoot: Path by lazy {
    intellijProjectRoot.resolve("fleet")
  }

  val fontsDirectory: Path by lazy {
    projectRoot.resolve("frontend.ui/src/main/fonts")
  }

  val dockAppDevIconFile: Path by lazy {
    projectRoot.resolve("resources/artwork/fleet/fleet-appicon-dev.png")
  }

  val nemmetPath: Path by lazy {
    projectRoot.resolve("plugins/emmet/frontend/resources/nemmet/dist/nemmet.js")
  }

  private val buildDirectory: Path by lazy {
    fleetProperty("fleet.build.directory.path")?.let { Path.of(it) } ?: projectRoot.resolve("build/build")
  }

  val skikoLibraryDirectory: Path by lazy {
    buildDirectory.resolve("localDistribution/libs")
  }

  //@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.plugins.keymap.test", "fleet.app.fleet.tests"])
  val bundledKeymapsDirectory: Path by lazy {
    projectRoot.resolve("plugins/keymap/frontend/resources/fleet/keymap")
  }

  //@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.noria.ui.test"])
  object Fonts {
    val jbMonoTTF: String by lazy {
      fontsDirectory.resolve("JetBrainsMono/JetBrainsMono-Regular.ttf").pathString
    }
    val notoColorEmoji: String by lazy {
      fontsDirectory.resolve("NotoColorEmoji/NotoColorEmoji.ttf").pathString
    }
  }

  private fun findRepositoryRoot(): Path? {
    var directory: Path? = findFleetRootByClass()
    while (directory != null) {
      if (directory.name != "community") {
        try {
          val children = Files.list(directory).use { it.map(Path::name).collect(Collectors.toSet()) }
          if (children.contains(".idea") && children.contains("fleet")) {
            return directory
          }
        }
        catch (ignore: IOException) {
        }
        catch (ignore: UncheckedIOException) {
        }
      }
      directory = directory.parent
    }
    return null
  }

  private fun findFleetRootByClass(): Path? {
    val url: URL? = javaClass.getResource("/${javaClass.name.replace('.', '/')}.class")
    return when (url?.protocol) {
      "file" -> url.toURI().toPath()
      "jar" -> URL(url.file).toURI().toPath().pathString.split("!").firstOrNull()?.let { Path.of(it) }
      else -> null
    }
  }
}