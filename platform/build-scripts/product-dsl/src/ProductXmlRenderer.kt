// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout

import com.intellij.openapi.util.JDOMUtil
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.findFileInModuleLibraryDependencies
import org.jetbrains.intellij.build.findFileInModuleSources
import org.jetbrains.intellij.build.isModuleNameLikeFilename

/**
 * Appends XML header comments.
 */
internal fun StringBuilder.appendXmlHeader(generatorCommand: String, productPropertiesClass: String) {
  append("  <!-- DO NOT EDIT: This file is auto-generated from Kotlin code -->\n")
  append("  <!-- To regenerate, run 'Generate Product Layouts' or directly $generatorCommand -->\n")
  append("  <!-- Source: $productPropertiesClass -->\n")
}

/**
 * Appends the opening <idea-plugin> tag with optional xi:include namespace.
 */
internal fun StringBuilder.appendOpeningTag(
  spec: ProductModulesContentSpec,
  inlineXmlIncludes: Boolean,
  inlineModuleSets: Boolean
) {
  // Determine if xi:include namespace is needed
  val hasXmlIncludes = !inlineXmlIncludes && spec.deprecatedXmlIncludes.isNotEmpty()
  val hasModuleSetIncludes = !inlineModuleSets && spec.moduleSets.isNotEmpty()
  // when we inline another some xi-include file, it can in turn have own xi-includes
  val needsXiNamespace = inlineXmlIncludes || hasXmlIncludes || hasModuleSetIncludes
  if (needsXiNamespace) {
    append("<idea-plugin xmlns:xi=\"http://www.w3.org/2001/XInclude\">\n")
  }
  else {
    append("<idea-plugin>\n")
  }
  
  // Add id and name as child tags if PlatformLangPlugin.xml is not included
  val includesPlatformLang = spec.deprecatedXmlIncludes.any {
    it.resourcePath == "META-INF/PlatformLangPlugin.xml" ||
    it.resourcePath == "META-INF/JavaIdePlugin.xml" ||
    it.resourcePath == "META-INF/pycharm-core.xml"
  }
  
  if (!includesPlatformLang) {
    append("  <id>com.intellij</id>\n")
    append("  <name>IDEA CORE</name>\n")
    if (spec.vendor != null) {
      append("  <vendor>${spec.vendor}</vendor>\n")
    }
  }
}

/**
 * Generates xi:include directives or inline content for deprecated XML includes.
 */
internal fun generateXIncludes(
  spec: ProductModulesContentSpec,
  moduleOutputProvider: ModuleOutputProvider,
  inlineXmlIncludes: Boolean,
  sb: StringBuilder,
  isUltimateBuild: Boolean,
) {
  for (include in spec.deprecatedXmlIncludes) {
    // When inlining: skip ultimate-only `xi-includes` in Community builds
    if (inlineXmlIncludes && include.ultimateOnly && !isUltimateBuild) {
      continue
    }

    // Find the module and file
    val module = moduleOutputProvider.findModule(include.moduleName)
    val resourcePath = include.resourcePath
    if (module == null) {
      if (include.ultimateOnly) {
        error("Ultimate-only module '${include.moduleName}' not found in Ultimate build - this is a configuration error (referenced in xi:include for '$resourcePath')")
      }
      error("Module '${include.moduleName}' not found (referenced in xi:include for '$resourcePath')")
    }

    val data = findFileInModuleSources(module, resourcePath)?.let { JDOMUtil.load(it) }
               ?: findFileInModuleLibraryDependencies(module = module, relativePath = resourcePath)?.let { JDOMUtil.load(it) }
               ?: error("Resource '$resourcePath' not found in module '${module.name}' sources or libraries (referenced in xi:include)")

    if (inlineXmlIncludes && !include.optional) {
      withEditorFold(sb, "  ", "Inlined from ${include.moduleName}/$resourcePath") {
        // Inline the actual XML content
        for (element in data.children) {
          sb.append(JDOMUtil.write(element).prependIndent("  "))
          sb.append("\n")
        }
      }
      sb.append("\n")
    }
    else {
      // Generate xi:include with absolute path (resources are in /META-INF/... in jars)
      // Wrap ultimate-only and optional xi-includes with xi:fallback for graceful handling
      if (include.ultimateOnly || include.optional) {
        sb.append("""  <xi:include href="${resourcePathToXIncludePath(resourcePath)}">""")
        sb.append("\n")
        sb.append("""    <xi:fallback/>""")
        sb.append("\n")
        sb.append("""  </xi:include>""")
        sb.append("\n")
      }
      else {
        sb.append("""  <xi:include href="${resourcePathToXIncludePath(resourcePath)}"/>""")
        sb.append("\n")
      }
    }
  }
}

/**
 * Converts a resource path to an `xi:include` href path.
 */
internal fun resourcePathToXIncludePath(resourcePath: String): String {
  return if (isModuleNameLikeFilename(resourcePath)) resourcePath else "/$resourcePath"
}
