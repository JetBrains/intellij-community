// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JsonSchemaVfsListener")
package com.jetbrains.jsonSchema

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.json.JsonFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.ZipperUpdater
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileContentsChangedAdapter
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeAnyChangeAbstractAdapter
import com.intellij.util.Alarm
import com.intellij.util.ThrowableRunnable
import com.intellij.util.concurrency.SequentialTaskExecutor
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.messages.Topic
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.JsonSchemaServiceImpl
import java.util.Arrays
import java.util.concurrent.ConcurrentHashMap


@Service(Service.Level.PROJECT)
private class JsonSchemaUpdater(project: Project): Disposable {
  private val myProject: Project
  private val myUpdater: ZipperUpdater
  private val myDirtySchemas: MutableSet<VirtualFile?> = ConcurrentHashMap.newKeySet<VirtualFile?>()
  private val myRunnable: Runnable
  private val myTaskExecutor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("Json Vfs Updater Executor")

  init {
    val disposable = this as Disposable

    myProject = project
    myUpdater = ZipperUpdater(DELAY_MS, Alarm.ThreadToUse.POOLED_THREAD, disposable)
    myRunnable = Runnable {
      val service = project.service<JsonSchemaService>()
      if (myProject.isDisposed()) return@Runnable
      val scope: MutableCollection<VirtualFile?> = HashSet<VirtualFile?>(myDirtySchemas)
      if (ContainerUtil.exists<VirtualFile?>(
          scope,
          Condition { f: VirtualFile? -> service.possiblyHasReference(f!!.getName()) })
      ) {
        myProject.getMessageBus().syncPublisher<Runnable>(JSON_DEPS_CHANGED).run()
        JsonDependencyModificationTracker.forProject(myProject).incModificationCount()
      }
      myDirtySchemas.removeAll(scope)
      if (scope.isEmpty()) return@Runnable

      val finalScope: MutableCollection<VirtualFile?> =
        ContainerUtil.filter<VirtualFile?>(scope, Condition { file: VirtualFile? ->
          service.isApplicableToFile(file)
          && (service as JsonSchemaServiceImpl).isMappedSchema(file!!, false)
        })
      if (finalScope.isEmpty()) return@Runnable
      if (myProject.isDisposed()) return@Runnable
      myProject.getMessageBus().syncPublisher<Runnable>(JSON_SCHEMA_CHANGED).run()

      val analyzer = DaemonCodeAnalyzer.getInstance(project)
      val psiManager = PsiManager.getInstance(project)
      val editors = EditorFactory.getInstance().getAllEditors()
      Arrays.stream<Editor?>(editors)
        .filter { editor: Editor? -> editor is EditorEx && editor.getProject() === myProject }
        .map<VirtualFile?> { editor: Editor? -> editor!!.getVirtualFile() }
        .filter { file: VirtualFile? -> file != null && file.isValid() }
        .forEach { file: VirtualFile? ->
          val schemaFiles = (service as JsonSchemaServiceImpl).getSchemasForFile(file!!, false, true)
          if (ContainerUtil.exists<VirtualFile?>(schemaFiles, Condition { o: VirtualFile? -> finalScope.contains(o) })) {
            if (ApplicationManager.getApplication().isUnitTestMode()) {
              ReadAction.run<RuntimeException?>(ThrowableRunnable {
                Companion.restartAnalyzer(
                  analyzer,
                  psiManager,
                  file
                )
              })
            }
            else {
              ReadAction.nonBlocking(Runnable { Companion.restartAnalyzer(analyzer, psiManager, file) })
                .expireWith(disposable)
                .submit(myTaskExecutor)
            }
          }
        }
    }
  }

  fun onFileChange(schemaFile: VirtualFile) {
    if (JsonFileType.DEFAULT_EXTENSION == schemaFile.getExtension()) {
      myDirtySchemas.add(schemaFile)
      val app = ApplicationManager.getApplication()
      if (app.isUnitTestMode()) {
        app.invokeLater(myRunnable, myProject.getDisposed())
      }
      else {
        myUpdater.queue(myRunnable)
      }
    }
  }

  override fun dispose() {
  }

  companion object {
    private const val DELAY_MS = 200

    private fun restartAnalyzer(analyzer: DaemonCodeAnalyzer, psiManager: PsiManager, file: VirtualFile) {
      val psiFile = if (!psiManager.isDisposed() && file.isValid()) psiManager.findFile(file) else null
      if (psiFile != null) analyzer.restart(psiFile, "JsonSchemaUpdater")
    }
  }
}

@JvmField
val JSON_SCHEMA_CHANGED: Topic<Runnable> =
  Topic.create("JsonSchemaVfsListener.Json.Schema.Changed", Runnable::class.java)

@JvmField
val JSON_DEPS_CHANGED: Topic<Runnable> = Topic.create("JsonSchemaVfsListener.Json.Deps.Changed", Runnable::class.java)

fun startListening(project: Project, service: JsonSchemaService, connection: MessageBusConnection) {
  val updater = project.service<JsonSchemaUpdater>()
  connection.subscribe<BulkFileListener>(VirtualFileManager.VFS_CHANGES,
                                         BulkVirtualFileListenerAdapter(object : VirtualFileContentsChangedAdapter() {
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
  }, service as Disposable)
}


