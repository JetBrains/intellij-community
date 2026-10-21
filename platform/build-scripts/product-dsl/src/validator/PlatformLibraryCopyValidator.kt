// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.ContentModuleNode
import com.intellij.platform.pluginGraph.GraphScope
import com.intellij.platform.pluginGraph.ProductNode
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import org.jetbrains.intellij.build.productLayout.LIB_MODULE_PREFIX
import org.jetbrains.intellij.build.productLayout.model.error.PlatformLibraryCopyError
import org.jetbrains.intellij.build.productLayout.model.error.PlatformLibraryCopyViolation
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.library.JpsRepositoryLibraryType
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleReference
import org.jetbrains.jps.util.JpsPathUtil
import java.util.TreeSet

/**
 * Platform library copy validation.
 *
 * Purpose: Make sure that a bundled plugin does not pack a copy of a library that the core classloader already holds.
 * An embedded library module of a product puts its library into the core classloader.
 * A content module of a bundled plugin packs its module-level libraries into the jar of the plugin.
 * A plugin that depends on the owner of such a copy can load the copy instead of the platform classes.
 *
 * Inputs: the plugin graph for the embedded library modules and the bundled plugins of each product, the JPS model
 * for the module-level libraries of each module, and `model.suppressionConfig` for the allowed copies.
 * Output: `PlatformLibraryCopyError`, one for all products.
 *
 * The node drops a copy that `platformLibraryCopies` in `suppressions.json` lists for the module and the coordinate.
 * Auto-fix: no.
 *
 * Glossary: docs/validators/README.md.
 * Spec: docs/validators/platform-library-copy.md.
 */
internal object PlatformLibraryCopyValidator : PipelineNode {
  override val id get() = NodeIds.PLATFORM_LIBRARY_COPY_VALIDATION

  override fun execute(ctx: ComputeContext) {
    val outputProvider = ctx.model.outputProvider
    val allowedCopies = ctx.model.suppressionConfig.platformLibraryCopies
    val artifactsByModule = HashMap<String, List<MavenArtifact>>()
    fun artifactsOf(moduleName: String): List<MavenArtifact> {
      return artifactsByModule.getOrPut(moduleName) {
        outputProvider.findModule(moduleName)?.let { collectModuleLibraryArtifacts(it) } ?: emptyList()
      }
    }

    val productsByViolation = LinkedHashMap<PlatformLibraryCopyViolation, MutableSet<String>>()
    ctx.model.pluginGraph.query {
      products { product ->
        val platformModules = collectEmbeddedLibraryModules(product)
        // the first platform module in name order provides a coordinate, so the report is stable
        val platformModuleByCoordinate = HashMap<String, String>()
        for (platformModule in platformModules) {
          for (artifact in artifactsOf(platformModule)) {
            platformModuleByCoordinate.putIfAbsent(artifact.coordinate, platformModule)
          }
        }
        if (platformModuleByCoordinate.isEmpty()) {
          return@products
        }

        val productName = product.name()
        product.bundles { plugin ->
          plugin.containsContent { module, _ ->
            val moduleName = module.contentName().value
            // a module in slash notation has no JPS module of its own, and a platform module is not a copy
            if (moduleName.contains('/') || moduleName in platformModules) {
              return@containsContent
            }
            for (artifact in artifactsOf(moduleName)) {
              val platformModule = platformModuleByCoordinate.get(artifact.coordinate) ?: continue
              if (allowedCopies.get(module.contentName())?.coordinates?.contains(artifact.coordinate) == true) {
                continue
              }
              val violation = PlatformLibraryCopyViolation(
                plugin = plugin.name().value,
                contentModule = moduleName,
                library = artifact.libraryName,
                coordinate = artifact.coordinate,
                version = artifact.version,
                platformModule = platformModule,
                products = emptyList(),
              )
              productsByViolation.computeIfAbsent(violation) { TreeSet() }.add(productName)
            }
          }
        }
      }
    }

    if (productsByViolation.isNotEmpty()) {
      val violations = productsByViolation.map { (violation, products) -> violation.copy(products = products.toList()) }
      ctx.emitError(PlatformLibraryCopyError(context = "bundled plugins", violations = violations))
    }
  }
}

/**
 * Returns the names of the library modules that the product embeds, in name order.
 *
 * A module counts when a module set of the product, or the product itself, declares it with the embedded loading rule.
 */
private fun GraphScope.collectEmbeddedLibraryModules(product: ProductNode): Set<String> {
  val result = TreeSet<String>()
  fun add(module: ContentModuleNode, loading: ModuleLoadingRuleValue) {
    val name = module.contentName().value
    if (loading == ModuleLoadingRuleValue.EMBEDDED && name.startsWith(LIB_MODULE_PREFIX)) {
      result.add(name)
    }
  }

  moduleSets { moduleSet ->
    if (product.includesModuleSetRecursive(moduleSet)) {
      moduleSet.containsModule { module, loading -> add(module, loading) }
    }
  }
  product.containsContent { module, loading -> add(module, loading) }
  return result
}

/**
 * A Maven artifact that a module-level library of a module resolves.
 */
internal data class MavenArtifact(
  @JvmField val groupId: String,
  @JvmField val artifactId: String,
  @JvmField val version: String,
  @JvmField val libraryName: String,
) {
  val coordinate: String
    get() = "$groupId:$artifactId"
}

/**
 * Returns the Maven artifacts of the module-level repository libraries of [module].
 *
 * A library dependency with the PROVIDED or TEST scope does not count, because the build does not pack it.
 * A project library does not count. The module dependencies of [module] do not count.
 */
internal fun collectModuleLibraryArtifacts(module: JpsModule): List<MavenArtifact> {
  val javaExtensionService = JpsJavaExtensionService.getInstance()
  val result = ArrayList<MavenArtifact>()
  for (dependency in module.dependenciesList.dependencies) {
    if (dependency !is JpsLibraryDependency || dependency.libraryReference.parentReference !is JpsModuleReference) {
      continue
    }
    val scope = javaExtensionService.getDependencyExtension(dependency)?.scope
    if (scope != null && !scope.isIncludedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME)) {
      continue
    }
    dependency.library?.let { collectMavenArtifacts(it, result) }
  }
  return result
}

/**
 * Derives a Maven artifact from each `CLASSES` root of a repository library.
 *
 * The roots are the resolved closure of the library, so an excluded dependency has no root.
 * The JPS model holds a root with the repository path expanded.
 * The root of the main artifact therefore gives the repository path, and each other root gives its group from that path.
 * A root outside the repository path does not count.
 */
private fun collectMavenArtifacts(library: JpsLibrary, result: MutableList<MavenArtifact>) {
  val descriptor = library.asTyped(JpsRepositoryLibraryType.INSTANCE)?.properties?.data ?: return
  val urls = library.getRootUrls(JpsOrderRootType.COMPILED)
  val mainArtifactDirectory = "/${descriptor.groupId.replace('.', '/')}/${descriptor.artifactId}/${descriptor.version}/"
  val repositoryRoot = urls.firstNotNullOfOrNull { url ->
    val index = url.indexOf(mainArtifactDirectory)
    if (index < 0) null else url.substring(0, index + 1)
  } ?: return

  for (url in urls) {
    if (!url.startsWith(repositoryRoot)) {
      continue
    }
    // <group directories>/<artifact>/<version>/<file>
    val segments = url.substring(repositoryRoot.length).removeSuffix(JpsPathUtil.JAR_SEPARATOR).split('/')
    if (segments.size < 4) {
      continue
    }
    result.add(MavenArtifact(
      groupId = segments.subList(0, segments.size - 3).joinToString("."),
      artifactId = segments.get(segments.size - 3),
      version = segments.get(segments.size - 2),
      libraryName = library.name,
    ))
  }
}
