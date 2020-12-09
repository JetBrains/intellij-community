// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.ide.IdeBundle
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathMacros
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.TrackingPathMacroSubstitutor
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.impl.stores.UnknownMacroNotification
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.project.impl.ProjectMacrosUtil
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.createDirectories
import com.intellij.util.io.systemIndependentPath
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.nio.file.Path
import java.util.*

@NonNls const val NOTIFICATION_GROUP_ID = "Load Error"

@TestOnly
@NonNls
var DEBUG_LOG: String? = null

@ApiStatus.Internal
fun doNotify(macros: MutableSet<String>, project: Project, substitutorToStore: Map<TrackingPathMacroSubstitutor, IComponentStore>) {
  val joinedMacros = HtmlChunk.text(macros.joinToString(", ")).italic().toString()
  val mainMessage =
    if (macros.size == 1) {
      IdeBundle.message("notification.content.unknown.macros.error.one.macros.undefined", joinedMacros)
    }
    else {
      IdeBundle.message("notification.content.unknown.macros.error.many.macroses.undefined", joinedMacros)
    }

  val description = IdeBundle.message("notification.content.unknown.macros.error.description",
                                      ApplicationNamesInfo.getInstance().productName)
  val message = HtmlBuilder().appendRaw(mainMessage).br().br().appendRaw(description).toString()
  val title = IdeBundle.message("notification.title.unknown.macros.error")
  UnknownMacroNotification(NOTIFICATION_GROUP_ID, title, message, NotificationType.ERROR, null, macros).apply {
    addAction(NotificationAction.createSimple(IdeBundle.message("notification.action.unknown.macros.error.fix")) {
      checkUnknownMacros(project, true, macros, substitutorToStore)
    })
  }.notify(project)
}

@ApiStatus.Internal
fun checkUnknownMacros(project: Project, notify: Boolean) {
  // use linked set/map to get stable results
  val unknownMacros = LinkedHashSet<String>()
  val substitutorToStore = LinkedHashMap<TrackingPathMacroSubstitutor, IComponentStore>()
  collect(project, unknownMacros, substitutorToStore)
  for (module in ModuleManager.getInstance(project).modules) {
    collect(module, unknownMacros, substitutorToStore)
  }

  if (unknownMacros.isEmpty()) {
    return
  }

  if (notify) {
    doNotify(unknownMacros, project, substitutorToStore)
    return
  }

  checkUnknownMacros(project, false, unknownMacros, substitutorToStore)
}

private fun checkUnknownMacros(project: Project,
                               showDialog: Boolean,
                               unknownMacros: MutableSet<String>,
                               substitutorToStore: Map<TrackingPathMacroSubstitutor, IComponentStore>) {
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

      for (notification in notificationManager.getNotificationsOfType(UnknownMacroNotification::class.java, project)) {
        if (unknownMacros.containsAll(notification.macros)) {
          notification.expire()
        }
      }

      store.reloadStates(components, project.messageBus)
    }
    else if (Messages.showYesNoDialog(project, IdeBundle.message("dialog.message.component.could.not.be.reloaded"),
                                      IdeBundle.message("dialog.title.configuration.changed"),
                                      Messages.getQuestionIcon()) == Messages.YES) {
      StoreReloadManager.getInstance().reloadProject(project)
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
  var virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(file.systemIndependentPath)
  if (virtualFile == null) {
    val parentFile = file.parent
    parentFile.createDirectories()

    // need refresh if the directory has just been created
    val parentVirtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(parentFile.systemIndependentPath)
                            ?: throw IOException(ProjectBundle.message("project.configuration.save.file.not.found", parentFile))

    virtualFile = runAsWriteActionIfNeeded {
      parentVirtualFile.createChildData(requestor, file.fileName.toString())
    }
  }
  // internal .xml files written with BOM can cause problems, see IDEA-219913
  // (e.g. unable to backport them to 191/unwanted changed files when someone checks File Encodings|create new files with BOM)
  // so we forcibly remove BOM from storage .xmls
  if (virtualFile.bom != null) {
    virtualFile.bom = null
  }
  return virtualFile
}

// runWriteAction itself cannot do such check because in general case any write action must be tracked regardless of current action
@ApiStatus.Internal
inline fun <T> runAsWriteActionIfNeeded(crossinline runnable: () -> T): T {
  return when {
    ApplicationManager.getApplication().isWriteAccessAllowed -> runnable()
    else -> runWriteAction(runnable)
  }
}