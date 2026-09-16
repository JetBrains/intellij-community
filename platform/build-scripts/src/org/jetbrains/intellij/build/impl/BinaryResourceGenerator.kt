package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.io.copyFileToDir
import java.nio.file.Files
import java.nio.file.Path

/** Copies a file or a directory of the community checkout into the plugin, as [PluginLayout.PluginLayoutSpec.withBin] declares it. */
internal data class BinaryResourceGenerator(
  @JvmField val binPathRelativeToCommunity: String,
  @JvmField val outputPath: String,
) : ResourceGenerator {
  override fun invoke(targetDir: Path, context: BuildContext) {
    val source = context.paths.communityHomeDir.resolve(binPathRelativeToCommunity).normalize()
    val target = targetDir.resolve(outputPath)
    when {
      Files.isRegularFile(source) -> copyFileToDir(source, target)
      Files.isDirectory(source) -> copyDir(source, target)
      else -> error("$source doesn't exist")
    }
  }
}
