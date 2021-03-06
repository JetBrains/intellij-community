// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.mappings

import org.jetbrains.intellij.build.images.IconsClassGenerator
import org.jetbrains.intellij.build.images.IntellijIconClassGeneratorConfig
import org.jetbrains.intellij.build.images.isImage
import org.jetbrains.intellij.build.images.shutdownAppScheduledExecutorService
import org.jetbrains.intellij.build.images.sync.*
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import java.util.stream.Stream
import kotlin.streams.asSequence

fun main() {
  try {
    generateMappings()
  }
  finally {
    shutdownAppScheduledExecutorService()
  }
}

/**
 * Generate icon mappings for https://github.com/JetBrains/IntelliJIcons-web-site
 */
private fun generateMappings() {
  val exclusions = System.getProperty("mappings.json.exclude.paths")
                     ?.split(",")
                     ?.map(String::trim)
                   ?: emptyList()
  val context = Context()
  val mappings = (loadIdeaGeneratedIcons(context) {
    exclusions.none { excluded ->
      path.startsWith(excluded)
    }
  }.asSequence() + loadNonGeneratedIcons(context, "idea"))
    .groupByTo(TreeMap()) { "${it.product}#${it.set}" }
    .values
    .asSequence()
    .flatMap {
      if (it.size > 1) {
        System.err.println("Duplicates were generated $it\nRenaming")
        it.subList(1, it.size).sorted().mapIndexed { i, duplicate ->
          Mapping(duplicate.product, "${duplicate.set}${i + 1}", duplicate.path)
        } + it.first()
      }
      else it
    }
    .filter { mapping ->
      exclusions.none { excluded ->
        mapping.path.startsWith(excluded)
      }
    }
  val mappingsJson = mappings.joinToString(separator = ",\n") {
    it.toString().prependIndent("     ")
  }
  val json = """
    |{
    |  "mappings": [
    |$mappingsJson
    |  ]
    |}
  """.trimMargin()
  val path = Paths.get(System.getProperty("mappings.json.path") ?: error("Specify mappings.json.path"))
  val repo = findGitRepoRoot(path)
  fun String.normalize() = replace(Regex("\\s+"), " ").trim()
  if (json.normalize() == Files.readString(path).normalize()) {
    println("Update is not required")
  }
  else {
    val branch = System.getProperty("branch") ?: "icons-mappings-update"
    execute(repo, GIT, "checkout", "-B", branch, "origin/master")
    Files.writeString(path, json)
    val jsonFile = repo.relativize(path).toString()
    stageFiles(listOf(jsonFile), repo)
    commitAndPush(repo, "refs/heads/$branch", "$jsonFile automatic update",
                  "MappingsUpdater", "mappings-updater-no-reply@jetbrains.com",
                  force = true)
  }
}

private class Mapping(val product: String, val set: String, val path: String) : Comparable<Mapping> {
  override fun toString(): String {
    val productName = when (product) {
      "kotlin", "mps" -> product
      else -> "intellij-$product"
    }
    return """
      |{
      |   "product": "$productName",
      |   "set": "$set",
      |   "src": "../IntelliJIcons/$path",
      |   "category": "icons"
      |}
    """.trimMargin()
  }

  override fun compareTo(other: Mapping) = path.compareTo(other.path)
}

private fun loadIdeaGeneratedIcons(context: Context, filter: Mapping.() -> Boolean): Stream<Mapping> {
  val home = context.devRepoDir
  val project = jpsProject(home.toString())
  val generator = IconsClassGenerator(home, project.modules)
  val config = IntellijIconClassGeneratorConfig()
  return protectStdErr {
    project.modules.parallelStream()
      .flatMap { generator.getIconClassInfo(it, moduleConfig = config.getConfigForModule(it.name)).stream() }
      .filter { it.images.isNotEmpty() }
      .flatMap { info ->
        val icons = info.images.asSequence()
          .filter { it.basicFile != null && Icon(it.basicFile!!).isValid }
          .map { Paths.get(JpsPathUtil.urlToPath(it.sourceRoot.url)) }
          .distinct()
          .map { Mapping("idea", info.className, "idea/${home.relativize(it)}") }
          .filter(filter)
          .toList()
        if (icons.size > 1) {
          error("Expected single source root for ${info.className} mapping but found: ${icons.joinToString()}")
        }
        icons.stream()
      }
  }
}

private fun loadNonGeneratedIcons(context: Context, vararg skip: String): Sequence<Mapping> {
  val iconRepo = context.iconRepoDir
  val toSkip = sequenceOf(*skip)
    .map(iconRepo::resolve)
    .map(Path::toString)
    .toList()
  val iconsRoots = mutableSetOf<Path>()
  Files.walkFileTree(iconRepo, object : SimpleFileVisitor<Path>() {
    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult? {
      return if (toSkip.contains(dir.toString()) || dir.fileName.toString() == ".git") {
        FileVisitResult.SKIP_SUBTREE
      }
      else super.preVisitDirectory(dir, attrs)
    }

    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
      if (isImage(file)) {
        iconsRoots.add(file.parent)
      }
      return FileVisitResult.CONTINUE
    }
  })
  return iconsRoots
    .groupBy { product(iconRepo, it) }
    .asSequence()
    .flatMap { entry ->
    val (product, roots) = entry
    val rootSet = "${product.capitalize()}Icons"
    if (roots.size == 1) {
      val path = roots.single().relativize(iconRepo).toString()
      return@flatMap listOf(Mapping(product, rootSet, path))
    }
    roots.map { root ->
      val path = iconRepo.relativize(root).toString()
      val set = set(root, roots, iconRepo, product)
        .takeIf(String::isNotBlank)
        ?.let { "$rootSet.$it" }
      Mapping(product, set ?: rootSet, path)
    }.distinct()
  }
}

private fun product(iconRepo: Path, iconDir: Path): String {
  return when {
    iconRepo == iconDir.parent -> iconDir.fileName.toString()
    iconDir.parent != null -> product(iconRepo, iconDir.parent)
    else -> error("Unable to determine product name for $iconDir")
  }
}

private val delimiters = arrayOf("/", ".", "-", "_")
private val exclusions = setOf("icons", "images",
                               "source", "src", "main", "resources",
                               "org", "jetbrains", "plugins")

private fun set(root: Path, roots: Collection<Path>, iconRepo: Path, product: String): String {
  val ancestors = roots.filter { it.isAncestorOf(root) }
  val parts = iconRepo.relativize(root).toString()
    .splitToSequence(*delimiters)
    .filter(String::isNotBlank)
    .filter { it.toLowerCase() != product }
    .filter { !exclusions.contains(it.toLowerCase()) }
    .toMutableList()
  ancestors.forEach { parts -= iconRepo.relativize(it).toString().split(*delimiters) }
  val parentPrefix = parent(root, roots, iconRepo)
                       ?.let { set(it, roots, iconRepo, product) }
                       ?.takeIf(String::isNotBlank)
                       ?.let { "$it." } ?: ""
  return parentPrefix + parts.asSequence()
    .distinct()
    .filter(String::isNotBlank)
    .joinToString(separator = ".", transform = String::capitalize)
}

private fun parent(root: Path?, roots: Collection<Path>, iconRepo: Path): Path? {
  if (root == null || root == iconRepo) {
    return null
  }
  return roots.firstOrNull { it == root.parent }
         ?: parent(root.parent, roots, iconRepo)
}