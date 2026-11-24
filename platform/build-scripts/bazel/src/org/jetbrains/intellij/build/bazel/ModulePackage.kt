// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.moveTo
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.writeText

/** The label of the `.iml` file of [module], which the BUILD file of the module exports. */
internal fun imlTargetLabel(module: ModuleDescriptor, communityRoot: Path, ultimateRoot: Path?): String {
  val relativeImlPath = module.imlFile.relativeTo(module.bazelBuildFileDir).invariantSeparatorsPathString
  return "${bazelPackagePrefix(module = module, communityRoot = communityRoot, ultimateRoot = ultimateRoot)}:$relativeImlPath"
}

/**
 * The Bazel package of one module, as far as a file label needs it.
 *
 * A file gives a label only when it exists, when it is in [directory], and when it is on the repository half of the
 * module. An ultimate module can keep its resources in the community tree, as `dotenv-ultimate` and `php.dev` do.
 * `community` is a `.bazelignore` entry of the ultimate root, so a main-repository label never materializes such a file.
 */
internal class ModulePackage(
  @JvmField val directory: Path,
  /** The `@community//pkg` or `//pkg` prefix of a label in the package, see [bazelPackagePrefix]. */
  @JvmField val labelPrefix: String,
  @JvmField val isCommunity: Boolean,
  @JvmField val communityRoot: Path,
) {
  fun fileLabel(file: Path): String? {
    val normalized = file.normalize()
    if (!normalized.exists() || normalized.startsWith(communityRoot) != isCommunity || !normalized.startsWith(directory)) {
      return null
    }
    return "$labelPrefix:${directory.relativize(normalized).invariantSeparatorsPathString}"
  }

  /** Whether [file] exists on the repository half of the module but outside [directory], so it gives no label. */
  fun isOutsideOnItsHalf(file: Path): Boolean {
    val normalized = file.normalize()
    return normalized.exists() && normalized.startsWith(communityRoot) == isCommunity && !normalized.startsWith(directory)
  }
}

internal fun modulePackage(module: ModuleDescriptor, communityRoot: Path, ultimateRoot: Path?): ModulePackage {
  return ModulePackage(
    directory = module.bazelBuildFileDir,
    labelPrefix = bazelPackagePrefix(module = module, communityRoot = communityRoot, ultimateRoot = ultimateRoot),
    isCommunity = module.isCommunity,
    communityRoot = communityRoot,
  )
}

/**
 * The label of `<module name>.xml` at the first production resource root of [module] that holds it, or `null`.
 *
 * A root with a relative output path does not count, because the jar maps its files into another directory. The file
 * must give a label in the package of the module, see [ModulePackage.fileLabel].
 */
internal fun moduleDescriptorLabel(module: ModuleDescriptor, communityRoot: Path, ultimateRoot: Path?): String? {
  return moduleDescriptorLabel(moduleName = module.module.name, resources = module.resources, modulePackage = modulePackage(module, communityRoot, ultimateRoot))
}

internal fun moduleDescriptorLabel(moduleName: String, resources: List<ResourceDescriptor>, modulePackage: ModulePackage): String? {
  return resources.asSequence()
    .filter { it.relativeOutputPath.isEmpty() }
    .firstNotNullOfOrNull { modulePackage.fileLabel(it.root.resolve("$moduleName.xml")) }
}

/**
 * The labels of the conventional descriptors of [module]: `<module name>.xml` and `META-INF/plugin.xml` at each
 * production resource root. A file gives a label only in the package of the module, see [ModulePackage.fileLabel].
 *
 * A descriptor on the half of the module but outside its package gives a warning, in both converters. The model of
 * today has none. A file on the other half gives no warning, because `dotenv-ultimate` and `php.dev` keep theirs there.
 */
internal fun conventionalDescriptorLabels(module: ModuleDescriptor, communityRoot: Path, ultimateRoot: Path?): List<String> {
  val modulePackage = modulePackage(module, communityRoot, ultimateRoot)
  val result = ArrayList<String>()
  for (resource in module.resources) {
    for (candidate in listOf("${module.module.name}.xml", "META-INF/plugin.xml")) {
      val file = resource.root.resolve(candidate)
      val label = modulePackage.fileLabel(file)
      if (label != null) {
        result.add(label)
      }
      else if (modulePackage.isOutsideOnItsHalf(file)) {
        println("WARN: the descriptor $file of module ${module.module.name} is outside the package ${modulePackage.directory}, so it gets no label")
      }
    }
  }
  return result
}

/** Writes [content] to [file] unless the file already holds it. Returns whether the file changed. */
internal fun writeGeneratedFile(file: Path, content: String): Boolean {
  if (file.isRegularFile() && file.readText() == content) {
    return false
  }

  file.parent.createDirectories()
  val tempFile = Files.createTempFile(file.parent, file.fileName.toString(), ".tmp")
  try {
    tempFile.writeText(content)
    tempFile.moveTo(file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
  }
  finally {
    tempFile.deleteIfExists()
  }
  return true
}
