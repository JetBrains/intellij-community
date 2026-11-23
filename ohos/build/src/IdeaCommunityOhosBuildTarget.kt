// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.IdeaCommunityProperties
import org.jetbrains.intellij.build.impl.createBuildContext
import org.jetbrains.intellij.build.ohos.buildOhosAppForDevelopment
import java.nio.file.Path

private const val DESTINATION_PROPERTY = "intellij.build.ohos.destination"

/**
 * Builds IntelliJ IDEA for OHOS for development and copies the app to the path in [DESTINATION_PROPERTY].
 * `ohos/build-idea.cmd` sets the property from its first argument.
 * To get an archive of the app instead, use `installers.cmd -Dintellij.build.target.os=ohos`.
 */
object IdeaCommunityOhosBuildTarget {
  @JvmStatic
  fun main(args: Array<String>) {
    val destination = System.getProperty(DESTINATION_PROPERTY)?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
                      ?: error("$DESTINATION_PROPERTY is not set. Give the destination of the app to ohos/build-idea.cmd.")
    val options = OpenSourceCommunityInstallersBuildTarget.OPTIONS.let {
      it.copy(buildStepsToSkip = it.buildStepsToSkip + listOf(BuildOptions.SOURCES_ARCHIVE_STEP, BuildOptions.ARCHIVE_PLUGINS))
    }
    buildOhosAppForDevelopment(options, destination) { developmentOptions, lifetime ->
      createBuildContext(
        projectHome = COMMUNITY_ROOT.communityRoot,
        productProperties = IdeaCommunityProperties(COMMUNITY_ROOT.communityRoot),
        options = developmentOptions,
        lifetime = lifetime,
      )
    }
  }
}
