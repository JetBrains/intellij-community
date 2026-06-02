// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.remote

import com.intellij.json.JsonBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import com.jetbrains.jsonSchema.JsonSchemaCatalogProjectConfiguration
import com.jetbrains.jsonSchema.JsonSchemaMappingsProjectConfiguration
import com.jetbrains.jsonSchema.extension.JsonSchemaInfo
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import java.util.function.Function
import javax.swing.JComponent

class JsonSchemaCatalogNotificationProvider : EditorNotificationProvider, DumbAware {
  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
    val service = JsonSchemaService.Impl.get(project)
    if (!service.isApplicableToFile(file)) return null

    project.service<JsonSchemaCatalogNotificationUpdateService>().ensureCallbacksRegistered()

    val mappingsConfiguration = JsonSchemaMappingsProjectConfiguration.getInstance(project)
    if (mappingsConfiguration.isIgnoredFile(file)) return null
    if (service.getSchemaFilesForFile(file).isNotEmpty()) return null

    val entry = service.catalogManager.getSchemaCatalogEntryForFile(file) ?: return null
    val schemaInfo = createSchemaInfo(entry)
    return Function { fileEditor -> JsonSchemaCatalogNotificationPanel(fileEditor, project, file, schemaInfo) }
  }

  private fun createSchemaInfo(entry: com.jetbrains.jsonSchema.JsonSchemaCatalogEntry): JsonSchemaInfo {
    return JsonSchemaInfo(entry.url).apply {
      name = entry.name
      documentation = entry.description
    }
  }

  private fun applySchema(project: Project, file: VirtualFile, schemaInfo: JsonSchemaInfo) {
    JsonSchemaMappingsProjectConfiguration.getInstance(project).setSchemaForFile(schemaInfo, file)
    JsonSchemaService.Impl.get(project).reset()
    EditorNotifications.getInstance(project).updateNotifications(file)
  }

  private fun ignoreSchema(project: Project, file: VirtualFile) {
    JsonSchemaMappingsProjectConfiguration.getInstance(project).markAsIgnored(file)
    JsonSchemaService.Impl.get(project).reset()
    EditorNotifications.getInstance(project).updateNotifications(file)
  }

  private fun disableCatalogSuggestions(project: Project) {
    JsonSchemaCatalogProjectConfiguration.getInstance(project).setCatalogEnabled(false)
    JsonSchemaService.Impl.get(project).reset()
    EditorNotifications.getInstance(project).updateAllNotifications()
  }

  private inner class JsonSchemaCatalogNotificationPanel(
    fileEditor: FileEditor,
    project: Project,
    file: VirtualFile,
    schemaInfo: JsonSchemaInfo,
  ) : EditorNotificationPanel(fileEditor, Status.Info) {
    init {
      text = JsonBundle.message("schema.catalog.suggestion.text", schemaInfo.description)
      createActionLabel(JsonBundle.message("schema.catalog.suggestion.apply")) { applySchema(project, file, schemaInfo) }
      createActionLabel(JsonBundle.message("schema.widget.no.mapping")) { ignoreSchema(project, file) }
      createActionLabel(JsonBundle.message("schema.catalog.suggestion.disable")) { disableCatalogSuggestions(project) }
    }
  }

  @Service(Service.Level.PROJECT)
  private class JsonSchemaCatalogNotificationUpdateService(private val project: Project) : Disposable {

    private val updateCallback = Runnable {
      if (!project.isDisposed) {
        EditorNotifications.getInstance(project).updateAllNotifications()
      }
    }

    private var registered = false

    fun ensureCallbacksRegistered() {
      if (registered) return
      synchronized(updateCallback) {
        if (registered) return
        val service = JsonSchemaService.Impl.get(project)
        service.registerRemoteUpdateCallback(updateCallback)
        service.registerResetAction(updateCallback)
        Disposer.register(this) {
          service.unregisterRemoteUpdateCallback(updateCallback)
          service.unregisterResetAction(updateCallback)
          registered = false
        }
        registered = true
      }
    }

    override fun dispose() {
    }
  }

}
