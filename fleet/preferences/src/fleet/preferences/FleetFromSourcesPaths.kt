// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.preferences

import java.io.IOException
import java.io.UncheckedIOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.io.path.absolute
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
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

  @Deprecated(message = "use FrontendResourceReader.fontsResourcePrefix when possible, we now read fonts from resources", level = DeprecationLevel.ERROR)
  val fontsDirectory: Path = Path.of("/frontend/fonts")

  @Deprecated(message = "use [DesktopDockResources#osIntegrationAppIconFile] instead, it will be set appropriately in dev distributions", level = DeprecationLevel.ERROR)
  val dockAppDevIconFile: Path by lazy { // TODO: remove in next Dock API breakage
    Path.of("") // former path has been emptied here, Fleet should never know about build tooling paths
  }

  val nemmetPath: Path by lazy {
    projectRoot.resolve("plugins/emmet/frontend/resources/nemmet/dist/nemmet.js")
  }

  // TODO: remove the usages of this property and delete it.
  // Ideally `skiko.library.path` should always be set by the tooling or distribution argfile, making `skikoLibraryDirectory` property redundant.
  // Currently, this is needed for GalleryApp and some isolated UI tests.
  // Once our test running logic is unified, we will be able to wire the proper preparation steps to test runs avoiding such code.
  val skikoLibraryDirectory: Path by lazy {
    val skiko = projectRoot.resolve("build/fleet-skiko/build/skiko/buildPlatform")
    require(skiko.takeIf { it.exists()}?.listDirectoryEntries()?.isNotEmpty() == true) {
      """
        '$skiko' is empty or does not exist.
        
        Usually, this directory is automatically populated when required. However a few use cases are outside of the normal test flow.
        If you are in such case (standalone noria UI tests, etc.), you should either:
         - run `./fleet.sh :fleet-skiko:downloadSkikoForJps` Gradle command
         - or, set the JVM system property `skiko.library.path` to a valid skiko downloaded on your machine
        
        If you ran through a JPS configuration, probably it is misconfigured, please contact #fleet-platform.
        If you ran through the Fleet Gradle build tooling, probably it is misconfigured, please contact #fleet-platform.
      """.trimIndent()
    }
    skiko
  }

  //@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.plugins.keymap.test", "fleet.app.fleet.tests"])
  val bundledKeymapsDirectory: Path by lazy {
    projectRoot.resolve("plugins/keymap/frontend/resources/fleet/keymap")
  }

  //@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.noria.ui.test"])
  object Fonts {
    @Deprecated("use FrontendResourceReader.fontsResourcePrefix when possible, we now read fonts from resources", level = DeprecationLevel.ERROR)
    val jbMonoTTF: String = "/frontend/fonts/JetBrainsMono/JetBrainsMono-Regular.ttf"

    @Deprecated("use FrontendResourceReader.fontsResourcePrefix when possible, we now read fonts from resources", level = DeprecationLevel.ERROR)
    val notoColorEmoji: String = "/frontend/fonts/NotoColorEmoji/NotoColorEmoji.ttf"
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