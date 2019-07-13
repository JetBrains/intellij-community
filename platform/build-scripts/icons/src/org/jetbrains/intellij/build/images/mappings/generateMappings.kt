// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.mappings

import org.jetbrains.intellij.build.images.IconsClassGenerator
import org.jetbrains.intellij.build.images.ImageCollector
import org.jetbrains.intellij.build.images.isImage
import org.jetbrains.intellij.build.images.sync.*
import org.jetbrains.jps.model.serialization.JpsSerializationManager
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import kotlin.streams.toList

fun main() = generateMappings()

private fun generateMappings() {
  val exclusions = System.getProperty("mappings.json.exclude.paths")
                     ?.split(",")
                     ?.map(String::trim)
                   ?: emptyList()
  val context = Context()
  val mappings = (loadIdeaGeneratedIcons(context) + loadNonGeneratedIcons(context, "idea")).groupBy {
    "${it.product}#${it.set}"
  }.toSortedMap().values.flatMap {
    if (it.size > 1) {
      System.err.println("Duplicates were generated $it\nRenaming")
      it.subList(1, it.size).mapIndexed { i, duplicate ->
        Mapping(duplicate.product, "${duplicate.set}${i + 1}", duplicate.path)
      } + it.first()
    }
    else it
  }.filter { mapping ->
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
  val path = File(System.getProperty("mappings.json.path") ?: error("Specify mappings.json.path"))
  val repo = findGitRepoRoot(path)
  fun String.normalize() = replace(Regex("\\s+"), " ").trim()
  if (json.normalize() == path.readText().normalize()) {
    println("Update is not required")
  }
  else {
    val branch = System.getProperty("branch") ?: "icons-mappings-update"
    execute(repo, GIT, "checkout", "-B", branch, "origin/master")
    path.writeText(json)
    val jsonFile = path.toRelativeString(repo)
    stageFiles(listOf(jsonFile), repo)
    commitAndPush(repo, "refs/heads/$branch", "$jsonFile automatic update",
                  "MappingsUpdater", "mappings-updater-no-reply@jetbrains.com")
  }
}

private class Mapping(val product: String, val set: String, val path: String) {
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
}

private fun loadIdeaGeneratedIcons(context: Context): Collection<Mapping> {
  val home = context.devRepoDir
  val homePath = home.absolutePath
  val project = JpsSerializationManager.getInstance().loadModel(homePath, null).project
  val generator = IconsClassGenerator(home, project.modules)
  return protectStdErr {
    project.modules.parallelStream().map { module ->
      val iconsClassInfo = generator.getIconsClassInfo(module) ?: return@map null
      val imageCollector = ImageCollector(home.toPath(), iconsOnly = true, className = iconsClassInfo.className)
      val images = imageCollector.collect(module, includePhantom = true)
      if (images.isNotEmpty()) {
        val icons = images.asSequence()
          .filter { it.file != null && Icon(it.file!!.toFile()).isValid }
          .map { it.sourceRoot.file }.toSet()
        return@map when {
          icons.isEmpty() -> null
          icons.size > 1 -> error("${iconsClassInfo.className}: ${icons.joinToString()}")
          else -> Mapping("idea", iconsClassInfo.className, "idea/${icons.first().toRelativeString(home)}")
        }
      }
      else null
    }.filter(Objects::nonNull).map { it!! }.toList()
  }
}

private fun loadNonGeneratedIcons(context: Context, vararg skip: String): Collection<Mapping> {
  val iconsRepo = context.iconsRepoDir
  val toSkip = sequenceOf(*skip)
    .map(iconsRepo::resolve)
    .map(File::toString)
    .toList()
  val iconsRoots = mutableSetOf<File>()
  Files.walkFileTree(iconsRepo.toPath(), object : SimpleFileVisitor<Path>() {
    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes) =
      if (toSkip.contains(dir.toString()) || dir.fileName.toString() == ".git") {
        FileVisitResult.SKIP_SUBTREE
      }
      else super.preVisitDirectory(dir, attrs)

    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
      if (isImage(file)) iconsRoots.add(file.parent.toFile())
      return FileVisitResult.CONTINUE
    }
  })
  return iconsRoots.groupBy { product(iconsRepo, it) }.flatMap { entry ->
    val (product, roots) = entry
    val rootSet = "${product.capitalize()}Icons"
    if (roots.size == 1) {
      val path = roots.single().toRelativeString(iconsRepo)
      return@flatMap listOf(Mapping(product, rootSet, path))
    }
    roots.map { root ->
      val path = root.toRelativeString(iconsRepo)
      val set = set(root, roots, iconsRepo, product)
        .takeIf(String::isNotBlank)
        ?.let { "$rootSet.$it" }
      Mapping(product, set ?: rootSet, path)
    }.distinct()
  }
}

private fun product(iconsRepo: File, iconsDir: File): String = when {
  iconsRepo == iconsDir.parentFile -> iconsDir.name
  iconsDir.parentFile != null -> product(iconsRepo, iconsDir.parentFile)
  else -> error("Unable to determine product name for $iconsDir")
}

private val delimiters = arrayOf("/", ".", "-", "_")
private val exclusions = setOf("icons", "images",
                               "source", "src", "main", "resources",
                               "org", "jetbrains", "plugins")

private fun set(root: File, roots: Collection<File>, iconsRepo: File, product: String): String {
  val ancestors = roots.filter { it.isAncestorOf(root) }
  val parts = root.toRelativeString(iconsRepo)
    .splitToSequence(*delimiters)
    .filter(String::isNotBlank)
    .filter { it.toLowerCase() != product }
    .filter { !exclusions.contains(it.toLowerCase()) }
    .toMutableList()
  ancestors.forEach { parts -= it.toRelativeString(iconsRepo).split(*delimiters) }
  val parentPrefix = parent(root, roots, iconsRepo)
                       ?.let { set(it, roots, iconsRepo, product) }
                       ?.takeIf(String::isNotBlank)
                       ?.let { "$it." } ?: ""
  return parentPrefix + parts.asSequence()
    .distinct().filter(String::isNotBlank)
    .joinToString(separator = ".", transform = String::capitalize)
}

private fun parent(root: File?, roots: Collection<File>, iconsRepo: File): File? =
  if (root != null && root != iconsRepo) roots.firstOrNull {
    it == root.parentFile
  } ?: parent(root.parentFile, roots, iconsRepo)
  else null