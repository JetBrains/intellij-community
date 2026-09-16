package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.dev.extractDevPluginLibraryResources
import java.nio.file.Path

internal data class LibraryResourceGenerator(
  @JvmField val libraryName: String,
  @JvmField val targetPath: String,
) : ResourceGenerator {
  override fun invoke(targetDir: Path, context: BuildContext) {
    val jars = context.outputProvider.findLibraryRoots(libraryName, moduleLibraryModuleName = null)
    if (jars.size != 1) {
      throw IllegalStateException("$libraryName is expected to have only one jar")
    }
    extractDevPluginLibraryResources(jars[0], targetDir.resolve(targetPath))
  }
}
