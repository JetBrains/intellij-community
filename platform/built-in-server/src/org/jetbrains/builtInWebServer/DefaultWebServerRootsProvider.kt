/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.builtInWebServer

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.DirectoryIndex
import com.intellij.openapi.roots.impl.ModuleLibraryOrderEntryImpl
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PlatformUtils
import com.intellij.util.containers.computeIfAny

internal data class SuitableRoot(val file: VirtualFile, val moduleQualifier: String?)

private class DefaultWebServerRootsProvider : WebServerRootsProvider() {
  override fun resolve(path: String, project: Project, pathQuery: PathQuery): PathInfo? {
    val pathToFileManager = WebServerPathToFileManager.getInstance(project)

    var effectivePath = path
    if (PlatformUtils.isIntelliJ()) {
      val index = effectivePath.indexOf('/')
      if (index > 0 && !effectivePath.regionMatches(0, project.name, 0, index, !SystemInfo.isFileSystemCaseSensitive)) {
        val moduleName = effectivePath.substring(0, index)
        val module = runReadAction { ModuleManager.getInstance(project).findModuleByName(moduleName) }
        if (module != null && !module.isDisposed) {
          effectivePath = effectivePath.substring(index + 1)
          val resolver = pathToFileManager.getResolver(effectivePath)
          val result = RootProvider.values().computeIfAny { findByRelativePath(effectivePath, it.getRoots(module.rootManager), resolver, moduleName, pathQuery) }
            ?: findInModuleLibraries(effectivePath, module, resolver, pathQuery)
          if (result != null) {
            return result
          }
        }
      }
    }

    val resolver = pathToFileManager.getResolver(effectivePath)
    val modules = runReadAction { ModuleManager.getInstance(project).modules }
    if (pathQuery.useVfs) {
      var oldestParent = path.indexOf("/").let { if (it > 0) path.substring(0, it) else null }
      if (oldestParent == null && !path.isEmpty() && !path.contains('.')) {
        // maybe it is top level directory? (in case of dart projects - web)
        oldestParent = path
      }

      if (oldestParent != null) {
        for ((file, moduleQualifier) in pathToFileManager.parentToSuitableRoot.get(oldestParent)) {
          file.findFileByRelativePath(path)?.let {
            return PathInfo(null, it, file, moduleQualifier)
          }
        }
      }
    }
    else {
      for (rootProvider in RootProvider.values()) {
        for (module in modules) {
          if (module.isDisposed) {
            continue
          }

          findByRelativePath(path, rootProvider.getRoots(module.rootManager), resolver, null, pathQuery)?.let {
            it.moduleName = getModuleNameQualifier(project, module)
            return it
          }
        }
      }
    }

    if (!pathQuery.searchInLibs) {
      // yes, if !searchInLibs, config.json is also not checked
      return null
    }

    fun findByConfigJson(): PathInfo? {
      // https://youtrack.jetbrains.com/issue/WEB-24283
      for (rootProvider in RootProvider.values()) {
        for (module in modules) {
          if (module.isDisposed) {
            continue
          }

          for (root in rootProvider.getRoots(module.rootManager)) {
            if (resolver.resolve("config.json", root, pathQuery = pathQuery) != null) {
              resolver.resolve("index.html", root, pathQuery = pathQuery)?.let {
                it.moduleName = getModuleNameQualifier(project, module)
                return it
              }
            }
          }
        }
      }
      return null
    }

    val exists = pathToFileManager.pathToExistShortTermCache.getIfPresent("config.json")
    if (exists == null || exists) {
      val result = findByConfigJson()
      pathToFileManager.pathToExistShortTermCache.put("config.json", result != null)
      if (result != null) {
        return result
      }
    }

    return findInLibraries(project, effectivePath, resolver, pathQuery)
  }

  override fun getPathInfo(file: VirtualFile, project: Project): PathInfo? {
    return runReadAction {
      val directoryIndex = DirectoryIndex.getInstance(project)
      val info = directoryIndex.getInfoForFile(file)
      // we serve excluded files
      if (!info.isExcluded(file) && !info.isInProject(file)) {
        // javadoc jars is "not under project", but actually is, so, let's check library or SDK
        if (file.fileSystem == JarFileSystem.getInstance()) getInfoForDocJar(file, project) else null
      }
      else {
        var root = info.sourceRoot
        val isRootNameOptionalInPath: Boolean
        val isLibrary: Boolean
        if (root == null) {
          isRootNameOptionalInPath = false
          root = info.contentRoot
          if (root == null) {
            root = info.libraryClassRoot
            if (root == null) {
              // https://youtrack.jetbrains.com/issue/WEB-20598
              return@runReadAction null
            }

            isLibrary = true
          }
          else {
            isLibrary = false
          }
        }
        else {
          isLibrary = info.isInLibrarySource(file)
          isRootNameOptionalInPath = !isLibrary
        }

        var module = info.module
        if (isLibrary && module == null) {
          for (entry in directoryIndex.getOrderEntries(info)) {
            if (entry is ModuleLibraryOrderEntryImpl) {
              module = entry.ownerModule
              break
            }
          }
        }

        PathInfo(null, file, root, getModuleNameQualifier(project, module), isLibrary, isRootNameOptionalInPath = isRootNameOptionalInPath)
      }
    }
  }
}

internal enum class RootProvider {
  SOURCE {
    override fun getRoots(rootManager: ModuleRootManager): Array<VirtualFile> = rootManager.sourceRoots
  },
  CONTENT {
    override fun getRoots(rootManager: ModuleRootManager): Array<VirtualFile> = rootManager.contentRoots
  },
  EXCLUDED {
    override fun getRoots(rootManager: ModuleRootManager): Array<VirtualFile> = rootManager.excludeRoots
  };

  abstract fun getRoots(rootManager: ModuleRootManager): Array<VirtualFile>
}

private val ORDER_ROOT_TYPES by lazy {
  val javaDocRootType = getJavadocOrderRootType()
  if (javaDocRootType == null)
    arrayOf(OrderRootType.DOCUMENTATION, OrderRootType.SOURCES, OrderRootType.CLASSES)
  else
    arrayOf(javaDocRootType, OrderRootType.DOCUMENTATION, OrderRootType.SOURCES, OrderRootType.CLASSES)
}

private fun getJavadocOrderRootType(): OrderRootType? {
  return try {
    JavadocOrderRootType.getInstance()
  }
  catch (e: Throwable) {
    null
  }
}

private fun findInModuleLibraries(path: String, module: Module, resolver: FileResolver, pathQuery: PathQuery): PathInfo? {
  val index = path.indexOf('/')
  if (index <= 0) {
    return null
  }

  val libraryFileName = path.substring(0, index)
  val relativePath = path.substring(index + 1)
  return ORDER_ROOT_TYPES.computeIfAny {
    findInModuleLevelLibraries(module, it) { root, _ ->
      if (StringUtil.equalsIgnoreCase(root.nameSequence, libraryFileName)) resolver.resolve(relativePath, root, isLibrary = true, pathQuery = pathQuery) else null
    }
  }
}

private fun findInLibraries(project: Project, path: String, resolver: FileResolver, pathQuery: PathQuery): PathInfo? {
  val index = path.indexOf('/')
  if (index < 0) {
    return null
  }

  val libraryFileName = path.substring(0, index)
  val relativePath = path.substring(index + 1)
  return findInLibrariesAndSdk(project, ORDER_ROOT_TYPES) { root, _ ->
    if (StringUtil.equalsIgnoreCase(root.nameSequence, libraryFileName)) resolver.resolve(relativePath, root, isLibrary = true, pathQuery = pathQuery) else null
  }
}

private fun getInfoForDocJar(file: VirtualFile, project: Project): PathInfo? {
  val javaDocRootType = getJavadocOrderRootType() ?: return null
  return findInLibrariesAndSdk(project, arrayOf(javaDocRootType)) { root, module ->
    if (VfsUtilCore.isAncestor(root, file, false)) PathInfo(null, file, root, getModuleNameQualifier(project, module), true) else null
  }
}

internal fun getModuleNameQualifier(project: Project, module: Module?): String? {
  if (module != null && PlatformUtils.isIntelliJ() && !(module.name.equals(project.name, ignoreCase = true) || compareNameAndProjectBasePath(module.name, project))) {
    return module.name
  }
  return null
}

private fun findByRelativePath(path: String, roots: Array<VirtualFile>, resolver: FileResolver, moduleName: String?, pathQuery: PathQuery) = roots.computeIfAny { resolver.resolve(path, it, moduleName, pathQuery = pathQuery) }

private fun findInLibrariesAndSdk(project: Project, rootTypes: Array<OrderRootType>, fileProcessor: (root: VirtualFile, module: Module?) -> PathInfo?): PathInfo? {
  fun findInLibraryTable(table: LibraryTable, rootType: OrderRootType) = table.libraryIterator.computeIfAny { it.getFiles(rootType).computeIfAny { fileProcessor(it, null) } }

  fun findInProjectSdkOrInAll(rootType: OrderRootType): PathInfo? {
    val inSdkFinder = { sdk: Sdk -> sdk.rootProvider.getFiles(rootType).computeIfAny { fileProcessor(it, null) } }

    val projectSdk = ProjectRootManager.getInstance(project).projectSdk
    return projectSdk?.let(inSdkFinder) ?: ProjectJdkTable.getInstance().allJdks.computeIfAny { if (it === projectSdk) null else inSdkFinder(it) }
  }

  return rootTypes.computeIfAny { rootType ->
    runReadAction {
      findInLibraryTable(LibraryTablesRegistrar.getInstance().getLibraryTable(project), rootType)
        ?: findInProjectSdkOrInAll(rootType)
        ?: ModuleManager.getInstance(project).modules.computeIfAny { if (it.isDisposed) null else findInModuleLevelLibraries(it, rootType, fileProcessor) }
        ?: findInLibraryTable(LibraryTablesRegistrar.getInstance().libraryTable, rootType)
    }
  }
}

private fun findInModuleLevelLibraries(module: Module, rootType: OrderRootType, fileProcessor: (root: VirtualFile, module: Module?) -> PathInfo?): PathInfo? {
  return module.rootManager.orderEntries.computeIfAny {
    if (it is LibraryOrderEntry && it.isModuleLevel) it.getFiles(rootType).computeIfAny { fileProcessor(it, module) } else null
  }
}