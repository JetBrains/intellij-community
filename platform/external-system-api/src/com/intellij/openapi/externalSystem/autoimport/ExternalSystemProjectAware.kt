// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemSettingsFilesModificationContext.Event
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemSettingsFilesModificationContext.ReloadStatus
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration

interface ExternalSystemProjectAware {

  val projectId: ExternalSystemProjectId

  /**
   * Collects settings files which will be watched.
   * This property can be called from any thread context to reduce UI freezes and CPU usage.
   * Result will be cached, so settings files should be equals between reloads.
   */
  val settingsFiles: Set<String>

  fun subscribe(listener: ExternalSystemProjectListener, parentDisposable: Disposable)

  fun reloadProject(context: ExternalSystemProjectReloadContext)

  /**
   * Internal. Please see implementation limitations.
   *
   * This property defines a delay for the "smart" project sync request.
   * Usually, the "smart" sync is requested after changes in the [settingsFiles].
   *
   * Note: All external systems sync events are dispatched and merged by the same [com.intellij.util.ui.update.MergingUpdateQueue].
   * Therefore, this value can be overridden by the greater [smartProjectReloadDelay].
   * For example, between default (3 seconds) and zero delays will be chosen the default delay.
   *
   * A null value means default [smartProjectReloadDelay] that is equals to 3 seconds.
   */
  @get:ApiStatus.Internal
  val smartProjectReloadDelay: Duration? get() = null

  /**
   * Experimental. Please see implementation limitations.
   *
   * This function allows ignoring settings files events. For example Idea can ignore external
   * changes during reload.
   *
   * Note: All ignored modifications cannot be reverted. So if we ignore only create events
   * then if settings file was created and removed then we mark project status as modified,
   * because we can restore only delete event by CRCs.
   *
   * Note: Now create event and register settings file (file appear in settings files list) event
   * is same (also for delete and unregister), because we cannot find settings file if it doesn't
   * exist in file system. Usually settings files list forms during file system scanning.
   *
   * Note: This function will be called on EDT. Please make only trivial checks like:
   * ```context.modificationType == EXTERNAL && path.endsWith(".my-ext")```
   *
   * Note: [ReloadStatus.JUST_FINISHED] is used to ignore create events during reload. But we cannot
   * replace it by [ReloadStatus.IN_PROGRESS], because we should merge create and all next update
   * events into one create event and ignore all of them. So [ReloadStatus.JUST_FINISHED] true only
   * at the end of reload.
   */
  @ApiStatus.Experimental
  fun isIgnoredSettingsFileEvent(path: String, context: ExternalSystemSettingsFilesModificationContext): Boolean =
    context.reloadStatus == ReloadStatus.JUST_STARTED ||
    context.reloadStatus == ReloadStatus.JUST_FINISHED && context.event == Event.CREATE

  /**
   * Experimental. Please see implementation limitations.
   *
   * This function allows adjusting modification type of the modified file. For example, Idea can change
   * [ExternalSystemModificationType.INTERNAL] to [ExternalSystemModificationType.HIDDEN] to skip auto reloading.
   *
   * Note: This function will be called on EDT. Please make only trivial checks like:
   * ```modificationType == INTERNAL && path.endsWith(".hidden")```
   */
  @ApiStatus.Experimental
  fun adjustModificationType(path: String, modificationType: ExternalSystemModificationType): ExternalSystemModificationType =
    modificationType
}