package org.jetbrains.intellij.build.impl

import com.intellij.util.io.Decompressor
import org.jetbrains.intellij.build.BuildContext
import java.nio.file.Path

/** Extracts the one jar of the project library [libraryName] into [targetPath]. The dev-dist plan states it as an `archive-tree` asset. */
internal data class LibraryResourceGenerator(
  @JvmField val libraryName: String,
  @JvmField val targetPath: String,
) : ResourceGenerator {
  override fun invoke(targetDir: Path, context: BuildContext) {
    val jars = context.outputProvider.findLibraryRoots(libraryName, moduleLibraryModuleName = null)
    if (jars.size != 1) {
      throw IllegalStateException("$libraryName is expected to have only one jar")
    }
    Decompressor.Zip(jars[0]).extract(targetDir.resolve(targetPath))
  }
}
