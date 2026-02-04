// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.util.lang.ImmutableZipFile
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.intellij.build.io.ZipEntryProcessorResult
import org.jetbrains.intellij.build.io.readZipFile
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

suspend fun getUnprocessedPluginXmlContent(module: JpsModule, outputProvider: ModuleOutputProvider): ByteArray {
  return requireNotNull(findUnprocessedDescriptorContent(module = module, path = PLUGIN_XML_RELATIVE_PATH, outputProvider = outputProvider)) {
    "META-INF/plugin.xml not found in ${module.name} module output"
  }
}

suspend fun findUnprocessedDescriptorContent(module: JpsModule, path: String, outputProvider: ModuleOutputProvider): ByteArray? {
  try {
    val result = outputProvider.readFileContentFromModuleOutput(module = module, relativePath = path, forTests = false)
    if (result == null && outputProvider.useTestCompilationOutput) {
      return outputProvider.readFileContentFromModuleOutput(module = module, relativePath = path, forTests = true)
    }
    return result
  }
  catch (e: Throwable) {
    throw IllegalStateException("Cannot read $path from ${module.name} module output", e)
  }
}

private val rootTypeOrder = arrayOf(JavaResourceRootType.RESOURCE, JavaSourceRootType.SOURCE, JavaResourceRootType.TEST_RESOURCE, JavaSourceRootType.TEST_SOURCE)

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

fun getLibraryRoots(library: JpsLibrary, outputProvider: ModuleOutputProvider): List<Path> {
  return getLibraryReferenceRoots(library.createReference(), outputProvider)
}

fun findFileInModuleLibraryDependencies(module: JpsModule, relativePath: String, outputProvider: ModuleOutputProvider): ByteArray? {
  for (dependency in module.dependenciesList.dependencies) {
    if (dependency is JpsLibraryDependency) {
      for (jarPath in getLibraryReferenceRoots(dependency.libraryReference, outputProvider)) {
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
    findUnprocessedDescriptorContent(module = dependentModule, path = relativePath, outputProvider = provider)?.let {
      return it
    }

    findFileInModuleDependenciesRecursive(
      module = dependentModule,
      relativePath = relativePath,
      provider = provider,
      processedModules = processedModules,
      moduleNamePrefix = moduleNamePrefix,
    )?.let {
      return it
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
