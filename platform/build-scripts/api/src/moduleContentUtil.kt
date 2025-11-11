// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.util.lang.ImmutableZipFile
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.intellij.build.io.ZipEntryProcessorResult
import org.jetbrains.intellij.build.io.readZipFile
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleDependency
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

const val PLUGIN_XML_RELATIVE_PATH: String = "META-INF/plugin.xml"
val useTestSourceEnabled: Boolean = System.getProperty("idea.build.pack.test.source.enabled", "true").toBoolean()

fun getUnprocessedPluginXmlContent(module: JpsModule, context: ModuleOutputProvider): ByteArray {
  return requireNotNull(findUnprocessedDescriptorContent(module = module, path = PLUGIN_XML_RELATIVE_PATH, context = context)) {
    "META-INF/plugin.xml not found in ${module.name} module output"
  }
}

fun findUnprocessedDescriptorContent(module: JpsModule, path: String, context: ModuleOutputProvider): ByteArray? {
  try {
    val result = context.readFileContentFromModuleOutput(module = module, relativePath = path, forTests = false)
    if (result == null && useTestSourceEnabled) {
      return context.readFileContentFromModuleOutput(module = module, relativePath = path, forTests = true)
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

fun findFileInModuleLibraryDependencies(module: JpsModule, relativePath: String): ByteArray? {
  for (dependency in module.dependenciesList.dependencies) {
    if (dependency is JpsLibraryDependency) {
      val library = dependency.library ?: continue
      for (jarPath in library.getPaths(JpsOrderRootType.COMPILED)) {
        ImmutableZipFile.load(jarPath).use { zipFile ->
          zipFile.getData(relativePath)?.let { return it }
        }
      }
    }
  }
  return null
}

fun findProductModulesFile(clientMainModuleName: String, context: ModuleOutputProvider): Path? {
  return findFileInModuleSources(context.findRequiredModule(clientMainModuleName), "META-INF/$clientMainModuleName/product-modules.xml")
}

fun findFileInModuleDependencies(
  module: JpsModule,
  relativePath: String,
  context: ModuleOutputProvider,
  processedModules: MutableSet<String>,
  recursiveModuleExclude: String? = null,
): ByteArray? {
  findFileInModuleLibraryDependencies(module, relativePath)?.let {
    return it
  }

  return findFileInModuleDependenciesRecursive(
    module = module,
    relativePath = relativePath,
    context = context,
    processedModules = processedModules,
    recursiveModuleExclude = recursiveModuleExclude,
  )
}

private fun findFileInModuleDependenciesRecursive(
  module: JpsModule,
  relativePath: String,
  context: ModuleOutputProvider,
  processedModules: MutableSet<String>,
  recursiveModuleExclude: String?,
): ByteArray? {
  for (dependency in module.dependenciesList.dependencies) {
    if (dependency !is JpsModuleDependency) {
      continue
    }

    val moduleName = dependency.moduleReference.moduleName
    if (!processedModules.add(moduleName)) {
      continue
    }

    val dependentModule = context.findRequiredModule(moduleName)
    findUnprocessedDescriptorContent(module = dependentModule, path = relativePath, context = context)?.let {
      return it
    }

    // if recursiveModuleFilter is null, it means that non-direct search not needed
    if (recursiveModuleExclude != null && !moduleName.startsWith(recursiveModuleExclude)) {
      findFileInModuleDependenciesRecursive(
        module = dependentModule,
        relativePath = relativePath,
        context = context,
        recursiveModuleExclude = recursiveModuleExclude,
        processedModules = processedModules,
      )?.let {
        return it
      }
    }
  }
  return null
}

@Internal
fun hasModuleOutputPath(module: JpsModule, relativePath: String, context: ModuleOutputProvider): Boolean {
  return context.getModuleOutputRoots(module).any { output ->
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