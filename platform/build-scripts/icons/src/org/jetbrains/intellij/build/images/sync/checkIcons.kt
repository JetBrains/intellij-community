// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.intellij.build.images.imageSize
import org.jetbrains.intellij.build.images.isImage
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.serialization.JpsSerializationManager
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.PrintStream
import java.util.function.Consumer
import java.util.stream.Collectors

private const val repoArg = "repos"
private const val patternArg = "skip.dirs.pattern"
private const val syncIcons = "sync.icons"
private const val syncDevIcons = "sync.dev.icons"
private const val syncRemovedIconsInDev = "sync.dev.icons.removed"

fun main(args: Array<String>) {
  if (args.isEmpty()) printUsageAndExit()
  val repos = args.find(repoArg)?.split(",") ?: emptyList()
  if (repos.size < 2) printUsageAndExit()
  val skipPattern = args.find(patternArg)
  checkIcons(ignoreCaseInDirName(repos[0]), ignoreCaseInDirName(repos[1]), skipPattern,
             args.find(syncIcons)?.toBoolean() ?: false,
             args.find(syncDevIcons)?.toBoolean() ?: false,
             args.find(syncRemovedIconsInDev)?.toBoolean() ?: true)
}

private fun ignoreCaseInDirName(path: String) = File(path).let {
  it.parentFile.listFiles().first {
    it.absolutePath.equals(FileUtil.toSystemDependentName(path), ignoreCase = true)
  }.absolutePath
}

private fun Array<String>.find(arg: String) = this.find {
  it.startsWith("$arg=")
}?.removePrefix("$arg=")

private fun printUsageAndExit() {
  println("""
    |Usage: $repoArg=<devRepoDir>,<iconsRepoDir> [$patternArg=...] [$syncIcons=false|true] [$syncDevIcons=false|true]
    |
    |* `$repoArg` - comma-separated repository paths, first is developers' repo, second is designers'
    |* `$patternArg` - test data folders regular expression
    |* `$syncDevIcons` - update icons in developers' repo. Switch off to run check only
    |* `$syncIcons` - update icons in designers' repo. Switch off to run check only
    |* `$syncRemovedIconsInDev` - remove icons in developers' repo removed by designers
  """.trimMargin())
  System.exit(1)
}

/**
 * @param devRepoDir developers' git repo
 * @param iconsRepoDir designers' git repo
 * @param skipDirsPattern dir name pattern to skip unnecessary icons
 */
fun checkIcons(
  devRepoDir: String, iconsRepoDir: String, skipDirsPattern: String?,
  doSyncIconsRepo: Boolean = false, doSyncDevRepo: Boolean = false, doSyncRemovedIconsInDev: Boolean = true,
  loggerImpl: Consumer<String> = Consumer { println(it) },
  errorHandler: Consumer<String> = Consumer { throw IllegalStateException(it) }
) {
  logger = loggerImpl
  val iconsRepo = findGitRepoRoot(iconsRepoDir)
  val icons = readIconsRepo(iconsRepo, iconsRepoDir)
  val devRepoRoot = findGitRepoRoot(devRepoDir)
  val devRepoVcsRoots = vcsRoots(devRepoRoot)
  val devIcons = readDevRepo(devRepoRoot, devRepoDir, devRepoVcsRoots, skipDirsPattern)
  val devIconsBackup = HashMap(devIcons)
  val addedByDesigners = mutableListOf<String>()
  val modified = mutableListOf<String>()
  val consistent = mutableListOf<String>()
  icons.forEach { icon, gitObject ->
    if (!devIcons.containsKey(icon)) {
      addedByDesigners += icon
    }
    else if (gitObject.hash != devIcons[icon]?.hash) {
      modified += icon
    }
    else {
      consistent += icon
    }
    devIcons.remove(icon)
  }
  val addedByDev = devIcons.keys
  val modifiedByDev = callWithTimer("Searching for modified by developers") {
    modifiedByDev(modified, icons, devIconsBackup)
  }
  val removedByDev = callWithTimer("Searching for removed by developers") {
    removedByDev(addedByDesigners, icons, devRepoVcsRoots, File(devRepoDir))
  }
  val modifiedByDesigners = modified.filter { !modifiedByDev.contains(it) }
  val removedByDesigners = callWithTimer("Searching for removed by designers") {
    removedByDesigners(
      addedByDev, devIconsBackup, iconsRepo,
      File(iconsRepoDir).relativeTo(iconsRepo).path.let {
        if (it.isEmpty()) "" else "$it/"
      }
    )
  }
  if (doSyncIconsRepo) {
    syncAdded(addedByDev, devIconsBackup, File(iconsRepoDir)) { iconsRepo }
    syncModified(modifiedByDev, icons, devIconsBackup)
    syncRemoved(removedByDev, icons)
  }
  if (doSyncDevRepo) {
    syncAdded(addedByDesigners, icons, File(devRepoDir)) { findGitRepoRoot(it.absolutePath, true) }
    syncModified(modifiedByDesigners, devIconsBackup, icons)
    if (doSyncRemovedIconsInDev) syncRemoved(removedByDesigners, devIconsBackup)
  }
  report(
    devIconsBackup.size, icons.size, skippedDirs.size,
    addedByDev, removedByDev, modifiedByDev,
    addedByDesigners, removedByDesigners, modifiedByDesigners,
    consistent, errorHandler, !doSyncIconsRepo && !doSyncDevRepo
  )
}

private fun readIconsRepo(iconsRepo: File, iconsRepoDir: String) =
  listGitObjects(iconsRepo, iconsRepoDir) {
    // read icon hashes
    isIcon(it)
  }.also {
    if (it.isEmpty()) throw IllegalStateException("Icons repo doesn't contain icons")
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
    !inTestRoot(file, testRoots, skipDirsRegex) && isIcon(file)
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
  if (devIcons.isEmpty()) throw IllegalStateException("Dev repo doesn't contain icons")
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

private fun isIcon(file: File): Boolean {
  val err = System.err
  System.setErr(mutedStream)
  return try {
    // image
    isImage(file) && imageSize(file)?.let { size ->
      val pixels = if (file.name.contains("@2x")) 64 else 32
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
  devIcons: Map<String, GitObject>,
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
  icons: Map<String, GitObject>,
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
    if (file.startsWith(repo.absolutePath)) {
      val lct = latestChangeTime(file.removePrefix("${repo.absolutePath}/"), repo)
      if (lct > 0) return lct
    }
  }
  return -1
}

private fun modifiedByDev(
  modified: Collection<String>,
  icons: Map<String, GitObject>,
  devIcons: Map<String, GitObject>) = modified.parallelStream()
  // latest changes are made by developers
  .filter { latestChangeTime(icons[it]) < latestChangeTime(devIcons[it]) }
  .collect(Collectors.toList())

private fun latestChangeTime(obj: GitObject?) =
  latestChangeTime(obj!!.file, obj.repo).also {
    if (it <= 0) throw IllegalStateException(obj.toString())
  }