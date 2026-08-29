// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.xml

import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.pluginSystem.parser.impl.LoadPathUtil
import org.jdom.Element
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.findFileInModuleLibraryDependencies
import org.jetbrains.intellij.build.findFileInModuleSources
import org.jetbrains.intellij.build.isModuleNameLikeFilename
import org.jetbrains.intellij.build.readFileFromModuleOutput
import org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec
import org.jetbrains.jps.model.module.JpsModule

/**
 * Appends XML header comments (placed BEFORE the opening tag, outside <idea-plugin>).
 */
internal fun StringBuilder.appendXmlHeader(generatorCommand: String, productPropertiesClass: String) {
  append("<!-- DO NOT EDIT: This file is auto-generated from Kotlin code -->\n")
  append("<!-- To regenerate, run 'Generate Product Layouts' or directly $generatorCommand -->\n")
  append("<!-- Source: $productPropertiesClass.getProductContentDescriptor() -->\n")
}

/**
 * Appends the opening <idea-plugin> tag with optional xi:include namespace.
 * Only handles the tag itself - id/name/vendor are handled by metadataBuilder.
 */
internal fun StringBuilder.appendOpeningTag(
  spec: ProductModulesContentSpec,
  inlineXmlIncludes: Boolean,
  inlineModuleSets: Boolean
) {
  // Determine if xi:include namespace is needed
  val hasXmlIncludes = !inlineXmlIncludes && spec.deprecatedXmlIncludes.isNotEmpty()
  val hasModuleSetIncludes = !inlineModuleSets && spec.moduleSets.isNotEmpty()
  // When inlining xi-include files, those files might have their own xi-includes that need the namespace
  val needsXiNamespaceForInlining = inlineXmlIncludes && spec.deprecatedXmlIncludes.isNotEmpty()
  val needsXiNamespace = needsXiNamespaceForInlining || hasXmlIncludes || hasModuleSetIncludes
  if (needsXiNamespace) {
    append("<idea-plugin xmlns:xi=\"http://www.w3.org/2001/XInclude\">\n")
  }
  else {
    append("<idea-plugin>\n")
  }
}

/**
 * Generates xi:include directives or inline content for deprecated XML includes.
 */
internal fun generateXIncludes(
  spec: ProductModulesContentSpec,
  outputProvider: ModuleOutputProvider,
  inlineXmlIncludes: Boolean,
  sb: StringBuilder,
) {
  for (include in spec.deprecatedXmlIncludes) {
    // Find the module and file
    val module = outputProvider.findModule(include.contentModuleName.value)
    val resourcePath = include.resourcePath
    if (module == null) {
      error("Module '${include.contentModuleName.value}' not found (referenced in xi:include for '$resourcePath')")
    }

    // Sources, then libraries, then the module's own output - the same order `resolveXIncludeBytes` uses. The
    // output is not a mere fallback: a build assembling from Bazel outputs has no checkout to read sources from,
    // and the file it wants is the one that module compiled into its jar.
    val data = findFileInModuleSources(module, resourcePath)?.let { JDOMUtil.load(it) }
               ?: findFileInModuleLibraryDependencies(module = module, relativePath = resourcePath, outputProvider = outputProvider)
                 ?.let { JDOMUtil.load(it) }
               ?: readFileFromModuleOutput(module = module, relativePath = resourcePath, outputProvider = outputProvider)
                 ?.let { JDOMUtil.load(it) }
               ?: error("Resource '$resourcePath' not found in module '${module.name}' sources, libraries or output (referenced in xi:include)")

    if (inlineXmlIncludes && !include.optional) {
      resolveIncludes(data, ModuleScopedXIncludeResolver(module, outputProvider))
      withEditorFold(sb, "  ", "Inlined from ${include.contentModuleName}/$resourcePath") {
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
      if (include.optional) {
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

/**
 * Resolves nested `<xi:include>` elements inside a `deprecatedInclude` resource against
 * the **owning** module's sources and libraries.
 *
 * The `deprecatedInclude("<module>", "<resource>")` API unambiguously identifies the
 * owning module; xi:includes inside that resource are expected to live in the same
 * module, which is the convention throughout the platform. Expanding them eagerly at
 * render time removes the need for the late descriptor-search-scope resolver to carry
 * module-origin information across the shallow inlining step.
 *
 * Returning `null` for an unresolvable href signals a cross-module xi:include (e.g., an
 * `intellij.*.xml` content-module reference). The shared [resolveIncludes] will leave
 * such elements in place so the late resolver can handle them with its broader scope.
 */
private class ModuleScopedXIncludeResolver(
  private val module: JpsModule,
  private val outputProvider: ModuleOutputProvider,
) : XIncludeElementResolver {
  override fun resolveElement(relativePath: String, isOptional: Boolean, isDynamic: Boolean): Element? {
    if (isOptional || isDynamic) return null
    val loadPath = hrefToLoadPath(relativePath)
    return findFileInModuleSources(module, loadPath)?.let { JDOMUtil.load(it) }
           ?: findFileInModuleLibraryDependencies(module = module, relativePath = loadPath, outputProvider = outputProvider)
             ?.let { JDOMUtil.load(it) }
  }
}

/** [LoadPathUtil.toLoadPath], which reads the first character, so an empty href answers itself. */
private fun hrefToLoadPath(href: String): String = if (href.isEmpty()) href else LoadPathUtil.toLoadPath(href)
