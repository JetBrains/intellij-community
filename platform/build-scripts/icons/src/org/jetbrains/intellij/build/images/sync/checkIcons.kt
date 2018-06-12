// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

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
 * @param codeRepoDir git repo with code + icons
 * @param iconsRepoDir git repo with icons
 * @param skipDirsPattern dir name pattern to skip unnecessary icons
 */
fun checkIcons(
  codeRepoDir: String, iconsRepoDir: String, skipDirsPattern: String?,
  loggerImpl: Consumer<String> = Consumer { println(it) },
  errorHandler: Consumer<String> = Consumer { throw IllegalStateException(it) }
) {
  logger = loggerImpl
  val icons = readIconsRepo(iconsRepoDir)
  val iconsFromCode = readCodeRepo(codeRepoDir, skipDirsPattern)
  val iconsFromCodeSize = iconsFromCode.size
  val added = mutableListOf<String>()
  val modified = mutableListOf<String>()
  val consistent = mutableListOf<String>()
  icons.forEach { icon, hash ->
    if (!iconsFromCode.containsKey(icon)) {
      added += icon
    }
    else if (hash != iconsFromCode[icon]) {
      modified += icon
    }
    else {
      consistent += icon
    }
    iconsFromCode.remove(icon)
  }
  val root = Paths.get(
    System.getProperty("teamcity.build.checkoutDir") ?: "."
  ).normalize().toAbsolutePath().toString() + "/"
  val newIcons = iconsFromCode.keys.size
  val dumpInfo = dumpToFile(iconsFromCode.keys, "added", "dev_repo") +
                 dumpToFile(added, "added", "icons_repo") +
                 dumpToFile(modified, "modified", "icons_repo")
  val report = """
    |$iconsFromCodeSize icons are found in ${codeRepoDir.removePrefix(root)}
    | skipped ${skippedDirs.size} dirs
    | $newIcons new icons
    |${icons.size} icons are found in ${iconsRepoDir.removePrefix(root)}
    | ${added.size} new icons
    | ${modified.size} modified icons
    |${consistent.size} consistent icons in both repos
    |$dumpInfo
    """.trimMargin()
  if (newIcons > 0 || added.size > 0 || modified.size > 0) {
    errorHandler.accept(report)
  }
  else {
    log(report)
  }
}

private fun dumpToFile(icons: Collection<String>, name: String, repo: String) =
  if (icons.isNotEmpty()) {
    val output = "${repo}_${name}.txt"
    File(output).writeText(icons.joinToString(System.lineSeparator()))
    "See $output${System.lineSeparator()}"
  }
  else {
    ""
  }

private fun readIconsRepo(iconsRepoDir: String) =
  listGitObjects(findGitRepoRoot(iconsRepoDir), iconsRepoDir) {
    // read icon hashes
    isIcon(it)
  }

private fun readCodeRepo(codeRepoDir: String, skipDirsPattern: String?): MutableMap<String, String> {
  val codeRepoRoot = findGitRepoRoot(codeRepoDir)
  val testRoots = searchTestRoots(codeRepoRoot.absolutePath)
  log("Found ${testRoots.size} test roots")
  if (skipDirsPattern != null) log("Using pattern $skipDirsPattern to skip dirs")
  val skipDirsRegex = skipDirsPattern?.toRegex()
  val codeRepoIconFilter = { file: File ->
    // read icon hashes skipping test roots
    isIcon(file) && !inTestRoot(file, testRoots, skipDirsRegex)
  }
  val codeRepoVcsRoots = vcsRoots(codeRepoRoot)
  val iconsFromCode = if (codeRepoVcsRoots.size == 1
                          && codeRepoVcsRoots.contains(codeRepoRoot)) {
    // read icons from codeRepoRoot
    listGitObjects(codeRepoRoot, codeRepoDir, codeRepoIconFilter)
  }
  else {
    // read icons from multiple repos in codeRepoRoot
    listGitObjects(codeRepoRoot, codeRepoVcsRoots, codeRepoIconFilter)
  }
  return iconsFromCode.toMutableMap()
}

private fun searchTestRoots(codeRepoDir: String) = try {
  JpsSerializationManager.getInstance()
    .loadModel(codeRepoDir, null)
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
    org.jetbrains.intellij.build.images.isIcon(
      file, if (file.name.contains("@2x")) 64 else 32
    )
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
