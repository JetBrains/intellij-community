package org.jetbrains.builtInWebServer

import com.google.common.cache.CacheBuilder
import com.intellij.ProjectTopics
import com.intellij.openapi.application.Application
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootAdapter
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.containers.computeOrNull
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Implement [WebServerRootsProvider] to add your provider
 */
class WebServerPathToFileManager(application: Application, private val project: Project) {
  val pathToInfoCache = CacheBuilder.newBuilder().maximumSize(512).expireAfterAccess(10, TimeUnit.MINUTES).build<String, PathInfo>()
  // time to expire should be greater than pathToFileCache
  private val virtualFileToPathInfo = CacheBuilder.newBuilder().maximumSize(512).expireAfterAccess(11, TimeUnit.MINUTES).build<VirtualFile, PathInfo>()

  init {
    application.messageBus.connect(project).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener.Adapter() {
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
    project.messageBus.connect().subscribe(ProjectTopics.PROJECT_ROOTS, object : ModuleRootAdapter() {
      override fun rootsChanged(event: ModuleRootEvent) {
        clearCache()
      }
    })
  }

  companion object {
    @JvmStatic fun getInstance(project: Project) = ServiceManager.getService(project, WebServerPathToFileManager::class.java)
  }

  private fun clearCache() {
    pathToInfoCache.invalidateAll()
    virtualFileToPathInfo.invalidateAll()
  }

  @JvmOverloads fun findVirtualFile(path: String, cacheResult: Boolean = true): VirtualFile? {
    val pathInfo = getPathInfo(path, cacheResult) ?: return null
    return pathInfo.file ?: LocalFileSystem.getInstance().findFileByIoFile(pathInfo.ioFile!!)
  }

  @JvmOverloads fun getPathInfo(path: String, cacheResult: Boolean = true): PathInfo? {
    var pathInfo = pathToInfoCache.getIfPresent(path)
    if (pathInfo == null || !pathInfo.isValid) {
      pathInfo = doFindByRelativePath(path)
      if (cacheResult && pathInfo != null && pathInfo.isValid) {
        pathToInfoCache.put(path, pathInfo)
      }
    }
    return pathInfo
  }

  fun getPath(file: VirtualFile) = getPathInfo(file)?.path

  private fun getPathInfo(child: VirtualFile): PathInfo? {
    var result = virtualFileToPathInfo.getIfPresent(child)
    if (result == null) {
      result = WebServerRootsProvider.EP_NAME.extensions.computeOrNull { it.getPathInfo(child, project) }
      if (result != null) {
        virtualFileToPathInfo.put(child, result)
      }
    }
    return result
  }

  internal fun doFindByRelativePath(path: String): PathInfo? {
    val result = WebServerRootsProvider.EP_NAME.extensions.computeOrNull { it.resolve(path, project) } ?: return null
    if (result.file != null) {
      virtualFileToPathInfo.put(result.file, result)
    }
    return result
  }

  fun getResolver(path: String) = if (path.isEmpty()) EMPTY_PATH_RESOLVER else RELATIVE_PATH_RESOLVER
}

interface FileResolver {
  fun resolve(path: String, root: VirtualFile, moduleName: String? = null, isLibrary: Boolean = false): PathInfo?
}

private val RELATIVE_PATH_RESOLVER = object : FileResolver {
  override fun resolve(path: String, root: VirtualFile, moduleName: String?, isLibrary: Boolean): PathInfo? {
    // WEB-17691 built-in server doesn't serve files it doesn't have in the project tree
    // temp:// reports isInLocalFileSystem == true, but it is not true
    if (root.isInLocalFileSystem && root.fileSystem == LocalFileSystem.getInstance()) {
      val file = File(root.path, path)
      if (file.exists()) {
        return PathInfo(file, null, root, moduleName, isLibrary)
      }
      else {
        return null
      }
    }
    else {
      val file = root.findFileByRelativePath(path) ?: return null
      return PathInfo(null, file, root, moduleName, isLibrary)
    }
  }
}

private val EMPTY_PATH_RESOLVER = object : FileResolver {
  override fun resolve(path: String, root: VirtualFile, moduleName: String?, isLibrary: Boolean): PathInfo? {
    val file = findIndexFile(root) ?: return null
    return PathInfo(null, file, root, moduleName, isLibrary)
  }
}