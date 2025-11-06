// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.openapi.application.PathManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.jetbrains.intellij.build.productLayout.DuplicateIncludeDetector
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.walk

/**
 * Command-line tool to detect duplicate xi:include elements in product plugin.xml files.
 * 
 * Usage:
 *   bazel run //community/platform/build-scripts:DuplicateIncludeAnalyzer
 * 
 * Output:
 *   JSON report showing products with duplicate includes, where each duplicate comes from,
 *   and summary statistics.
 */
object DuplicateIncludeAnalyzer {
  @JvmStatic
  fun main(args: Array<String>) {
    runBlocking(Dispatchers.Default) {
      val projectRoot = Path.of(PathManager.getHomePathFor(DuplicateIncludeAnalyzer::class.java)!!)
      
      // Discover all product plugin.xml files
      val productFiles = discoverProductFiles(projectRoot)
      
      // Run detection
      val report = DuplicateIncludeDetector.detectDuplicates(productFiles, projectRoot)
      
      // Output JSON
      val json = Json { prettyPrint = true }
      println(json.encodeToString(report))
    }
  }
  
  /**
   * Discovers all product plugin.xml files in the project.
   * Searches in known product directories and looks for files ending with Plugin.xml or named plugin.xml.
   */
  private fun discoverProductFiles(projectRoot: Path): List<Path> {
    val productFiles = mutableListOf<Path>()
    
    // Known product directories to search
    val productDirs = listOf(
      projectRoot.resolve("community"),
      projectRoot.resolve("ultimate"),
      projectRoot.resolve("CIDR"),
      projectRoot.resolve("goland"),
      projectRoot.resolve("ruby"),
      projectRoot.resolve("WebStorm"),
      projectRoot.resolve("dbe"),
      projectRoot.resolve("aqua"),
      projectRoot.resolve("rider"),
      projectRoot.resolve("python"),
      projectRoot.resolve("plugins"),
    )
    
    for (dir in productDirs) {
      if (!dir.exists() || !dir.isDirectory()) {
        continue
      }
      
      // Search for Plugin.xml files in resources/META-INF directories
      dir.walk()
        .filter { it.isRegularFile() }
        .filter { it.parent?.fileName?.toString() == "META-INF" }
        .filter { 
          val name = it.fileName.toString()
          name.endsWith("Plugin.xml") || name == "plugin.xml"
        }
        .filter { isProductFile(it) }
        .forEach { productFiles.add(it) }
    }
    
    return productFiles.distinct()
  }
  
  /**
   * Checks if an XML file is a product descriptor (not a plugin descriptor).
   * Products don't have their own <id> tag or have <id>com.intellij</id>.
   * Also filters out test files and non-product descriptors.
   */
  private fun isProductFile(file: Path): Boolean {
    // Skip test resources
    if (file.toString().contains("/testResources/") || 
        file.toString().contains("/testSrc/") ||
        file.toString().contains("/test/")) {
      return false
    }
    
    // Skip toolbox
    if (file.toString().contains("/toolbox/")) {
      return false
    }
    
    val fileName = file.fileName.toString()
    
    // Skip module descriptor files (have dots in the name like intellij.platform.jewel.detektPlugin.xml)
    if (fileName != "plugin.xml" && fileName.contains(".") && !fileName.matches(Regex("^[A-Z][a-zA-Z]*Plugin\\.xml$"))) {
      return false
    }
    
    try {
      val content = file.readText()
      
      // Check for <id> tag that's NOT com.intellij
      val idMatch = Regex("""<id>([^<]+)</id>""").find(content)
      if (idMatch != null && idMatch.groupValues[1] != "com.intellij") {
        // This is a plugin with its own ID, not a product
        return false
      }
      
      // Check if it has ApplicationInfo.xml nearby (strong indicator of a product)
      val resourceRoot = file.parent?.parent // Go up from META-INF to resources
      if (resourceRoot != null) {
        val ideaDir = resourceRoot.resolve("idea")
        if (ideaDir.exists() && ideaDir.isDirectory()) {
          val hasAppInfo = ideaDir.listDirectoryEntries()
            .any { it.fileName.toString().endsWith("ApplicationInfo.xml") }
          if (hasAppInfo) {
            return true
          }
        }
      }
      
      // If no <id> tag or has com.intellij, likely a product
      return true
    }
    catch (e: Exception) {
      return false
    }
  }
}
