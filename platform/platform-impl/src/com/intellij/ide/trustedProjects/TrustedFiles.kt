// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.trustedProjects

import com.intellij.ide.lightEdit.LightEdit
import com.intellij.ide.trustedProjects.TrustedProjectsLocator.LocatedProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.reopenVirtualFileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotifications
import com.intellij.util.ThreeState
import com.intellij.util.application
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-file trust: whether [a file][VirtualFile] opened in a project may use the full IDE functionality,
 * or has to stay in the safe mode (plain text editor, no inspections, no external tools).
 *
 * A file is trusted when it lies inside the project's own roots (the project itself is guarded
 * by the project-level trust check, see [TrustedProjects]) or under an explicitly trusted location
 * (see [TrustedProjects.isProjectTrusted] by path). Any other local file is untrusted until the user
 * trusts its location, e.g. from the editor banner (`UntrustedFileNotificationProvider`).
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
}

/**
 * Caches per-file trust verdicts: [TrustedProjectsLocator.locateProject] fans out over an EP
 * and is called for every editor-provider selection and highlighting pass.
 *
 * Also drives the trust-granted refresh: editors of files that became trusted are reopened,
 * so provider selection and highlighting settings are re-derived with the file trusted.
 */
@Service(Service.Level.PROJECT)
internal class TrustedFilesCache(private val project: Project) : Disposable {
  companion object {
    fun getInstance(project: Project): TrustedFilesCache = project.service()
  }

  private val verdicts = ConcurrentHashMap<VirtualFile, Boolean>()

  init {
    application.messageBus.connect(this).subscribe(TrustedProjectsListener.TOPIC, object : TrustedProjectsListener {
      override fun onProjectTrusted(locatedProject: LocatedProject) = onTrustChanged()
      override fun onProjectUntrusted(locatedProject: LocatedProject) = onTrustChanged()
    })
    // the trusted roots may change when projects are linked or unlinked
    project.messageBus.connect(this).subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) {
        verdicts.clear()
      }
    })
    Registry.get(TrustedFiles.SAFE_MODE_REGISTRY_KEY).addListener(object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) {
        verdicts.clear()
      }
    }, this)
  }

  fun isTrusted(file: VirtualFile): Boolean = verdicts.computeIfAbsent(file) { computeTrusted(it) }

  private fun computeTrusted(file: VirtualFile): Boolean {
    // non-local files (remote, injected, diff previews, etc.) keep the project-level trust model
    val nioPath = file.fileSystem.getNioPath(file) ?: return true
    val roots = TrustedProjectsLocator.locateProject(project).projectRoots
    return roots.isEmpty() ||
           roots.any { nioPath.startsWith(it) } ||
           // UNSURE is not enough: an outside file is untrusted until its location is explicitly trusted
           TrustedProjects.getProjectTrustedState(nioPath) == ThreeState.YES
  }

  private fun onTrustChanged() {
    val wasUntrusted = verdicts.entries.mapNotNull { (file, trusted) -> file.takeIf { !trusted } }
    verdicts.clear()
    val upgraded = wasUntrusted.filter { it.isValid && isTrusted(it) }
    if (upgraded.isNotEmpty()) {
      scheduleEditorRefresh(upgraded)
    }
  }

  private fun scheduleEditorRefresh(upgraded: List<VirtualFile>) {
    ApplicationManager.getApplication().invokeLater(
      {
        val fileEditorManager = FileEditorManager.getInstance(project)
        for (file in upgraded) {
          if (file.isValid && fileEditorManager.isFileOpen(file)) {
            reopenVirtualFileEditor(project, file, file)
          }
        }
        EditorNotifications.getInstance(project).updateAllNotifications()
      },
      project.disposed,
    )
  }

  override fun dispose() {
  }
}
