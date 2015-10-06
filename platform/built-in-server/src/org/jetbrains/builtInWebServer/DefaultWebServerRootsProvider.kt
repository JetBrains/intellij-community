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
import com.intellij.openapi.roots.JavadocOrderRootType
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.DirectoryIndex
import com.intellij.openapi.roots.impl.ModuleLibraryOrderEntryImpl
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PlatformUtils
import com.intellij.util.Processor

class DefaultWebServerRootsProvider : WebServerRootsProvider() {
  override fun resolve(path: String, project: Project): PathInfo? {
    var effectivePath = path
    if (PlatformUtils.isIntelliJ()) {
      val index = effectivePath.indexOf('/')
      if (index > 0 && !effectivePath.regionMatches(0, project.name, 0, index, !SystemInfo.isFileSystemCaseSensitive)) {
        val moduleName = effectivePath.substring(0, index)
        val module = runReadAction { ModuleManager.getInstance(project).findModuleByName(moduleName) }

        if (module != null && !module.isDisposed) {
          effectivePath = effectivePath.substring(index + 1)
          val resolver = WebServerPathToFileManager.getInstance(project).getResolver(effectivePath)

          val moduleRootManager = ModuleRootManager.getInstance(module)
          for (rootProvider in RootProvider.values()) {
            val result = findByRelativePath(effectivePath, rootProvider.getRoots(moduleRootManager), resolver, moduleName)
            if (result != null) {
              return result
            }
          }

          val result = findInModuleLibraries(effectivePath, module, resolver)
          if (result != null) {
            return result
          }
        }
      }
    }

    val modules = runReadAction { ModuleManager.getInstance(project).modules }
    val resolver = WebServerPathToFileManager.getInstance(project).getResolver(effectivePath)
    if (!modules.isEmpty()) {
      for (rootProvider in RootProvider.values()) {
        val result = findByRelativePath(project, effectivePath, modules, rootProvider, resolver)
        if (result != null) {
          return result
        }
      }
    }
    return findInLibraries(project, modules, effectivePath, resolver)
  }

  override fun getPathInfo(file: VirtualFile, project: Project): PathInfo? {
    runReadAction {
      val directoryIndex = DirectoryIndex.getInstance(project)
      val info = directoryIndex.getInfoForFile(file)
      // we serve excluded files
      if (!info.isExcluded && !info.isInProject) {
        // javadoc jars is "not under project", but actually is, so, let's check project library table
        return if (file.fileSystem == JarFileSystem.getInstance()) getInfoForDocJar(file, project) else null
      }

      var root = info.sourceRoot
      val isLibrary: Boolean
      if (root == null) {
        root = info.contentRoot
        if (root == null) {
          root = info.libraryClassRoot
          isLibrary = true

          assert(root != null) { file.presentableUrl }
        }
        else {
          isLibrary = false
        }
      }
      else {
        isLibrary = info.isInLibrarySource
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

      return PathInfo(null, file, root!!, getModuleNameQualifier(project, module), isLibrary)
    }
  }

  private enum class RootProvider {
    SOURCE {
      override fun getRoots(rootManager: ModuleRootManager) = rootManager.sourceRoots
    },
    CONTENT {
      override fun getRoots(rootManager: ModuleRootManager) = rootManager.contentRoots
    },
    EXCLUDED {
      override fun getRoots(rootManager: ModuleRootManager) = rootManager.excludeRoots
    };

    public abstract fun getRoots(rootManager: ModuleRootManager): Array<VirtualFile>
  }

  companion object {
    private val ORDER_ROOT_TYPES = object : NotNullLazyValue<Array<OrderRootType>>() {
      override fun compute(): Array<OrderRootType> {
        val javaDocRootType = getJavadocOrderRootType()
        return if (javaDocRootType == null)
          arrayOf(OrderRootType.DOCUMENTATION, OrderRootType.SOURCES, OrderRootType.CLASSES)
        else
          arrayOf(javaDocRootType, OrderRootType.DOCUMENTATION, OrderRootType.SOURCES, OrderRootType.CLASSES)
      }
    }

    private fun getJavadocOrderRootType(): OrderRootType? {
      try {
        return JavadocOrderRootType.getInstance()
      }
      catch (e: Throwable) {
        return null
      }
    }

    private fun findInModuleLibraries(path: String, module: Module, resolver: FileResolver): PathInfo? {
      val index = path.indexOf('/')
      if (index <= 0) {
        return null
      }

      val result = Ref.create<PathInfo>()
      findInModuleLibraries(resolver, path.substring(0, index), path.substring(index + 1), result, module)
      return result.get()
    }

    private fun findInLibraries(project: Project, modules: Array<Module>, path: String, resolver: FileResolver): PathInfo? {
      val index = path.indexOf('/')
      if (index < 0) {
        return null
      }

      val libraryFileName = path.substring(0, index)
      val relativePath = path.substring(index + 1)
      runReadAction {
        val result = Ref.create<PathInfo>()
        for (module in modules) {
          if (!module.isDisposed) {
            if (findInModuleLibraries(resolver, libraryFileName, relativePath, result, module)) {
              return result.get()
            }
          }
        }

        for (library in LibraryTablesRegistrar.getInstance().getLibraryTable(project).libraries) {
          val pathInfo = findInLibrary(libraryFileName, relativePath, library, resolver)
          if (pathInfo != null) {
            return pathInfo
          }
        }
      }

      return null
    }

    private fun findInModuleLibraries(resolver: FileResolver, libraryFileName: String, relativePath: String, result: Ref<PathInfo>, module: Module): Boolean {
      ModuleRootManager.getInstance(module).orderEntries().forEachLibrary(object : Processor<Library> {
        override fun process(library: Library): Boolean {
          result.set(findInLibrary(libraryFileName, relativePath, library, resolver))
          return result.isNull
        }
      })
      return !result.isNull
    }

    private fun findInLibrary(libraryFileName: String, relativePath: String, library: Library, resolver: FileResolver): PathInfo? {
      for (rootType in ORDER_ROOT_TYPES.value) {
        for (root in library.getFiles(rootType)) {
          if (StringUtil.equalsIgnoreCase(root.nameSequence, libraryFileName)) {
            val result = resolver.resolve(relativePath, root, isLibrary = true) ?: continue
            return result
          }
        }
      }
      return null
    }

    private fun getInfoForDocJar(file: VirtualFile, project: Project): PathInfo? {
      val javaDocRootType = getJavadocOrderRootType() ?: return null

      class LibraryProcessor : Processor<Library> {
        var result: PathInfo? = null
        var module: Module? = null

        override fun process(library: Library): Boolean {
          for (root in library.getFiles(javaDocRootType)) {
            if (VfsUtilCore.isAncestor(root, file, false)) {
              result = PathInfo(null, file, root, getModuleNameQualifier(project, module), true)
              return false
            }
          }
          return true
        }
      }

      val processor = LibraryProcessor()
      runReadAction {
        val moduleManager = ModuleManager.getInstance(project)
        for (module in moduleManager.modules) {
          if (module.isDisposed) {
            continue
          }

          processor.module = module
          ModuleRootManager.getInstance(module).orderEntries().forEachLibrary(processor)
          if (processor.result != null) {
            return processor.result
          }
        }

        processor.module = null
        for (library in LibraryTablesRegistrar.getInstance().getLibraryTable(project).libraries) {
          if (!processor.process(library)) {
            return processor.result
          }
        }
      }

      return null
    }

    private fun getModuleNameQualifier(project: Project, module: Module?): String? {
      if (module != null && PlatformUtils.isIntelliJ() && !(module.name.equals(project.name, ignoreCase = true) || compareNameAndProjectBasePath(module.name, project))) {
        return module.name
      }
      return null
    }

    private fun findByRelativePath(path: String, roots: Array<VirtualFile>, resolver: FileResolver, moduleName: String?): PathInfo? {
      for (root in roots) {
        val result = resolver.resolve(path, root, moduleName) ?: continue
        return result
      }
      return null
    }

    private fun findByRelativePath(project: Project, path: String, modules: Array<Module>, rootProvider: RootProvider, resolver: FileResolver): PathInfo? {
      for (module in modules) {
        if (!module.isDisposed) {
          val moduleRootManager = ModuleRootManager.getInstance(module)
          val result = findByRelativePath(path, rootProvider.getRoots(moduleRootManager), resolver, null)
          if (result != null) {
            result.moduleName = getModuleNameQualifier(project, module)
            return result
          }
        }
      }
      return null
    }
  }
}