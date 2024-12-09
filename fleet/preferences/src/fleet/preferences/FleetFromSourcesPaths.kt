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

  private const val fontsResourcesDirectory: String = "/frontend/fonts"

  @Deprecated(message = "should use the string property on next dock api breakage", ReplaceWith("fontsResourcesDirectory"))
  val fontsDirectory: Path = Path.of(fontsResourcesDirectory)

  @Deprecated(message = "use [DesktopDockResources#osIntegrationAppIconFile] instead, it will be set appropriately in dev distributions", level = DeprecationLevel.ERROR)
  val dockAppDevIconFile: Path by lazy { // TODO: remove in next Dock API breakage
    Path.of("") // former path has been emptied here, Fleet should never know about build tooling paths
  }

  val nemmetPath: Path by lazy {
    projectRoot.resolve("plugins/emmet/frontend/resources/nemmet/dist/nemmet.js")
  }

  // TODO: remove the usages of this property and delete it.
  // Ideally `skiko.library.path` should always be set by the tooling or distribution argfile, making `skikoLibraryDirectory` property redundant.
  // Currently, this is needed for GalleryApp and some isolated UI tests, which can be run using JPS and which would break missing `skiko.library.path` property.
  val skikoLibraryDirectory: Path by lazy {
    val appDirectory = fleetProperty("fleet.distribution.app.directory")?.let { Path.of(it) }
      ?: projectRoot.resolve("build/build/localDistribution") // FIXME: this will break when we move to `:fl` and `:air` projects (only works if `:fleet-build-project:run` was once run)
    appDirectory.resolve("libs")
  }

  //@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.plugins.keymap.test", "fleet.app.fleet.tests"])
  val bundledKeymapsDirectory: Path by lazy {
    projectRoot.resolve("plugins/keymap/frontend/resources/fleet/keymap")
  }

  //@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.noria.ui.test"])
  object Fonts {
    val jbMonoTTF: String by lazy {
      "$fontsResourcesDirectory/JetBrainsMono/JetBrainsMono-Regular.ttf"
    }
    val notoColorEmoji: String by lazy {
      "$fontsResourcesDirectory/NotoColorEmoji/NotoColorEmoji.ttf"
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