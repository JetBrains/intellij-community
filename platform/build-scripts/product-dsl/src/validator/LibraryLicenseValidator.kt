// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.ContentSourceKind
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.getLibraryFileName
import org.jetbrains.intellij.build.productLayout.model.error.MissingLibraryLicenseError
import org.jetbrains.intellij.build.productLayout.model.error.MissingLibraryLicenseViolation
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsRepositoryLibraryType

/**
 * Library license validation.
 *
 * Purpose: Make sure every library that an installation packages has a license entry.
 * Inputs: the `libraryLicenses` config list, and the production runtime dependencies of every module in the graph.
 * Output: `MissingLibraryLicenseError`.
 * Auto-fix: no.
 *
 * The rule walks the production modules of the plugin graph. `LibraryModuleValidator` and `TestLibraryScopeValidator`
 * read the JPS model through the same scope. The walk is recursive, so a module also reports the libraries of its
 * production runtime closure. A module that a plugin layout adds, and that the graph does not hold, is covered while
 * a module in the graph depends on it.
 *
 * This rule applies no Maven descriptor filter, and no filter on a JetBrains own group id. The
 * `third_party_libraries` build step skips a library of either shape. This rule keeps that library, so it stays
 * stricter than the build step. Please do not add either filter here.
 *
 * Glossary: docs/validators/README.md.
 * Spec: docs/validators/library-license.md.
 */
internal object LibraryLicenseValidator : PipelineNode {
  override val id get() = NodeIds.LIBRARY_LICENSE_VALIDATION

  override suspend fun execute(ctx: ComputeContext) {
    val model = ctx.model
    val licenses = model.config.libraryLicenses
    // an empty license list turns the rule off
    if (licenses.isEmpty()) {
      return
    }

    val outputProvider = model.outputProvider
    // the JPS module of every production content module, and of every production plugin
    val moduleNames = LinkedHashSet<String>()
    model.pluginGraph.query {
      contentModules { contentModule ->
        // a module that only a test plugin declares as content ships nothing
        var isProductionContent = false
        contentModule.contentProductionSources { source ->
          if (source.kind != ContentSourceKind.PLUGIN || !source.plugin().isTest) {
            isProductionContent = true
          }
        }
        if (isProductionContent) {
          moduleNames.add(contentModule.name().value)
        }
      }
      // the main module of a plugin holds the descriptor, and the graph keeps it as a plugin, not as a content module
      plugins { plugin ->
        if (!plugin.isTest) {
          moduleNames.add(plugin.name().value)
        }
      }
    }

    val coveredNames = licenses.flatMapTo(HashSet()) { it.getLibraryNames() }
    val (checkedModuleCount, violations) = collectMissingLicenseViolations(
      moduleNames = moduleNames,
      outputProvider = outputProvider,
      coveredNames = coveredNames,
    )

    // the graph always holds a production module, and a silent pass would hide every missing entry
    check(checkedModuleCount != 0) {
      "The plugin graph has no production module with a JPS module, and the license list has ${licenses.size} " +
      "entries. A pass here would hide every missing license entry."
    }

    if (violations.isNotEmpty()) {
      // the error sorts the violations in its own format function
      ctx.emitError(MissingLibraryLicenseError(context = "the plugin graph", violations = violations, licenseFile = "CommunityLibraryLicenses.kt or UltimateLibraryLicenses.kt"))
    }
  }
}

/**
 * Reads the production runtime libraries of each module, and reports the ones no license entry covers.
 *
 * Returns the number of modules that resolved to a JPS module, and the violations. Both rules need the count, because a
 * zero would make a pass meaningless.
 */
internal fun collectMissingLicenseViolations(
  moduleNames: Collection<String>,
  outputProvider: ModuleOutputProvider,
  coveredNames: Set<String>,
): Pair<Int, List<MissingLibraryLicenseViolation>> {
  // a library can come from more than one module, so report it once
  val libraryToModule = HashMap<JpsLibrary, String>()
  var checkedModuleCount = 0
  for (moduleName in moduleNames) {
    val module = outputProvider.findModule(moduleName) ?: continue
    checkedModuleCount++
    val enumerator = JpsJavaExtensionService.dependencies(module).recursively().includedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME)
    for (library in enumerator.libraries) {
      libraryToModule.put(library, moduleName)
    }
  }

  val violations = ArrayList<MissingLibraryLicenseViolation>()
  for ((library, moduleName) in libraryToModule) {
    val libraryName = getLibraryFileName(library)
    if (coveredNames.contains(libraryName) || isImplicitLibrary(libraryName)) {
      continue
    }
    // a directory of JARs with the name `Ant` and a Maven library with the name `ant` coexist.
    // The license entry gives the name `Ant`.
    if (libraryName == "ant") {
      continue
    }

    // the coordinates go into the report only, and they never change whether the rule reports a library
    val coordinates = library.asTyped(JpsRepositoryLibraryType.INSTANCE)?.properties?.data?.toString()
    violations.add(MissingLibraryLicenseViolation(libraryName = libraryName, coordinates = coordinates, moduleName = moduleName))
  }
  return checkedModuleCount to violations
}

/**
 * Tells if a library is a sub-library of another library.
 *
 * A license entry is required for a main library such as `ktor-client`, and not for its sub-libraries.
 */
internal fun isImplicitLibrary(libraryName: String): Boolean {
  return ((libraryName.startsWith("ktor-") || libraryName.startsWith("io.ktor.")) && (libraryName != "ktor-client")) ||
         libraryName.startsWith("skiko-awt-runtime-")
}
