// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout

import com.intellij.openapi.util.JDOMUtil
import kotlinx.serialization.Serializable
import org.jdom.Element
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/**
 * Detects duplicate xi:include references in product plugin.xml files.
 * Analyzes both direct xi:includes and nested includes from deprecatedInclude XML files.
 */
object DuplicateIncludeDetector {
  
  /**
   * Detects duplicate xi:include references across all provided product XML files.
   * 
   * @param productXmlFiles List of product plugin.xml file paths to analyze
   * @param projectRoot Project root directory for resolving relative paths
   * @return Report containing all detected duplicates
   */
  fun detectDuplicates(
    productXmlFiles: List<Path>,
    projectRoot: Path
  ): DuplicateIncludesReport {
    val productsWithDuplicates = mutableListOf<ProductDuplicates>()
    
    for (productFile in productXmlFiles) {
      if (!productFile.exists() || !productFile.isRegularFile()) {
        continue
      }
      
      val allIncludes = mutableListOf<Pair<String, IncludeSource>>()
      
      // 1. Parse direct xi:includes from product file
      val directIncludes = parseXiIncludes(productFile)
      for (include in directIncludes) {
        allIncludes.add(include.href to IncludeSource(
          file = productFile.toString(),
          sourceType = "xi:include",
          lineNumber = include.lineNumber
        ))
        
        // 2. If this xi:include points to a potential deprecatedInclude XML file, parse it too
        val resolvedPath = resolveIncludePath(include.href, productFile, projectRoot)
        if (resolvedPath != null && resolvedPath.exists() && isDeprecatedIncludeFile(resolvedPath)) {
          val nestedIncludes = parseXiIncludes(resolvedPath)
          for (nested in nestedIncludes) {
            allIncludes.add(nested.href to IncludeSource(
              file = resolvedPath.toString(),
              sourceType = "deprecatedInclude-nested",
              lineNumber = nested.lineNumber
            ))
          }
        }
      }
      
      // 3. Find duplicates
      val grouped = allIncludes.groupBy { it.first }
      val duplicates = grouped.filter { it.value.size > 1 }.map { (href, sources) ->
        DuplicateInclude(
          href = href,
          resolvedPath = resolveIncludePath(href, productFile, projectRoot)?.toString(),
          count = sources.size,
          sources = sources.map { it.second }
        )
      }
      
      if (duplicates.isNotEmpty()) {
        productsWithDuplicates.add(ProductDuplicates(
          name = productFile.fileName.toString().removeSuffix(".xml"),
          file = productFile.toString(),
          duplicates = duplicates
        ))
      }
    }
    
    return DuplicateIncludesReport(
      timestamp = System.currentTimeMillis().toString(),
      products = productsWithDuplicates,
      summary = DuplicateSummary(
        totalProducts = productXmlFiles.size,
        productsWithDuplicates = productsWithDuplicates.size,
        totalDuplicateFiles = productsWithDuplicates.sumOf { it.duplicates.size }
      )
    )
  }

  /**
   * Parses xi:include elements from an XML file.
   * Returns list of XiInclude objects with href and line number.
   */
  private fun parseXiIncludes(xmlFile: Path): List<XiInclude> {
    val includes = mutableListOf<XiInclude>()

    val content = xmlFile.readText()
    val root = JDOMUtil.load(xmlFile)

    // Find all xi:include elements (recursively)
    val includeElements = findElementsByName(root, "include")
    for (element in includeElements) {
      val href = element.getAttributeValue("href")
      if (!href.isNullOrEmpty()) {
        // Try to find line number by searching in the file content
        val lineNumber = findLineNumber(content, href)
        includes.add(XiInclude(href, lineNumber))
      }
    }

    return includes
  }
  
  /**
   * Recursively finds all elements with the given local name (ignoring namespace).
   */
  private fun findElementsByName(element: Element, localName: String): List<Element> {
    val result = mutableListOf<Element>()
    
    // Check if current element matches (ignoring namespace prefix)
    if (element.name == localName || element.name.endsWith(":$localName")) {
      result.add(element)
    }
    
    // Recursively search children
    for (child in element.children) {
      result.addAll(findElementsByName(child, localName))
    }
    
    return result
  }
  
  /**
   * Attempts to find the line number where an href appears in the XML content.
   */
  private fun findLineNumber(content: String, href: String): Int? {
    val lines = content.lines()
    for ((index, line) in lines.withIndex()) {
      if (line.contains("href=\"$href\"")) {
        return index + 1  // 1-based line numbers
      }
    }
    return null
  }
  
  /**
   * Resolves an xi:include href to an absolute file path.
   */
  private fun resolveIncludePath(href: String, currentFile: Path, projectRoot: Path): Path? {
    if (href.startsWith("/META-INF/")) {
      // Absolute path - search in known resource locations
      val locations = listOf(
        projectRoot.resolve("community/platform/platform-resources/src$href"),
        projectRoot.resolve("community/platform/platform-resources/generated$href"),
        projectRoot.resolve("community/java/ide-resources/resources$href"),
        projectRoot.resolve("ultimate/platform-ultimate/resources$href"),
        projectRoot.resolve("licenseCommon/resources$href"),
        projectRoot.resolve("licenseCommon/generated$href"),
        // Also check CIDR and other products
        projectRoot.resolve("CIDR/clion/main/nolang/resources$href"),
        projectRoot.resolve("goland/resources$href"),
        projectRoot.resolve("ruby/resources$href"),
        projectRoot.resolve("WebStorm/resources$href"),
        projectRoot.resolve("dbe/ide/resources$href"),
        projectRoot.resolve("aqua/branding/resources$href"),
        projectRoot.resolve("rider/resources$href"),
      )
      
      for (loc in locations) {
        if (loc.exists()) {
          return loc
        }
      }
      return null
    }
    else if (href.startsWith("/")) {
      // Handle paths like /something.xml
      return resolveIncludePath("/META-INF$href", currentFile, projectRoot)
    }
    else {
      // Relative path
      val dir = currentFile.parent
      return dir?.resolve(href)
    }
  }
  
  /**
   * Checks if the given path is a deprecatedInclude XML file that might contain nested xi:includes.
   */
  private fun isDeprecatedIncludeFile(path: Path): Boolean {
    val fileName = path.fileName.toString()
    return fileName.endsWith("-customization.xml") ||
           fileName == "ultimate.xml" ||
           fileName == "PlatformLangPlugin.xml" ||
           fileName == "structuralsearch.xml" ||
           fileName.endsWith("Plugin.xml")
  }
  
  /**
   * Represents a single xi:include element.
   */
  private data class XiInclude(
    @JvmField val href: String,
    @JvmField val lineNumber: Int?
  )
}

/**
 * Report containing all detected duplicate includes.
 */
@Serializable
data class DuplicateIncludesReport(
  val timestamp: String,
  val products: List<ProductDuplicates>,
  val summary: DuplicateSummary
)

/**
 * Duplicates found in a single product.
 */
@Serializable
data class ProductDuplicates(
  val name: String,
  val file: String,
  val duplicates: List<DuplicateInclude>
)

/**
 * A single duplicate include with its sources.
 */
@Serializable
data class DuplicateInclude(
  val href: String,
  val resolvedPath: String?,
  val count: Int,
  val sources: List<IncludeSource>
)

/**
 * Source of an include (where it was found).
 */
@Serializable
data class IncludeSource(
  val file: String,
  val sourceType: String,  // "xi:include" or "deprecatedInclude-nested"
  val lineNumber: Int?
)

/**
 * Summary statistics.
 */
@Serializable
data class DuplicateSummary(
  val totalProducts: Int,
  val productsWithDuplicates: Int,
  val totalDuplicateFiles: Int
)
