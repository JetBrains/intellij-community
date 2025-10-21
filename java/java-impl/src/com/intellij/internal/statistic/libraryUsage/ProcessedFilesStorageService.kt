// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.libraryUsage

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.DigestUtil
import com.intellij.util.io.hashToHexString
import com.intellij.util.xmlb.annotations.XMap
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Service(Service.Level.PROJECT)
@State(name = "ProcessedFilesStorage", storages = [Storage(StoragePathMacros.CACHE_FILE)], reloadable = false)
@ApiStatus.Internal
public class ProcessedFilesStorageService : PersistentStateComponentWithModificationTracker<ProcessedFilesStorageService.MyState>, Disposable {
  override fun initializeComponent() {
    if (dropOutdatedPaths()) {
      tracker.incModificationCount()
    }
  }

  @Volatile
  private var state = MyState()
  private val tracker = SimpleModificationTracker()

  override fun getState(): MyState = state

  override fun getStateModificationCount(): Long = tracker.modificationCount

  override fun loadState(state: MyState) {
    this.state = state
  }

  public class MyState {
    @XMap
    @JvmField
    public val timestamps: MutableMap<String, Long> = ConcurrentHashMap()
  }

  public fun isVisited(vFile: VirtualFile): Boolean {
    val fileTime = state.timestamps[pathMd5Hash(vFile)] ?: return false
    val currentTime = System.currentTimeMillis()
    return !isDayPassed(lastTime = fileTime, currentTime = currentTime)
  }

  public fun visit(vFile: VirtualFile): Boolean {
    val hash = pathMd5Hash(vFile)
    val currentTime = System.currentTimeMillis()
    val oldTime = state.timestamps.put(hash, currentTime)
    dropOutdatedPaths(currentTime)
    tracker.incModificationCount()
    return oldTime == null || isDayPassed(oldTime, currentTime)
  }

  /**
   * @return **true** if any elements were removed
   */
  private fun dropOutdatedPaths(currentTime: Long = System.currentTimeMillis()): Boolean {
    return state.timestamps.values.removeIf { isDayPassed(lastTime = it, currentTime = currentTime) }
  }

  override fun dispose(): Unit = Unit

  public companion object {
    public fun getInstance(project: Project): ProcessedFilesStorageService = project.service()
  }
}

private fun pathMd5Hash(virtualFile: VirtualFile): String = hashToHexString(virtualFile.path, DigestUtil.md5())

private fun isDayPassed(lastTime: Long, currentTime: Long): Boolean {
  return TimeUnit.MILLISECONDS.toDays(currentTime - lastTime) >= 1
}
