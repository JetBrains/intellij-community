package com.intellij.tools.build.bazel.ijPluginPackager

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.invariantSeparatorsPathString

internal data class NonClasspathDataArgument(
  val relativeOutputPath: Path,
  /** The mode of the copied file, or of each regular file of the copied directory. */
  val mode: Int,
  val source: Path,
)

/**
 * Copies [nonClasspathData] to [outputDirectory], and returns the declared mode of each copied regular file.
 * The key is the path relative to [outputDirectory] with `/` as the separator. A symbolic link gets no entry.
 */
internal fun copyNonClasspathData(nonClasspathData: List<NonClasspathDataArgument>, outputDirectory: Path): Map<String, Int> {
  val fileModes = HashMap<String, Int>()
  for (data in nonClasspathData) {
    val outputPath = resolveRelativeOutputPath(outputDirectory, data.relativeOutputPath)
    Files.createDirectories(outputPath.parent)
    copyNonClasspathData(data.source, outputPath) { copiedFile ->
      fileModes.put(outputDirectory.normalize().relativize(copiedFile).invariantSeparatorsPathString, data.mode)
    }
  }
  return fileModes
}

private fun resolveRelativeOutputPath(outputDirectory: Path, relativeOutputPath: Path): Path {
  val normalizedOutputDirectory = outputDirectory.normalize()
  val outputPath = normalizedOutputDirectory.resolve(relativeOutputPath).normalize()
  if (!outputPath.startsWith(normalizedOutputDirectory)) {
    throw IjPluginPackagingException("Non-classpath data output path must be inside the plugin distribution: $relativeOutputPath")
  }
  return outputPath
}

private fun copyNonClasspathData(source: Path, outputPath: Path, regularFileCopied: (Path) -> Unit) {
  val sourceRoot = source.toRealPath()
  Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
      Files.createDirectories(outputPath.resolve(source.relativize(dir)))
      return FileVisitResult.CONTINUE
    }

    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
      val destination = outputPath.resolve(source.relativize(file))
      if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
        throw IjPluginPackagingException("Cannot copy $file to $destination because the output file already exists")
      }
      if (attrs.isSymbolicLink) {
        val linkTarget = Files.readSymbolicLink(file)
        val resolvedTarget = (if (linkTarget.isAbsolute) linkTarget else file.parent.resolve(linkTarget)).toRealPath()
        if (!resolvedTarget.startsWith(sourceRoot)) {
          throw IjPluginPackagingException("Cannot copy symlink $file because its target $resolvedTarget is outside the source directory $sourceRoot")
        }
        val correspondingTarget = outputPath.resolve(sourceRoot.relativize(resolvedTarget))
        Files.createSymbolicLink(destination, destination.parent.relativize(correspondingTarget))
      }
      else {
        Files.copy(file, destination, LinkOption.NOFOLLOW_LINKS, StandardCopyOption.COPY_ATTRIBUTES)
        regularFileCopied(destination)
      }
      return FileVisitResult.CONTINUE
    }
  })
}
