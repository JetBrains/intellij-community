// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.configurationStore

import com.intellij.ide.IdeBundle
import com.intellij.notification.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathMacros
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.TrackingPathMacroSubstitutor
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.project.impl.ProjectMacrosUtil
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.createDirectories
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

const val NOTIFICATION_GROUP_ID: String = "Load Error"

@TestOnly
var DEBUG_LOG: String? = null

@ApiStatus.Internal
fun doNotify(macros: MutableSet<@NlsSafe String>, project: Project, substitutorToStore: Map<TrackingPathMacroSubstitutor, IComponentStore>) {
  @Suppress("HardCodedStringLiteral") val joinedMacros = HtmlChunk.text(macros.joinToString(", ")).italic().toString()
  val mainMessage = if (macros.size == 1) IdeBundle.message("notification.content.unknown.macro.error", joinedMacros)
    else IdeBundle.message("notification.content.unknown.macros.error", joinedMacros)

  val description = IdeBundle.message("notification.content.unknown.macros.error.description",
                                      ApplicationNamesInfo.getInstance().productName)
  val message = HtmlBuilder().appendRaw(mainMessage).br().br().appendRaw(description).toString()
  val title = IdeBundle.message("notification.title.unknown.macros.error")
  UnknownMacroNotification(NOTIFICATION_GROUP_ID, title, message, NotificationType.ERROR, null, macros).apply {
    addAction(NotificationAction.createSimple(IdeBundle.message("notification.action.unknown.macros.error.fix")) {
      checkUnknownMacros(project = project, showDialog = true, unknownMacros = macros, substitutorToStore = substitutorToStore)
    })
  }.notify(project)
}

internal fun checkUnknownMacros(project: Project, notify: Boolean) {
  // use linked set/map to get stable results
  val unknownMacros = LinkedHashSet<String>()
  val substitutorToStore = LinkedHashMap<TrackingPathMacroSubstitutor, IComponentStore>()
  collect(componentManager = project, unknownMacros = unknownMacros, substitutorToStore = substitutorToStore)
  if (unknownMacros.isEmpty()) {
    return
  }

  if (notify) {
    doNotify(macros = unknownMacros, project = project, substitutorToStore = substitutorToStore)
    return
  }

  checkUnknownMacros(project = project, showDialog = false, unknownMacros = unknownMacros, substitutorToStore = substitutorToStore)
}

private fun checkUnknownMacros(
  project: Project,
  showDialog: Boolean,
  unknownMacros: MutableSet<String>,
  substitutorToStore: Map<TrackingPathMacroSubstitutor, IComponentStore>,
) {
  if (unknownMacros.isEmpty() || (showDialog && !ProjectMacrosUtil.checkMacros(project, HashSet(unknownMacros)))) {
    return
  }

  val pathMacros = PathMacros.getInstance()
  unknownMacros.removeAll { pathMacros.getValue(it).isNullOrBlank() && !pathMacros.isIgnoredMacroName(it) }
  if (unknownMacros.isEmpty()) {
    return
  }

  val notificationManager = NotificationsManager.getNotificationsManager()
  for ((substitutor, store) in substitutorToStore) {
    val components = substitutor.getComponents(unknownMacros)
    if (store.isReloadPossible(components)) {
      substitutor.invalidateUnknownMacros(unknownMacros)

      for (notification in notificationManager.getNotificationsOfType(
        UnknownMacroNotification::class.java, project)) {
        if (unknownMacros.containsAll(notification.macros)) {
          notification.expire()
        }
      }

      store.reloadStates(components)
    }
    else if (Messages.showYesNoDialog(project, IdeBundle.message("dialog.message.component.could.not.be.reloaded"),
                                      IdeBundle.message("dialog.title.configuration.changed"),
                                      Messages.getQuestionIcon()) == Messages.YES) {
      StoreReloadManager.getInstance(project).reloadProject()
    }
  }
}

private fun collect(componentManager: ComponentManager,
                    unknownMacros: MutableSet<String>,
                    substitutorToStore: MutableMap<TrackingPathMacroSubstitutor, IComponentStore>) {
  val store = componentManager.stateStore
  val substitutor = store.storageManager.macroSubstitutor as? TrackingPathMacroSubstitutor ?: return
  val macros = substitutor.getUnknownMacros(null)
  if (macros.isEmpty()) {
    return
  }

  unknownMacros.addAll(macros)
  substitutorToStore.put(substitutor, store)
}

@ApiStatus.Internal
fun getOrCreateVirtualFile(file: Path, requestor: StorageManagerFileWriteRequestor): VirtualFile {
  var virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(file.invariantSeparatorsPathString)
  if (virtualFile == null) {
    val parentFile = file.parent
    parentFile.createDirectories()

    // need refresh if the directory has just been created
    val parentVirtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(parentFile.invariantSeparatorsPathString)
                            ?: throw IOException(ProjectBundle.message("project.configuration.save.file.not.found", parentFile))

    virtualFile = runAsWriteActionIfNeeded {
      parentVirtualFile.createChildData(requestor, file.fileName.toString())
    }
  }
  // internal .xml files written with BOM can cause problems, see IDEA-219913
  // (e.g., unable to backport them to 191/unwanted changed files when someone checks File Encodings|create new files with BOM)
  // so we forcibly remove BOM from storage XMLs
  if (virtualFile.bom != null) {
    virtualFile.bom = null
  }
  return virtualFile
}

@ApiStatus.Internal
fun <T> runAsWriteActionIfNeeded(runnable: () -> T): T =
  ApplicationManager.getApplication().let { app ->
    if (app.isWriteAccessAllowed) runnable()
    else app.runWriteAction(Computable(runnable))
  }

class UnknownMacroNotification(
  groupId: String,
  title: @NlsContexts.NotificationTitle String,
  content: @NlsContexts.NotificationContent String,
  type: NotificationType,
  listener: NotificationListener?,
  val macros: Collection<String>,
) : Notification(groupId, title, content, type) {
  init {
    listener?.let {
      @Suppress("DEPRECATION")
      setListener(it)
    }
  }
}

/** Used in constructed configuration store events to trigger VFS content reloading for files updated via NIO. */
@ApiStatus.Internal
@JvmField
val RELOADING_STORAGE_WRITE_REQUESTOR = object : StorageManagerFileWriteRequestor { }
