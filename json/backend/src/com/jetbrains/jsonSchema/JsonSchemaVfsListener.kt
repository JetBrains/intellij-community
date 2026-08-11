// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JsonSchemaVfsListener")

package com.jetbrains.jsonSchema

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.json.JsonFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileContentsChangedAdapter
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapterBackgroundable
import com.intellij.openapi.vfs.newvfs.BulkFileListenerBackgroundable
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeAnyChangeAbstractAdapter
import com.intellij.util.messages.Topic
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.JsonSchemaServiceImpl
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds


@Service(Service.Level.PROJECT)
private class JsonSchemaUpdater(project: Project, scope: CoroutineScope) : Disposable {

  private val myDirtySchemas = ConcurrentHashMap.newKeySet<VirtualFile>()
  private val myDirtySchemasChanged: Channel<Unit> = Channel(Channel.CONFLATED)

  private val DELAY_MS = 200L

  init {
    scope.launch(CoroutineName("JsonSchemaUpdater consumer")) {
      while (isActive) {
        try {
          consumeDirtySchemas(project)
        }
        catch (e: Exception) {
          if (e is CancellationException) throw e
          logger<JsonSchemaUpdater>().error(e)
        }
      }
    }

  }

  private suspend fun consumeDirtySchemas(project: Project) {
    val service = project.serviceAsync<JsonSchemaService>()
    val scope = drainDirtySchemasWithDebounce()

    if (scope.any { f: VirtualFile -> service.possiblyHasReference(f.getName()) }
    ) {
      project.getMessageBus().syncPublisher(JSON_DEPS_CHANGED).run()
      JsonDependencyModificationTracker.forProject(project).incModificationCount()
    }

    val finalScope = scope.filter { file: VirtualFile ->
      service.isApplicableToFile(file) && (service as JsonSchemaServiceImpl).isMappedSchema(file, false)
    }
    if (finalScope.isEmpty()) return

    project.getMessageBus().syncPublisher(JSON_SCHEMA_CHANGED).run()

    val analyzer = DaemonCodeAnalyzer.getInstance(project)
    val psiManager = PsiManager.getInstance(project)
    readAction {
      val editorFiles = EditorFactory.getInstance().getAllEditors()
        .asSequence()
        .filter { editor: Editor -> editor is EditorEx && editor.getProject() === project }
        .mapNotNull { editor: Editor -> editor.virtualFile }
        .filter { file: VirtualFile -> file.isValid() }
        .toList()
      ProgressManager.checkCanceled()
      editorFiles
        .forEach { file: VirtualFile ->
          val schemaFiles = (service as JsonSchemaServiceImpl).getSchemasForFile(file, false, true)
          if (schemaFiles.any { o: VirtualFile? -> finalScope.contains(o) }) {
            restartAnalyzer(analyzer, psiManager, file)
          }
        }
    }
  }

  private suspend fun drainDirtySchemasWithDebounce(): Set<VirtualFile> {
    myDirtySchemasChanged.receive()
    val scope = HashSet<VirtualFile>()
    drainDirtySchemas(scope)
    while (currentCoroutineContext().isActive) {
      val hasMoreChanges = if (ApplicationManager.getApplication().isUnitTestMode()) {
        myDirtySchemasChanged.tryReceive().getOrNull() != null
      }
      else {
        withTimeoutOrNull(DELAY_MS.milliseconds) {
          myDirtySchemasChanged.receive()
        } != null
      }
      if (!hasMoreChanges) break
      drainDirtySchemas(scope)
    }
    return scope
  }

  private fun drainDirtySchemas(scope: MutableSet<VirtualFile>) {
    val iterator = myDirtySchemas.iterator()
    while (iterator.hasNext()) {
      scope.add(iterator.next())
      iterator.remove()
    }
  }

  fun onFileChange(schemaFile: VirtualFile) {
    if (JsonFileType.DEFAULT_EXTENSION == schemaFile.getExtension()) {
      myDirtySchemas.add(schemaFile)
      myDirtySchemasChanged.trySend(Unit)
    }
  }

  override fun dispose() {
  }


  private fun restartAnalyzer(analyzer: DaemonCodeAnalyzer, psiManager: PsiManager, file: VirtualFile) {
    val psiFile = if (!psiManager.isDisposed() && file.isValid()) psiManager.findFile(file) else null
    if (psiFile != null) analyzer.restart(psiFile, "JsonSchemaUpdater")
  }

}

@JvmField
internal val JSON_SCHEMA_CHANGED: Topic<Runnable> =
  Topic.create("JsonSchemaVfsListener.Json.Schema.Changed", Runnable::class.java)

@JvmField
@ApiStatus.Internal
val JSON_DEPS_CHANGED: Topic<Runnable> = Topic.create("JsonSchemaVfsListener.Json.Deps.Changed", Runnable::class.java)

internal fun startListening(project: Project) {
  val updater = project.service<JsonSchemaUpdater>()
  project.messageBus.connect(updater).subscribe<BulkFileListenerBackgroundable>(
    VirtualFileManager.VFS_CHANGES_BG,
    BulkVirtualFileListenerAdapterBackgroundable(object : VirtualFileContentsChangedAdapter() {
      override fun onFileChange(schemaFile: VirtualFile) {
        updater.onFileChange(schemaFile)
      }

      override fun onBeforeFileChange(schemaFile: VirtualFile) {
        updater.onFileChange(schemaFile)
      }
    }))
  PsiManager.getInstance(project).addPsiTreeChangeListener(object : PsiTreeAnyChangeAbstractAdapter() {
    override fun onChange(file: PsiFile?) {
      if (file != null) updater.onFileChange(file.getViewProvider().getVirtualFile())
    }
  }, updater)
}
