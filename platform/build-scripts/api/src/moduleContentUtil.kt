// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.util.lang.ImmutableZipFile
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.intellij.build.io.ZipEntryProcessorResult
import org.jetbrains.intellij.build.io.readZipFile
import org.jetbrains.jps.model.JpsElementType
import org.jetbrains.jps.model.ex.JpsElementBase
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsLibraryReference
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleDependency
import org.jetbrains.jps.model.module.JpsModuleReference
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

const val PLUGIN_XML_RELATIVE_PATH: String = "META-INF/plugin.xml"

/**
 * Where one step of a descriptor search looks.
 *
 * A search asks many modules for the same file and at most one of them has it, so the two places have to be
 * separated rather than tried module by module. [MODULE_OUTPUT] resolves a module's Bazel output *before* it can
 * look inside, and under an explicit input manifest that resolution is the declaration - see
 * `org.jetbrains.intellij.build.impl.BazelBuildInputs`. Asking three hundred modules "do you have this?" that way
 * makes three hundred jars an input of a dev-distribution fragment that packs four of them, whether or not a byte
 * is read.
 *
 * So a search over several candidates runs [PRODUCTION_SOURCES] across all of them first and falls through to
 * [MODULE_OUTPUT] only when none of them had the file. Each pass needs its own "already visited" set - a set shared
 * between them would make the second pass skip every module the first one missed.
 */
enum class DescriptorSearchPass {
  /**
   * The checkout, which costs nothing to miss. Every descriptor a product layout can reach is materialized here -
   * a dev-distribution fragment gets them through the project model tree (see `intellij_project_model_tree`), so
   * this pass answers the normal case on its own.
   */
  PRODUCTION_SOURCES,

  /** The module's compiled output, for a descriptor that is a generated resource and exists in no source root. */
  MODULE_OUTPUT,
}

suspend fun getUnprocessedPluginXmlContent(module: JpsModule, outputProvider: ModuleOutputProvider): ByteArray {
  return requireNotNull(findUnprocessedDescriptorContent(module = module, path = PLUGIN_XML_RELATIVE_PATH, outputProvider = outputProvider)) {
    "META-INF/plugin.xml not found in ${module.name} module output"
  }
}

/**
 * Both passes against one module, for a caller that has a single candidate and so has nothing to separate them over.
 * A caller with several candidates must loop over [DescriptorSearchPass] itself and call [readDescriptor].
 */
suspend fun findUnprocessedDescriptorContent(module: JpsModule, path: String, outputProvider: ModuleOutputProvider): ByteArray? {
  for (pass in DescriptorSearchPass.entries) {
    readDescriptor(module = module, path = path, outputProvider = outputProvider, pass = pass)?.let {
      return it
    }
  }
  return null
}

/** The bytes of [path] in [module], as seen by [pass], or `null` if that pass does not have them. */
suspend fun readDescriptor(module: JpsModule, path: String, outputProvider: ModuleOutputProvider, pass: DescriptorSearchPass): ByteArray? {
  try {
    return when (pass) {
      // Production roots only: the module output below reaches test output solely when
      // `isTestCompilationOutputEnabled`, and an unrestricted source search would not honour that.
      DescriptorSearchPass.PRODUCTION_SOURCES -> {
        findFileInModuleSources(module = module, relativePath = path, onlyProductionSources = true)?.let { Files.readAllBytes(it) }
      }
      // Scrambling is not a hazard here - this reads *module output*, which scrambling never rewrites; scrambled
      // descriptors reach a distribution through `CachedDescriptorContainer`, consulted before this function.
      DescriptorSearchPass.MODULE_OUTPUT -> {
        // A test-only module has no production payload to search, and asking for it is not free: under an explicit
        // Bazel input manifest that resolution is the declaration, so probing the empty production stub of a
        // `.tests` module makes a jar nobody packs an input - and fails when it was never declared. Packing agrees
        // (see `JarPackagerDependencyHelper.isTestPluginModule`), so descriptor search must not disagree.
        if (isTestOnlyPluginModule(moduleName = module.name, module = module, outputProvider = outputProvider)) {
          outputProvider.readFileContentFromModuleOutput(module = module, relativePath = path, forTests = true)
        }
        else {
          val result = outputProvider.readFileContentFromModuleOutput(module = module, relativePath = path, forTests = false)
          if (result == null && outputProvider.isTestCompilationOutputEnabled(module)) {
            outputProvider.readFileContentFromModuleOutput(module = module, relativePath = path, forTests = true)
          }
          else {
            result
          }
        }
      }
    }
  }
  catch (e: Throwable) {
    throw IllegalStateException("Cannot read $path from ${module.name} module output", e)
  }
}

private val rootTypeOrder = arrayOf<JpsElementType<out JpsElementBase<*>>>(JavaResourceRootType.RESOURCE, JavaSourceRootType.SOURCE, JavaResourceRootType.TEST_RESOURCE, JavaSourceRootType.TEST_SOURCE)

fun findFileInModuleSources(module: JpsModule, relativePath: String, onlyProductionSources: Boolean = false): Path? {
  for (type in rootTypeOrder) {
    for (root in module.sourceRoots) {
      if (type != root.rootType || (onlyProductionSources && !(root.rootType == JavaResourceRootType.RESOURCE || root.rootType == JavaSourceRootType.SOURCE))) {
        continue
      }
      val sourceFile = JpsJavaExtensionService.getInstance().findSourceFile(root, relativePath)
      if (sourceFile != null) {
        return sourceFile
      }
    }
  }
  return null
}

fun isModuleNameLikeFilename(relativePath: String): Boolean = relativePath.startsWith("intellij.") || relativePath.startsWith("fleet.")

fun getLibraryReferenceRoots(libraryReference: JpsLibraryReference, outputProvider: ModuleOutputProvider): List<Path> {
  val parentLibraryReference = libraryReference.parentReference
  val moduleLibraryModuleName = if (parentLibraryReference is JpsModuleReference) parentLibraryReference.moduleName else null
  return outputProvider.findLibraryRoots(libraryReference.libraryName, moduleLibraryModuleName = moduleLibraryModuleName)
}

/** [getLibraryReferenceRoots] for a probe: only the jars this build declares. See [ModuleOutputProvider.findDeclaredLibraryRoots]. */
private fun getDeclaredLibraryReferenceRoots(libraryReference: JpsLibraryReference, outputProvider: ModuleOutputProvider): List<Path> {
  val parentLibraryReference = libraryReference.parentReference
  val moduleLibraryModuleName = if (parentLibraryReference is JpsModuleReference) parentLibraryReference.moduleName else null
  return outputProvider.findDeclaredLibraryRoots(libraryReference.libraryName, moduleLibraryModuleName = moduleLibraryModuleName)
}

fun getLibraryRoots(library: JpsLibrary, outputProvider: ModuleOutputProvider): List<Path> {
  return getLibraryReferenceRoots(library.createReference(), outputProvider)
}

/**
 * Belongs to [DescriptorSearchPass.MODULE_OUTPUT] alone: a library has no source root, so there is nothing for the
 * sources pass to find, and resolving its jars to ask is the declaration that pass exists to avoid - which is why the
 * jars come from [ModuleOutputProvider.findDeclaredLibraryRoots] rather than [ModuleOutputProvider.findLibraryRoots].
 */
fun findFileInModuleLibraryDependencies(module: JpsModule, relativePath: String, outputProvider: ModuleOutputProvider): ByteArray? {
  for (dependency in module.dependenciesList.dependencies) {
    if (dependency is JpsLibraryDependency) {
      for (jarPath in getDeclaredLibraryReferenceRoots(dependency.libraryReference, outputProvider)) {
        ImmutableZipFile.load(jarPath).use { zipFile ->
          zipFile.getData(relativePath)?.let { return it }
        }
      }
    }
  }
  return null
}

fun findProductModulesFile(clientMainModuleName: String, provider: ModuleOutputProvider): Path? {
  return findFileInModuleSources(provider.findRequiredModule(clientMainModuleName), "META-INF/$clientMainModuleName/product-modules.xml")
}

suspend fun findFileInModuleDependenciesRecursive(
  module: JpsModule,
  relativePath: String,
  provider: ModuleOutputProvider,
  processedModules: MutableSet<String>,
  pass: DescriptorSearchPass,
  moduleNamePrefix: String? = null,
): ByteArray? {
  for (dependency in module.dependenciesList.dependencies) {
    if (dependency !is JpsModuleDependency) {
      continue
    }

    val moduleName = dependency.moduleReference.moduleName
    if (moduleNamePrefix != null && !moduleName.startsWith(moduleNamePrefix)) {
      continue
    }
    if (!processedModules.add(moduleName)) {
      continue
    }

    val dependentModule = provider.findRequiredModule(moduleName)
    readDescriptor(module = dependentModule, path = relativePath, outputProvider = provider, pass = pass)?.let {
      return it
    }

    findFileInModuleDependenciesRecursive(
      module = dependentModule,
      relativePath = relativePath,
      provider = provider,
      processedModules = processedModules,
      pass = pass,
      moduleNamePrefix = moduleNamePrefix,
    )?.let {
      return it
    }
  }
  return null
}

/**
 * The bytes of [relativePath] in [module]'s own output, or `null` if it has none.
 *
 * [findUnprocessedDescriptorContent] is the usual way to read a module's output and should be preferred. This
 * exists for XML generation, which runs inside a non-suspending `buildString` builder and would otherwise have
 * to fall back to reading the checkout - which is not there when the build assembles from Bazel outputs alone.
 */
@Internal
fun readFileFromModuleOutput(module: JpsModule, relativePath: String, outputProvider: ModuleOutputProvider): ByteArray? {
  for (output in outputProvider.getModuleOutputRoots(module)) {
    val attributes = try {
      Files.readAttributes(output, BasicFileAttributes::class.java)
    }
    catch (_: FileSystemException) {
      continue
    }

    if (attributes.isDirectory) {
      val file = output.resolve(relativePath)
      if (Files.exists(file)) {
        return Files.readAllBytes(file)
      }
    }
    else if (attributes.isRegularFile && output.toString().endsWith(".jar")) {
      ImmutableZipFile.load(output).use { zipFile ->
        zipFile.getData(relativePath)?.let { return it }
      }
    }
    else {
      throw IllegalStateException("Module '${module.name}' output is neither directory, nor jar $output")
    }
  }
  return null
}

@Internal
fun hasModuleOutputPath(module: JpsModule, relativePath: String, outputProvider: ModuleOutputProvider): Boolean {
  return outputProvider.getModuleOutputRoots(module).any { output ->
    val attributes = try {
      Files.readAttributes(output, BasicFileAttributes::class.java)
    }
    catch (_: FileSystemException) {
      return@any false
    }

    if (attributes.isDirectory) {
      return@any Files.exists(output.resolve(relativePath))
    }
    else if (attributes.isRegularFile && output.toString().endsWith(".jar")) {
      var found = false
      readZipFile(output) { name, _ ->
        if (name == relativePath) {
          found = true
          ZipEntryProcessorResult.STOP
        }
        else {
          ZipEntryProcessorResult.CONTINUE
        }
      }
      return@any found
    }
    else {
      throw IllegalStateException("Module '${module.name}' output is neither directory, nor jar $output")
    }
  }
}
