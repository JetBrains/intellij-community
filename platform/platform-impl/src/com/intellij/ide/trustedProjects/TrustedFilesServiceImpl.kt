// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.trustedProjects

import com.intellij.ide.TrustedFiles
import com.intellij.ide.TrustedFilesService
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.ide.trustedProjects.TrustedProjectsLocator.LocatedProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.reopenVirtualFileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotifications
import com.intellij.util.ThreeState
import com.intellij.util.application
import com.intellij.util.containers.CollectionFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong

/**
 * The [TrustedFiles] implementation. The trust model itself is documented on [TrustedFiles];
 * this class only resolves a verdict and delegates the caching to [TrustedFilesCache].
 */
internal class TrustedFilesServiceImpl : TrustedFilesService {
  override fun isTrusted(file: VirtualFile, project: Project): Boolean {
    if (!Registry.`is`(TrustedFiles.SAFE_MODE_REGISTRY_KEY, false)) {
      return true
    }
    if (TrustedProjects.isTrustedCheckDisabled()) {
      return true
    }
    // the LightEdit project has its own trust model; the default project has no content of its own
    if (project.isDefault || project.isDisposed || LightEdit.owns(project)) {
      return true
    }
    return TrustedFilesCache.getInstance(project).isTrusted(file)
  }

  override fun isTrustDecidedByFile(file: VirtualFile, project: Project): Boolean {
    if (!Registry.`is`(TrustedFiles.SAFE_MODE_REGISTRY_KEY, false)) {
      return false
    }
    if (TrustedProjects.isTrustedCheckDisabled()) {
      return false
    }
    // the LightEdit project has its own trust model; the default project has no content of its own
    if (project.isDefault || project.isDisposed || LightEdit.owns(project)) {
      return false
    }
    val nioPath = file.fileSystem.getNioPath(file) ?: return false
    return ExternallyOpenedFiles.getInstance().isMarked(nioPath) &&
           TrustedProjectsLocator.locateProject(project).projectRoots.none { nioPath.startsWith(it) }
  }

  override fun markExternallyOpened(file: VirtualFile) {
    if (file.isDirectory) return
    val nioPath = file.fileSystem.getNioPath(file) ?: return
    ExternallyOpenedFiles.getInstance().mark(nioPath)
    for (project in ProjectManager.getInstanceIfCreated()?.openProjects ?: return) {
      project.serviceIfCreated<TrustedFilesCache>()?.dropVerdict(file)
    }
  }

  override fun markExternallyOpenedIfOutsideProject(file: VirtualFile, project: Project) {
    val nioPath = file.fileSystem.getNioPath(file) ?: return
    if (TrustedProjectsLocator.locateProject(project).projectRoots.any { nioPath.startsWith(it) }) return
    markExternallyOpened(file)
  }
}

/**
 * Caches per-file trust verdicts: [TrustedProjectsLocator.Companion.locateProject] fans out over an EP
 * and is called for every editor-provider selection and highlighting pass.
 *
 * Also drives the editor refresh on a trust change in both directions: an affected open editor
 * is reopened, so provider selection and highlighting settings are re-derived from the new verdict.
 */
@Service(Service.Level.PROJECT)
internal class TrustedFilesCache(private val project: Project, private val scope: CoroutineScope) : Disposable {
  companion object {
    fun getInstance(project: Project): TrustedFilesCache = project.service()
  }

  /**
   * The keys are weak, so the cache does not keep a file alive.
   * A file in use stays reachable through the VFS, so its verdict stays cached.
   * Only a local file enters the map, see [isTrusted].
   * A write and an invalidation are ordered by [invalidations], see [invalidate].
   */
  private val verdicts = CollectionFactory.createConcurrentWeakMap<VirtualFile, Boolean>()

  /**
   * Counts the invalidations. A verdict computed before an invalidation is stale, so [isTrusted]
   * does not cache it. The weak map has no atomic `computeIfAbsent`, so the counter takes its place.
   */
  private val invalidations = AtomicLong()

  init {
    application.messageBus.connect(this).subscribe(TrustedProjectsListener.TOPIC, object : TrustedProjectsListener {
      override fun onProjectTrusted(locatedProject: LocatedProject) = resetVerdicts()
      override fun onProjectUntrusted(locatedProject: LocatedProject) = resetVerdicts()
    })
    // the trusted roots may change when projects are linked or unlinked
    project.messageBus.connect(this).subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) {
        invalidate(file = null)
      }
    })
    Registry.get(TrustedFiles.SAFE_MODE_REGISTRY_KEY).addListener(object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) {
        invalidate(file = null)
      }
    }, this)
  }

  fun isTrusted(file: VirtualFile): Boolean {
    // a non-local file (remote, injected, a diff preview, a light file) keeps the project-level trust model;
    // it never enters the map, so the cache does not pin it
    val nioPath = file.fileSystem.getNioPath(file) ?: return true
    verdicts[file]?.let { return it }
    val invalidation = invalidations.get()
    val trusted = computeTrusted(nioPath)
    synchronized(verdicts) {
      if (invalidation == invalidations.get()) {
        verdicts.putIfAbsent(file, trusted)
      }
    }
    return trusted
  }

  /**
   * Drops the cached verdict of [file], or every verdict when [file] is `null`.
   * Returns the dropped verdict of [file].
   *
   * The counter bump and the drop happen under the same lock as the write in [isTrusted],
   * so a verdict computed before this call never lands in the map after it.
   */
  private fun invalidate(file: VirtualFile?): Boolean? {
    synchronized(verdicts) {
      invalidations.incrementAndGet()
      if (file == null) {
        verdicts.clear()
        return null
      }
      return verdicts.remove(file)
    }
  }

  /**
   * Drops the cached verdict of [file] after [TrustedFiles.markExternallyOpened]:
   * the file could be checked, and its editor opened, as trusted before the mark.
   * When the mark downgrades the verdict, the open editor is reopened in the safe mode.
   */
  fun dropVerdict(file: VirtualFile) {
    if (invalidate(file) == true && !isTrusted(file)) {
      scheduleEditorRefresh(listOf(file))
    }
  }

  private fun computeTrusted(nioPath: Path): Boolean {
    // Explicitly trusted locations override the project trust state.
    if (TrustedProjects.getProjectTrustedState(nioPath) == ThreeState.YES) {
      return true
    }

    val roots = TrustedProjectsLocator.locateProject(project).projectRoots
    val isInsideProject = roots.any { nioPath.startsWith(it) }

    // Files inside the project follow the project trust unless explicitly trusted above.
    if (isInsideProject) {
      return TrustedProjects.isProjectTrusted(project)
    }

    // IDE-internal files outside the project are not subject to per-file safe mode.
    if (!ExternallyOpenedFiles.getInstance().isMarked(nioPath)) {
      return true
    }

    // An externally opened file outside the project stays untrusted until its location is trusted.
    return false
  }

  /** Recomputes every cached verdict and reopens the editors of files that became trusted. */
  fun resetVerdicts() {
    val wasUntrusted = verdicts.entries.mapNotNull { (file, trusted) -> file.takeIf { !trusted } }
    invalidate(file = null)
    val upgraded = wasUntrusted.filter { it.isValid && isTrusted(it) }
    if (upgraded.isNotEmpty()) {
      scheduleEditorRefresh(upgraded)
    }
  }

  private fun scheduleEditorRefresh(files: List<VirtualFile>) {
    scope.launch(Dispatchers.EDT) {
      val fileEditorManager = FileEditorManager.getInstance(project)
      for (file in files) {
        if (file.isValid && fileEditorManager.isFileOpen(file)) {
          reopenVirtualFileEditor(project, file, file)
        }
      }
      EditorNotifications.getInstance(project).updateAllNotifications()
    }
  }

  override fun dispose() {
  }
}
