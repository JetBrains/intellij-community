// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.trustedProjects

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
 * Per-file trust: whether [a file][VirtualFile] opened in a project may use the full IDE functionality,
 * or has to stay in the safe mode (plain text editor, no inspections, no external tools).
 *
 * Only a file the user opened from an external source is a safe-mode candidate
 * (see [markExternallyOpened]). Such a file is untrusted until it lies inside the project's own roots
 * (the project itself is guarded by the project-level trust check, see [TrustedProjects]) or under
 * an explicitly trusted location (see [TrustedProjects.isProjectTrusted] by path). The user can trust
 * the file location from the editor banner (`UntrustedFileNotificationProvider`).
 *
 * A file the IDE opens on its own (a scratch, a console, the custom VM options file, a library source)
 * is never marked, so it stays trusted.
 */
@ApiStatus.Experimental
object TrustedFiles {
  @ApiStatus.Internal
  const val SAFE_MODE_REGISTRY_KEY: String = "ide.untrusted.files.safe.mode"

  /**
   * Returns `true` when [file] opened in [project] may use the full IDE functionality.
   *
   * Functionality that can execute code from the file or pass it to external tools must not run
   * when this method returns `false`.
   */
  @JvmStatic
  fun isTrusted(file: VirtualFile, project: Project): Boolean {
    if (!Registry.`is`(SAFE_MODE_REGISTRY_KEY, false)) {
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

  /**
   * Returns `true` when per-file trust governs [file] in [project]: the file is an externally
   * opened local file outside the project's roots (see [markExternallyOpened]).
   *
   * The result does not depend on the trust state of the file location. When the safe mode
   * is off, or the trust check is disabled, the project-level trust governs every file,
   * so the method returns `false`.
   */
  @ApiStatus.Internal
  @JvmStatic
  fun isTrustDecidedByFile(file: VirtualFile, project: Project): Boolean {
    if (!Registry.`is`(SAFE_MODE_REGISTRY_KEY, false)) {
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

  /**
   * Marks [file] as opened from an external source: the system file manager, the command line,
   * a protocol URI, or drag and drop. Only a marked file is a safe-mode candidate.
   *
   * Call this method before the editor opens: editor provider selection reads the trust state.
   * The mark is stored at the application level, so the file stays a safe-mode candidate after
   * a restart and after a reopen from Recent Files. The mark works independently of
   * [SAFE_MODE_REGISTRY_KEY], so the state is correct when the registry value changes later.
   */
  @ApiStatus.Internal
  @JvmStatic
  fun markExternallyOpened(file: VirtualFile) {
    val nioPath = file.fileSystem.getNioPath(file) ?: return
    val evicted = ExternallyOpenedFiles.getInstance().mark(nioPath)
    for (project in ProjectManager.getInstanceIfCreated()?.openProjects ?: return) {
      val cache = project.serviceIfCreated<TrustedFilesCache>() ?: continue
      cache.dropVerdict(file)
      if (evicted) {
        // an evicted path is unmarked now: recompute its verdict and lift the safe mode from its editor
        cache.resetVerdicts()
      }
    }
  }
}

/**
 * Caches per-file trust verdicts: [TrustedProjectsLocator.locateProject] fans out over an EP
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
    // only a file opened from an external source is a safe-mode candidate;
    // an IDE-internal file (a scratch, a console, the custom VM options file) stays trusted
    if (!ExternallyOpenedFiles.getInstance().isMarked(nioPath)) {
      return true
    }
    val roots = TrustedProjectsLocator.locateProject(project).projectRoots
    return roots.any { nioPath.startsWith(it) } ||
           // UNSURE is not enough: a marked outside file is untrusted until its location is explicitly trusted
           TrustedProjects.getProjectTrustedState(nioPath) == ThreeState.YES
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
