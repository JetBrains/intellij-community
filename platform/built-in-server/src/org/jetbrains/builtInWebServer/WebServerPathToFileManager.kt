// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.builtInWebServer

import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.ProjectTopics
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.AdditionalLibraryRootsListener
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.SmartList
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists

private const val cacheSize: Long = 4096 * 4

/**
 * Implement [WebServerRootsProvider] to add your provider
 */
class WebServerPathToFileManager(private val project: Project) {
  internal val pathToInfoCache = Caffeine.newBuilder().maximumSize(cacheSize).expireAfterAccess(10, TimeUnit.MINUTES).build<String, PathInfo>()
  // time to expire should be greater than pathToFileCache
  private val virtualFileToPathInfo = Caffeine.newBuilder().maximumSize(cacheSize).expireAfterAccess(11, TimeUnit.MINUTES).build<VirtualFile, PathInfo>()

  internal val pathToExistShortTermCache = Caffeine.newBuilder().maximumSize(cacheSize).expireAfterAccess(5, TimeUnit.SECONDS).build<String, Boolean>()

  /**
   * https://youtrack.jetbrains.com/issue/WEB-25900
   *
   * Compute suitable roots for oldest parent (web/foo/my/file.dart -> oldest is web and we compute all suitable roots for it in advance) to avoid linear search
   * (i.e. to avoid two queries for root if files web/foo and web/bar requested if root doesn't have web dir)
   */
  internal val parentToSuitableRoot = Caffeine
    .newBuilder()
    .maximumSize(cacheSize).expireAfterAccess(10, TimeUnit.MINUTES)
    .build<String, List<SuitableRoot>>(CacheLoader { path ->
      val suitableRoots = SmartList<SuitableRoot>()
      var moduleQualifier: String? = null
      val modules = runReadAction { ModuleManager.getInstance(project).modules }
      for (rootProvider in RootProvider.values()) {
        for (module in modules) {
          if (module.isDisposed) {
            continue
          }

          for (root in rootProvider.getRoots(module.rootManager)) {
            if (root.findChild(path) != null) {
              if (moduleQualifier == null) {
                moduleQualifier = getModuleNameQualifier(project, module)
              }
              suitableRoots.add(SuitableRoot(root, moduleQualifier))
            }
          }
        }
      }
      suitableRoots
    })

  init {
    ApplicationManager.getApplication().messageBus.connect (project).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        for (event in events) {
          if (event is VFileContentChangeEvent) {
            val file = event.file
            for (rootsProvider in WebServerRootsProvider.EP_NAME.extensions) {
              if (rootsProvider.isClearCacheOnFileContentChanged(file)) {
                clearCache()
                break
              }
            }
          }
          else {
            clearCache()
            break
          }
        }
      }
    })
    project.messageBus.connect().subscribe(ProjectTopics.PROJECT_ROOTS, object : ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) {
        clearCache()
      }
    })
    project.messageBus.connect().subscribe(AdditionalLibraryRootsListener.TOPIC,
                                           AdditionalLibraryRootsListener { _, _, _, _ ->
                                             clearCache()
                                           })
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.service<WebServerPathToFileManager>()
  }

  private fun clearCache() {
    pathToInfoCache.invalidateAll()
    virtualFileToPathInfo.invalidateAll()
    pathToExistShortTermCache.invalidateAll()
    parentToSuitableRoot.invalidateAll()
  }

  @JvmOverloads
  fun findVirtualFile(path: String, cacheResult: Boolean = true, pathQuery: PathQuery = defaultPathQuery): VirtualFile? {
    return getPathInfo(path, cacheResult, pathQuery)?.getOrResolveVirtualFile()
  }

  @JvmOverloads
  fun getPathInfo(path: String, cacheResult: Boolean = true, pathQuery: PathQuery = defaultPathQuery): PathInfo? {
    var pathInfo = pathToInfoCache.getIfPresent(path)
    if (pathInfo == null || !pathInfo.isValid) {
      if (pathToExistShortTermCache.getIfPresent(path) == false) {
        return null
      }

      pathInfo = doFindByRelativePath(path, pathQuery)
      if (cacheResult) {
        if (pathInfo != null && pathInfo.isValid) {
          pathToInfoCache.put(path, pathInfo)
        }
        else {
          pathToExistShortTermCache.put(path, false)
        }
      }
    }
    return pathInfo
  }

  fun getPath(file: VirtualFile): String? = getPathInfo(file)?.path

  fun getPathInfo(child: VirtualFile): PathInfo? {
    var result = virtualFileToPathInfo.getIfPresent(child)
    if (result == null) {
      result = WebServerRootsProvider.EP_NAME.extensionList.asSequence().map { it.getPathInfo(child, project) }.find { it != null }
      if (result != null) {
        virtualFileToPathInfo.put(child, result)
      }
    }
    return result
  }

  internal fun doFindByRelativePath(path: String, pathQuery: PathQuery): PathInfo? {
    val result = WebServerRootsProvider.EP_NAME.extensionList.asSequence().map { it.resolve(path, project, pathQuery) }.find { it != null } ?: return null
    result.file?.let {
      virtualFileToPathInfo.put(it, result)
    }
    return result
  }

  fun getResolver(path: String): FileResolver = if (path.isEmpty()) EMPTY_PATH_RESOLVER else RELATIVE_PATH_RESOLVER
}

interface FileResolver {
  fun resolve(path: String, root: VirtualFile, moduleName: String? = null, isLibrary: Boolean = false, pathQuery: PathQuery): PathInfo?
}

private val RELATIVE_PATH_RESOLVER = object : FileResolver {
  override fun resolve(path: String, root: VirtualFile, moduleName: String?, isLibrary: Boolean, pathQuery: PathQuery): PathInfo? {
    // WEB-17691 built-in server doesn't serve files it doesn't have in the project tree
    // temp:// reports isInLocalFileSystem == true, but it is not true
    if (pathQuery.useVfs || root.fileSystem != LocalFileSystem.getInstance() || path == ".htaccess" || path == "config.json") {
      return root.findFileByRelativePath(path)?.let { PathInfo(null, it, root, moduleName, isLibrary) }
    }

    val file = Paths.get(root.path, path)
    return if (file.exists()) {
      PathInfo(file, null, root, moduleName, isLibrary)
    }
    else {
      null
    }
  }
}

private val EMPTY_PATH_RESOLVER = object : FileResolver {
  override fun resolve(path: String, root: VirtualFile, moduleName: String?, isLibrary: Boolean, pathQuery: PathQuery): PathInfo? {
    val file = findIndexFile(root) ?: return null
    return PathInfo(null, file, root, moduleName, isLibrary)
  }
}

internal val defaultPathQuery = PathQuery()