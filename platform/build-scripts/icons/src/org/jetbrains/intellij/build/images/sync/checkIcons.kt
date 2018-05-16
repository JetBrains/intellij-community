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
import java.nio.file.Paths
import java.util.function.Consumer

private val skippedDirs = mutableSetOf<File>()

/**
 * @param devRepoDir developers' git repo
 * @param iconsRepoDir designers' git repo
 * @param skipDirsPattern dir name pattern to skip unnecessary icons
 */
fun checkIcons(
  devRepoDir: String, iconsRepoDir: String,
  skipDirsPattern: String?, doSync: Boolean = false,
  loggerImpl: Consumer<String> = Consumer { println(it) },
  errorHandler: Consumer<String> = Consumer { throw IllegalStateException(it) }
) {
  logger = loggerImpl
  val icons = readIconsRepo(iconsRepoDir)
  val devIcons = readDevRepo(devRepoDir, skipDirsPattern)
  val devIconsBackup = HashMap(devIcons)
  val added = mutableListOf<String>()
  val modified = mutableListOf<String>()
  val consistent = mutableListOf<String>()
  icons.forEach { icon, gitObject ->
    if (!devIcons.containsKey(icon)) {
      added += icon
    }
    else if (gitObject.hash != devIcons[icon]?.hash) {
      modified += icon
    }
    else {
      consistent += icon
    }
    devIcons.remove(icon)
  }
  val root = Paths.get(
    System.getProperty("teamcity.build.checkoutDir") ?: "."
  ).normalize().toAbsolutePath().toString() + "/"
  val addedByDev = devIcons.keys
  val modifiedByDev = modifiedByDev(modified, icons, devIconsBackup)
  val report = """
    |${devIconsBackup.size} icons are found in ${devRepoDir.removePrefix(root)}
    | skipped ${skippedDirs.size} dirs
    | ${addedByDev.size} added icons
    | ${modifiedByDev.size} modified icons
    |${icons.size} icons are found in ${iconsRepoDir.removePrefix(root)}
    | ${added.size} added icons
    | ${modified.size - modifiedByDev.size} modified icons
    |${consistent.size} consistent icons in both repos
  """.trimMargin()
  if (addedByDev.isEmpty() && modifiedByDev.isEmpty()) {
    log(report)
  }
  else if (doSync) {
    doSync(addedByDev, modifiedByDev, icons, devIconsBackup, iconsRepoDir)
    log(report)
  }
  else {
    log(report)
    errorHandler.accept(report)
  }
}

private fun modifiedByDev(
  modified: Collection<String>,
  icons: Map<String, GitObject>,
  devIcons: Map<String, GitObject>) = modified.filter {
  val iconsRepoLatestChangeTime = icons[it]!!.let { latestChangeTime(it.file, it.repo) }
  val devRepoLatestChangeTime = devIcons[it]!!.let { latestChangeTime(it.file, it.repo) }
  iconsRepoLatestChangeTime < devRepoLatestChangeTime
}

private fun readIconsRepo(iconsRepoDir: String) =
  listGitObjects(findGitRepoRoot(iconsRepoDir), iconsRepoDir) {
    // read icon hashes
    isIcon(it)
  }

private fun readDevRepo(devRepoDir: String, skipDirsPattern: String?): MutableMap<String, GitObject> {
  val devRepoRoot = findGitRepoRoot(devRepoDir)
  val testRoots = searchTestRoots(devRepoRoot.absolutePath)
  log("Found ${testRoots.size} test roots")
  if (skipDirsPattern != null) log("Using pattern $skipDirsPattern to skip dirs")
  val skipDirsRegex = skipDirsPattern?.toRegex()
  val devRepoIconFilter = { file: File ->
    // read icon hashes skipping test roots
    isIcon(file) && !inTestRoot(file, testRoots, skipDirsRegex)
  }
  val devRepoVcsRoots = vcsRoots(devRepoRoot)
  val devIcons = if (devRepoVcsRoots.size == 1
                     && devRepoVcsRoots.contains(devRepoRoot)) {
    // read icons from devRepoRoot
    listGitObjects(devRepoRoot, devRepoDir, devRepoIconFilter)
  }
  else {
    // read icons from multiple repos in devRepoRoot
    listGitObjects(devRepoRoot, devRepoVcsRoots, devRepoIconFilter)
  }
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
