package com.intellij.tools.build.bazel.ijPluginPackager

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes

internal data class NonClasspathDataArgument(
  val relativeOutputPath: Path,
  val source: Path,
)

internal fun copyNonClasspathData(nonClasspathData: List<NonClasspathDataArgument>, outputDirectory: Path) {
  for (data in nonClasspathData) {
    val outputPath = resolveRelativeOutputPath(outputDirectory, data.relativeOutputPath)
    Files.createDirectories(outputPath.parent)
    copyNonClasspathData(data.source, outputPath)
  }
}

private fun resolveRelativeOutputPath(outputDirectory: Path, relativeOutputPath: Path): Path {
  val normalizedOutputDirectory = outputDirectory.normalize()
  val outputPath = normalizedOutputDirectory.resolve(relativeOutputPath).normalize()
  require(outputPath.startsWith(normalizedOutputDirectory)) {
    "Non-classpath data output path must be inside the plugin distribution: $relativeOutputPath"
  }
  return outputPath
}

private fun copyNonClasspathData(source: Path, outputPath: Path) {
  val sourceRoot = source.toRealPath()
  Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
      Files.createDirectories(outputPath.resolve(source.relativize(dir)))
      return FileVisitResult.CONTINUE
    }

    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
      val destination = outputPath.resolve(source.relativize(file))
      check(!Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
        "Cannot copy $file to $destination because the output file already exists"
      }
      if (attrs.isSymbolicLink) {
        val linkTarget = Files.readSymbolicLink(file)
        val resolvedTarget = (if (linkTarget.isAbsolute) linkTarget else file.parent.resolve(linkTarget)).toRealPath()
        check(resolvedTarget.startsWith(sourceRoot)) {
          "Cannot copy symlink $file because its target $resolvedTarget is outside the source directory $sourceRoot"
        }
        val correspondingTarget = outputPath.resolve(sourceRoot.relativize(resolvedTarget))
        Files.createSymbolicLink(destination, destination.parent.relativize(correspondingTarget))
      }
      else {
        Files.copy(file, destination, LinkOption.NOFOLLOW_LINKS, StandardCopyOption.COPY_ATTRIBUTES)
      }
      return FileVisitResult.CONTINUE
    }
  })
}
