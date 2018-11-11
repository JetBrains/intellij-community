// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import org.jetbrains.intellij.build.images.imageSize
import org.jetbrains.intellij.build.images.isImage
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.serialization.JpsSerializationManager
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.PrintStream
import java.nio.file.Path
import java.util.function.Consumer
import java.util.stream.Collectors

private lateinit var icons: Map<String, GitObject>
private lateinit var devIcons: Map<String, GitObject>

fun main(args: Array<String>) = try {
  checkIcons()
}
catch (e: Throwable) {
  log(e.message ?: e::class.java.name)
  e.printStackTrace()
}

internal fun checkIcons(context: Context = Context(),
                        loggerImpl: Consumer<String> = Consumer { println(it) },
                        errorHandler: Consumer<String> = Consumer { error(it) }) {
  logger = loggerImpl
  context.iconsRepo = findGitRepoRoot(context.iconsRepoDir)
  context.errorHandler = errorHandler
  icons = readIconsRepo(context.iconsRepo, context.iconsRepoDir)
  val devRepoRoot = findGitRepoRoot(context.devRepoDir)
  val devRepoVcsRoots = vcsRoots(devRepoRoot)
  devIcons = readDevRepo(devRepoRoot, context.devRepoDir, devRepoVcsRoots, context.skipDirsPattern)
  val devIconsTmp = HashMap(devIcons)
  val modified = mutableListOf<String>()
  val consistent = mutableListOf<String>()
  icons.forEach { icon, gitObject ->
    if (!devIconsTmp.containsKey(icon)) {
      context.addedByDesigners += icon
    }
    else if (gitObject.hash != devIconsTmp[icon]?.hash) {
      modified += icon
    }
    else {
      consistent += icon
    }
    devIconsTmp.remove(icon)
  }
  context.addedByDev = devIconsTmp.keys
  context.modifiedByDev = callWithTimer("Searching for modified by developers") {
    modifiedByDev(modified)
  }
  context.removedByDev = callWithTimer("Searching for removed by developers") {
    removedByDev(context.addedByDesigners, devRepoVcsRoots, File(context.devRepoDir))
  }
  context.modifiedByDesigners = modified.filter { !context.modifiedByDev.contains(it) }.toMutableList()
  context.removedByDesigners = callWithTimer("Searching for removed by designers") {
    removedByDesigners(context.addedByDev, context.iconsRepo, File(context.iconsRepoDir).relativeTo(context.iconsRepo).path.let {
      if (it.isEmpty()) "" else "$it/"
    })
  }
  syncIcons(context, devIcons, icons)
  report(context, devRepoRoot, devIcons.size, icons.size, skippedDirs.size, consistent)
}

private fun readIconsRepo(iconsRepo: File, iconsRepoDir: String) =
  listGitObjects(iconsRepo, iconsRepoDir) {
    // read icon hashes
    isValidIcon(it.toPath())
  }.also {
    if (it.isEmpty()) error("Icons repo doesn't contain icons")
  }

private fun readDevRepo(devRepoRoot: File,
                        devRepoDir: String,
                        devRepoVcsRoots: List<File>,
                        skipDirsPattern: String?
): MutableMap<String, GitObject> {
  val testRoots = searchTestRoots(devRepoRoot.absolutePath)
  log("Found ${testRoots.size} test roots")
  if (skipDirsPattern != null) log("Using pattern $skipDirsPattern to skip dirs")
  val skipDirsRegex = skipDirsPattern?.toRegex()
  val devRepoIconFilter = { file: File ->
    // read icon hashes skipping test roots
    !inTestRoot(file, testRoots, skipDirsRegex) && isValidIcon(file.toPath())
  }
  val devIcons = if (devRepoVcsRoots.size == 1
                     && devRepoVcsRoots.contains(devRepoRoot)) {
    // read icons from devRepoRoot
    listGitObjects(devRepoRoot, devRepoDir, devRepoIconFilter)
  }
  else {
    // read icons from multiple repos in devRepoRoot
    listGitObjects(devRepoRoot, devRepoVcsRoots, devRepoIconFilter)
  }
  if (devIcons.isEmpty()) error("Dev repo doesn't contain icons")
  return devIcons.toMutableMap()
}

private fun searchTestRoots(devRepoDir: String) = try {
  JpsSerializationManager.getInstance()
    .loadModel(devRepoDir, null)
    .project.modules.flatMap {
    it.getSourceRoots(JavaSourceRootType.TEST_SOURCE) +
    it.getSourceRoots(JavaResourceRootType.TEST_RESOURCE)
  }.mapTo(mutableSetOf()) { it.file }
}
catch (e: IOException) {
  log(e.message!!)
  emptySet<File>()
}

private val mutedStream = PrintStream(object : OutputStream() {
  override fun write(b: ByteArray) {}

  override fun write(b: ByteArray, off: Int, len: Int) {}

  override fun write(b: Int) {}
})

private fun isValidIcon(file: Path): Boolean {
  val err = System.err
  System.setErr(mutedStream)
  return try {
    // image
    isImage(file) && imageSize(file)?.let { size ->
      val pixels = if (file.fileName.toString().contains("@2x")) 64 else 32
      // small
      size.height <= pixels && size.width <= pixels
    } ?: false
  }
  catch (e: Exception) {
    log("WARNING: $file: ${e.message}")
    false
  }
  finally {
    System.setErr(err)
  }
}

private val skippedDirs = mutableSetOf<File>()

private fun inTestRoot(file: File, testRoots: Set<File>, skipDirsRegex: Regex?): Boolean {
  val inTestRoot = file.isDirectory &&
                   // is test root
                   (testRoots.contains(file) ||
                    // or matches pattern
                    skipDirsRegex != null && file.name.matches(skipDirsRegex))
  if (inTestRoot) skippedDirs += file
  return inTestRoot
         // or check parent
         || file.parentFile != null && inTestRoot(file.parentFile, testRoots, skipDirsRegex)
}

private fun removedByDesigners(
  addedByDev: MutableCollection<String>,
  iconsRepo: File, iconsDir: String) = addedByDev.parallelStream()
  .filter {
    val latestChangeTimeByDesigners = latestChangeTime("$iconsDir$it", iconsRepo)
    // latest changes are made by designers
    latestChangeTimeByDesigners > 0 && latestChangeTime(devIcons[it]) < latestChangeTimeByDesigners
  }.collect(Collectors.toList()).also {
    addedByDev.removeAll(it)
  }

private fun removedByDev(
  addedByDesigners: MutableCollection<String>,
  devRepos: Collection<File>, devRepoDir: File) = addedByDesigners.parallelStream()
  .filter { path ->
    val latestChangeTimeByDev = latestChangeTime(File(devRepoDir, path).absolutePath, devRepos)
    // latest changes are made by developers
    latestChangeTimeByDev > 0 && latestChangeTime(icons[path]) < latestChangeTimeByDev
  }.collect(Collectors.toList()).also {
    addedByDesigners.removeAll(it)
  }

private fun latestChangeTime(file: String, repos: Collection<File>): Long {
  for (repo in repos) {
    val prefix = "${repo.absolutePath}/"
    if (file.startsWith(prefix)) {
      val lct = latestChangeTime(file.removePrefix(prefix), repo)
      if (lct > 0) return lct
    }
  }
  return -1
}

private fun modifiedByDev(modified: Collection<String>) = modified.parallelStream()
  // latest changes are made by developers
  .filter { latestChangeTime(icons[it]) < latestChangeTime(devIcons[it]) }
  .collect(Collectors.toList())

private fun latestChangeTime(obj: GitObject?) =
  latestChangeTime(obj!!.path, obj.repo).also {
    if (it <= 0) error(obj.toString())
  }