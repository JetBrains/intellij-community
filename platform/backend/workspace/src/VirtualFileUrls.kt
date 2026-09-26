// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("VirtualFileUrls")

package com.intellij.platform.backend.workspace

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.platform.backend.workspace.impl.VirtualFileUrlWithVirtualFile
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlIndex
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager

/**
 * Returns instance of [VirtualFile] corresponding to this [VirtualFileUrl] or `null` if there is no a file with such URL in the Virtual
 * File System.
 *
 * Usually this property returns an instance cached in a field, so it's cheap. If no value is cached, it'll fall back to use
 * [VirtualFileManager.findFileByUrl].
 */
public val VirtualFileUrl.virtualFile: VirtualFile?
  get() {
    val file = if (this is VirtualFilePointer) file else VirtualFileManager.getInstance().findFileByUrl(url)
    if (file != null && this is VirtualFileUrlWithVirtualFile) {
      cacheVirtualFile(file)
    }
    return file
  }

/**
 * Returns an instance of [VirtualFileUrl] describing this [VirtualFile]. **Note** that if URL of this file wasn't registered in 
 * [virtualFileManager], a new instance will be created and stored in [virtualFileManager] until the project is closed. So this function 
 * should be used only if you're going to store the result in a property of some [com.intellij.platform.workspace.storage.WorkspaceEntity].
 *
 * **Important Note:** method can return different instances of `VirtualFileUrl` for the same `VirtualFile`, e.g. if the file was moved.
 */
@Deprecated("Use VirtualFileUrlManager.get to find VirtualFileUrl, storeAndGet to create a new instance for an entity. To find entities by a VirtualFile use VirtualFileUrlIndex.findEntitiesByVirtualFile",
            ReplaceWith("virtualFileManager.get(this)"))
public fun VirtualFile.toVirtualFileUrl(virtualFileManager: VirtualFileUrlManager): VirtualFileUrl {
  return virtualFileManager.storeAndGet(this)
}

/**
 * Search [WorkspaceEntity] which contain required [VirtualFile] as [VirtualFileUrl]
 * 
 * @return the sequence of entities which contain required [VirtualFile]'s url
 */
public fun VirtualFileUrlIndex.findEntitiesByVirtualFile(
  virtualFile: VirtualFile,
  virtualFileUrlManager: VirtualFileUrlManager,
): Sequence<WorkspaceEntity> {
  val virtualFileUrl: VirtualFileUrl = virtualFileUrlManager.get(virtualFile.url) ?: return emptySequence()
  return this.findEntitiesByUrl(virtualFileUrl)
}

/**
 * @return an existing instance of [VirtualFileUrl] for the given [VirtualFile]'s URL or `null` if no instance was registered.
 * @see VirtualFileUrlManager.get
 */
public fun VirtualFileUrlManager.get(virtualFile: VirtualFile): VirtualFileUrl? {
  return get(virtualFile.url)?.also { it.cacheIfWithVirtualFile(virtualFile) }
}

/**
 * Returns an existing, or creates and stores a new instance of [VirtualFileUrl] instance for the given [VirtualFile]'s URL in the Virtual File System 
 * format. This function **should be used only to** obtain an instance which will be stored in a property of a workspace model entity.
 * It must not be used for other purposes (e.g., to convert between different URL formats or to find [VirtualFile][com.intellij.openapi.vfs.VirtualFile]),
 * because all created URLs are stored in the shared data structures until the project is closed.
 * @see VirtualFileUrlManager.storeAndGet
 */
public fun VirtualFileUrlManager.storeAndGet(virtualFile: VirtualFile): VirtualFileUrl {
  return storeAndGet(virtualFile.url).also { it.cacheIfWithVirtualFile(virtualFile) }
}

private fun VirtualFileUrl.cacheIfWithVirtualFile(virtualFile: VirtualFile) {
  if (this is VirtualFileUrlWithVirtualFile) {
    this.cacheVirtualFile(virtualFile)
  }
}