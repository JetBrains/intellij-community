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
import kotlin.io.path.exists
import kotlin.io.path.name

fun main() {
  try {
    generateMappings()
  }
  finally {
    shutdownAppScheduledExecutorService()
  }
}

private val context = Context()

private object Exclusions {
  val paths: List<Path> = System.getProperty("mappings.json.exclude.paths", "")
    .splitToSequence(",")
    .map { context.devRepoDir.resolve(it.trim()) }
    .toList()

  val unmatched: MutableList<Path> = paths.toMutableList()

  fun match(image: Path): Boolean =
    paths.none {
      val excluded = image.startsWith(it)
      if (excluded) unmatched.remove(it)
      excluded
    }
}

/**
 * Generate icon mappings for https://github.com/JetBrains/IntelliJIcons-web-site
 */
private fun generateMappings() {
  val mappings = (loadIdeaGeneratedIcons() + loadNonGeneratedIcons()).groupByTo(TreeMap()) {
    "${it.product}#${it.set}"
  }.entries.asSequence().flatMap { (key, mappings) ->
    if (mappings.size > 1) {
      System.err.println(
        mappings.joinToString(
          prefix = "Duplicates for $key were generated:\n\t",
          separator = "\n\t", postfix = "\nRenamed."
        ) { it.path }
      )
      mappings.subList(1, mappings.size).sorted().mapIndexed { i, duplicate ->
        Mapping(duplicate.product, "${duplicate.set}${i + 1}", duplicate.path)
      } + mappings.first()
    }
    else mappings
  }.toList()
  check(mappings.isNotEmpty()) {
    "No mappings loaded"
  }
  check(Exclusions.unmatched.isEmpty()) {
    "Nothing matched to exclusions: ${Exclusions.unmatched}"
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
  fun exists(): Boolean {
    return context.iconRepoDir.resolve(path).exists()
  }

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

private fun loadIdeaGeneratedIcons(): Sequence<Mapping> {
  val home = context.devRepoDir
  val project = jpsProject(home.toString())
  val generator = IconsClassGenerator(home, project.modules)
  val config = IntellijIconClassGeneratorConfig()
  return protectStdErr {
    project.modules.asSequence()
      .flatMap { generator.getIconClassInfo(it, moduleConfig = config.getConfigForModule(it.name)) }
      .filter { it.images.isNotEmpty() }
      .flatMap { info ->
        val icons = info.images.asSequence()
          .filter { it.basicFile?.let(::Icon)?.isValid == true }
          .filter { Exclusions.match(checkNotNull(it.basicFile)) }
          .map { Paths.get(JpsPathUtil.urlToPath(it.sourceRoot.url)) }
          .distinct()
          .map { Mapping("idea", info.className, "idea/${home.relativize(it)}") }
          .filter { it.exists() }
          .toList()
        check(icons.size < 2) {
          "Expected single source root for ${info.className} mapping but found: ${icons.joinToString()}"
        }
        icons
      }
  }
}

private fun loadNonGeneratedIcons(): Sequence<Mapping> {
  val iconRepo = context.iconRepoDir
  val toSkip = iconRepo.resolve("idea")
  val iconsRoots = mutableSetOf<Path>()
  Files.walkFileTree(iconRepo, object : SimpleFileVisitor<Path>() {
    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult? {
      return if (dir.startsWith(toSkip) || dir.name == ".git") {
        FileVisitResult.SKIP_SUBTREE
      }
      else super.preVisitDirectory(dir, attrs)
    }

    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
      if (isImage(file) && Exclusions.match(file)) {
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
      val rootSet = "${product.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}Icons"
      if (roots.size == 1) {
        val path = iconRepo.relativize(roots.single()).toString()
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
    iconRepo == iconDir.parent -> iconDir.name
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
    .filter { it.lowercase() != product }
    .filter { !exclusions.contains(it.lowercase()) }
    .toMutableList()
  ancestors.forEach {
    parts -= iconRepo.relativize(it).toString().splitToSequence(*delimiters).toSet()
  }
  val parentPrefix = parent(root, roots, iconRepo)
                       ?.let { set(it, roots, iconRepo, product) }
                       ?.takeIf(String::isNotBlank)
                       ?.let { "$it." } ?: ""
  return parentPrefix + parts.asSequence()
    .distinct()
    .filter(String::isNotBlank)
    .joinToString(separator = ".") {
      it.replaceFirstChar { char ->
        if (char.isLowerCase()) char.titlecase() else char.toString()
      }
    }
}

private fun parent(root: Path?, roots: Collection<Path>, iconRepo: Path): Path? {
  if (root == null || root == iconRepo) {
    return null
  }
  return roots.firstOrNull { it == root.parent }
         ?: parent(root.parent, roots, iconRepo)
}