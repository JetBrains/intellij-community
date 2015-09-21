package org.jetbrains.builtInWebServer

import com.google.common.cache.CacheBuilder
import com.intellij.ProjectTopics
import com.intellij.openapi.application.Application
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootAdapter
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import java.util.concurrent.TimeUnit

/**
 * Implement [WebServerRootsProvider] to add your provider
 */
class WebServerPathToFileManager(application: Application, private val project: Project) {
  val pathToFileCache = CacheBuilder.newBuilder().maximumSize(512).expireAfterAccess(10, TimeUnit.MINUTES).build<String, VirtualFile>()
  // time to expire should be greater than pathToFileCache
  private val fileToRoot = CacheBuilder.newBuilder().maximumSize(512).expireAfterAccess(11, TimeUnit.MINUTES).build<VirtualFile, PathInfo>()

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

  private fun clearCache() {
    pathToFileCache.invalidateAll()
    fileToRoot.invalidateAll()
  }

  @JvmOverloads fun get(path: String, cacheResult: Boolean = true): VirtualFile? {
    var result: VirtualFile? = pathToFileCache.getIfPresent(path)
    if (result == null || !result.isValid) {
      result = findByRelativePath(project, path)
      if (cacheResult && result != null && result.isValid) {
        pathToFileCache.put(path, result)
      }
    }
    return result
  }

  fun getPath(file: VirtualFile): String? {
    val pathInfo = getRoot(file)
    return pathInfo?.path
  }

  fun getRoot(child: VirtualFile): PathInfo? {
    var result: PathInfo? = fileToRoot.getIfPresent(child)
    if (result == null) {
      for (rootsProvider in WebServerRootsProvider.EP_NAME.extensions) {
        result = rootsProvider.getRoot(child, project)
        if (result != null) {
          fileToRoot.put(child, result)
          break
        }
      }
    }
    return result
  }

  fun findByRelativePath(project: Project, path: String): VirtualFile? {
    for (rootsProvider in WebServerRootsProvider.EP_NAME.extensions) {
      val result = rootsProvider.resolve(path, project)
      if (result != null) {
        fileToRoot.put(result.child, result)
        return result.child
      }
    }
    return null
  }

  fun getResolver(path: String) = if (path.isEmpty()) EMPTY_PATH_RESOLVER else RELATIVE_PATH_RESOLVER

  companion object {
    private val RELATIVE_PATH_RESOLVER = object : FileResolver {
      override fun resolve(path: String, parent: VirtualFile): VirtualFile? {
        return parent.findFileByRelativePath(path)
      }
    }

    private val EMPTY_PATH_RESOLVER = object : FileResolver {
      override fun resolve(path: String, parent: VirtualFile): VirtualFile? {
        return findIndexFile(parent)
      }
    }

    @JvmStatic fun getInstance(project: Project): WebServerPathToFileManager {
      return ServiceManager.getService(project, WebServerPathToFileManager::class.java)
    }
  }
}


interface FileResolver {
  fun resolve(path: String, parent: VirtualFile): VirtualFile?
}