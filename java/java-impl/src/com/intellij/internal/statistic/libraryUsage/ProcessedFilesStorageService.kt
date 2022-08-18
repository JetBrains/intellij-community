// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.libraryUsage

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.DigestUtil
import com.intellij.util.xmlb.annotations.XMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Service(Service.Level.PROJECT)
@State(
  name = "ProcessedFilesStorage",
  storages = [Storage(StoragePathMacros.CACHE_FILE)],
  reportStatistic = false,
  reloadable = false,
)
class ProcessedFilesStorageService :
  PersistentStateComponentWithModificationTracker<ProcessedFilesStorageService.MyState>,
  Disposable {
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

  class MyState {
    @XMap
    @JvmField
    val timestamps: MutableMap<String, Long> = ConcurrentHashMap()
  }

  fun isVisited(vFile: VirtualFile): Boolean {
    val fileTime = state.timestamps[vFile.pathMd5Hash()] ?: return false
    val currentTime = System.currentTimeMillis()
    return !isDayPassed(lastTime = fileTime, currentTime = currentTime)
  }

  fun visit(vFile: VirtualFile): Boolean {
    val hash = vFile.pathMd5Hash()
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

  private fun VirtualFile.pathMd5Hash(): String = DigestUtil.md5Hex(path.encodeToByteArray())

  override fun dispose() = Unit

  companion object {
    fun getInstance(project: Project): ProcessedFilesStorageService = project.service()
  }
}

private fun isDayPassed(lastTime: Long, currentTime: Long): Boolean = TimeUnit.MILLISECONDS.toDays(currentTime - lastTime) >= 1
