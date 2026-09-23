// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

/**
 * Determines whether [a file][VirtualFile] opened in a project may use the full IDE functionality
 * or has to stay in safe mode.
 *
 * For local files, an explicitly trusted location has the highest priority: a file under such
 * a location is trusted regardless of the trust state of the project it belongs to.
 *
 * Otherwise, a file inside the project's roots follows the project trust state
 * (see `com.intellij.ide.trustedProjects.TrustedProjects`).
 *
 * A file outside the project's roots is subject to safe mode only when it was opened from an external
 * source (see [markExternallyOpened]). Such a file remains untrusted until its location is explicitly
 * trusted. Files opened by the IDE itself outside the project roots, such as scratches, consoles,
 * or the custom VM options file, remain trusted.
 *
 * The user can explicitly trust a file location from the editor banner
 * (`UntrustedFileNotificationProvider`).
 */
@ApiStatus.Experimental
object TrustedFiles {
  @ApiStatus.Internal
  const val SAFE_MODE_REGISTRY_KEY: String = "ide.untrusted.files.safe.mode"

  /**
   * Returns `true` when [file] opened in [project] may use the full IDE functionality.
   *
   * The result takes into account explicit trust of the file location, the trust state of the
   * containing project, and whether a file outside the project was opened from an external source.
   *
   * Functionality that can execute code from the file or pass it to external tools must not run
   * when this method returns `false`.
   */
  @JvmStatic
  fun isTrusted(file: VirtualFile, project: Project): Boolean =
    serviceOrNull<TrustedFilesService>()?.isTrusted(file, project) ?: true

  /**
   * Returns `true` when [file] is an externally opened local file outside the roots of [project]
   * and therefore is a safe-mode candidate on its own (see [markExternallyOpened]).
   *
   * The result does not depend on the current trust state of the file location: explicitly trusting
   * the location changes the file's trust verdict, but does not change whether the file is classified
   * as an externally opened file outside the project.
   *
   * When safe mode is disabled, trust checks are disabled, or the project does not participate in
   * this trust model, the method returns `false`.
   */
  @ApiStatus.Internal
  @JvmStatic
  fun isTrustDecidedByFile(file: VirtualFile, project: Project): Boolean =
    serviceOrNull<TrustedFilesService>()?.isTrustDecidedByFile(file, project) ?: false

  /**
   * Marks [file] as opened from an external source: the system file manager, the command line,
   * a protocol URI, or drag and drop.
   *
   * The mark affects files outside project roots: such a file is kept in safe mode until its
   * location is explicitly trusted. For files inside project roots, the mark does not affect
   * the trust verdict; they follow the project trust unless their location is explicitly trusted.
   *
   * Call this method before the editor opens: editor provider selection reads the trust state.
   * The mark is stored at the application level, so it survives IDE restart and reopening the file
   * from Recent Files. The mark works independently of [SAFE_MODE_REGISTRY_KEY], so the state remains
   * available if the registry value changes later.
   */
  @ApiStatus.Internal
  @JvmStatic
  fun markExternallyOpened(file: VirtualFile) {
    serviceOrNull<TrustedFilesService>()?.markExternallyOpened(file)
  }

  /**
   * Marks [file] like [markExternallyOpened] when it lies outside the roots of [project].
   *
   * A navigation UI that can reach any local file, for example the navigation bar, calls this method
   * for the file it opens. A file inside the project roots is governed by the project trust,
   * so its mark would only take a slot in the store.
   */
  @ApiStatus.Internal
  @JvmStatic
  fun markExternallyOpenedIfOutsideProject(file: VirtualFile, project: Project) {
    serviceOrNull<TrustedFilesService>()?.markExternallyOpenedIfOutsideProject(file, project)
  }
}

/**
 * The implementation seam behind [TrustedFiles]. Call [TrustedFiles] instead of this service.
 */
@ApiStatus.Internal
interface TrustedFilesService {
  fun isTrusted(file: VirtualFile, project: Project): Boolean

  fun isTrustDecidedByFile(file: VirtualFile, project: Project): Boolean

  fun markExternallyOpened(file: VirtualFile)

  fun markExternallyOpenedIfOutsideProject(file: VirtualFile, project: Project)
}
