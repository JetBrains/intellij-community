// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.LinkedHashSet

@ApiStatus.Internal
data class DevBuildComponent(
  @JvmField val root: Path,
  @JvmField val manifest: DevBuildComponentManifest,
)

@ApiStatus.Internal
data class ComposedDevBuild(
  @JvmField val platformPrefix: String,
  @JvmField val mainClass: String,
  @JvmField val additionalModules: List<String>,
  @JvmField val coreClassPath: List<String>,
  @JvmField val fingerprint: String,
)

@ApiStatus.Internal
fun composeDevBuildComponents(components: List<DevBuildComponent>, target: Path): ComposedDevBuild {
  require(components.isNotEmpty()) { "At least one dev-build component is required" }
  val first = components.first().manifest
  for ((_, manifest) in components.drop(1)) {
    check(manifest.platformPrefix == first.platformPrefix) {
      "Dev-build components have different products: '${first.platformPrefix}' and '${manifest.platformPrefix}'"
    }
    check(manifest.os == first.os && manifest.arch == first.arch) {
      "Dev-build components have different target platforms: '${first.os}/${first.arch}' and '${manifest.os}/${manifest.arch}'"
    }
    check(manifest.mainClass == first.mainClass) {
      "Dev-build components have different IDE main classes: '${first.mainClass}' and '${manifest.mainClass}'"
    }
  }

  Files.createDirectories(target)
  for ((root, _) in components) {
    mergeDevBuildComponent(root, target)
  }

  val additionalModules = LinkedHashSet<String>()
  for ((_, manifest) in components) {
    additionalModules.addAll(manifest.additionalModules)
  }
  return ComposedDevBuild(
    platformPrefix = first.platformPrefix,
    mainClass = first.mainClass,
    additionalModules = additionalModules.toList(),
    coreClassPath = components.flatMap { it.manifest.coreClassPath },
    fingerprint = computeIdeFingerprintFromComponents(components.map { it.manifest }),
  )
}

@ApiStatus.Internal
fun mergeDevBuildComponent(source: Path, target: Path) {
  mergeDevBuildComponent(source = source, target = target) { destination, file ->
    Files.copy(file, destination, StandardCopyOption.COPY_ATTRIBUTES)
  }
}

internal fun mergeDevBuildComponent(
  source: Path,
  target: Path,
  linkFile: (destination: Path, source: Path) -> Unit,
) {
  Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
      Files.createDirectories(target.resolve(source.relativize(dir).toString()))
      return FileVisitResult.CONTINUE
    }

    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
      val destination = target.resolve(source.relativize(file).toString())
      check(!Files.exists(destination)) { "Dev-build components both provide '${target.relativize(destination)}'" }
      if (Files.isSymbolicLink(file)) {
        Files.createSymbolicLink(destination, Files.readSymbolicLink(file))
      }
      else {
        try {
          linkFile(destination, file)
        }
        catch (_: IOException) {
          Files.copy(file, destination, StandardCopyOption.COPY_ATTRIBUTES)
        }
      }
      return FileVisitResult.CONTINUE
    }
  })
}
