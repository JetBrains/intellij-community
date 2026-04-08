// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.ide.impl.ProjectUtilCore
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl
import com.intellij.openapi.fileTypes.FileTypeEvent
import com.intellij.openapi.fileTypes.FileTypeListener
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsListener
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdaterImpl
import com.intellij.openapi.startup.InitProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.isTooLarge
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.LanguageSubstitutors
import com.intellij.psi.PsiLargeFile
import com.intellij.psi.impl.PsiManagerEx
import java.util.concurrent.atomic.AtomicBoolean

internal class PsiVfsInitProjectActivity : InitProjectActivity {
  override val isParallelExecution: Boolean
    get() = true

  private fun processFileTypesChanged(project: Project, clearViewProviders: Boolean = true) {
    (PsiManagerEx.getInstanceEx(project).fileManagerEx).processFileTypesChanged(clearViewProviders)
  }

  override suspend fun run(project: Project) {
    val connection = project.messageBus.simpleConnect()

    @Suppress("UsagesOfObsoleteApi")
    serviceAsync<LanguageSubstitutors>().point?.addChangeListener((project as ComponentManagerEx).getCoroutineScope()) {
      processFileTypesChanged(project)
    }

    connection.subscribe(AdditionalLibraryRootsListener.TOPIC, PsiVfsAdditionalLibraryRootListener(project))
    connection.subscribe(FileTypeManager.TOPIC, object : FileTypeListener {
      override fun fileTypesChanged(e: FileTypeEvent) {
        processFileTypesChanged(project, clearViewProviders = e.removedFileType != null)
      }
    })
    connection.subscribe(FileDocumentManagerListener.TOPIC, MyFileDocumentManagerListener(project))

    connection.subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
      override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
        processFileTypesChanged(project)
      }

      override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        processFileTypesChanged(project)
      }
    })

    installGlobalListener()
  }

  private fun installGlobalListener() {
    if (!globalListenerInstalled.compareAndSet(false, true)) {
      return
    }

    ApplicationManager.getApplication().messageBus.simpleConnect().subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun before(events: List<VFileEvent>) {
        for (project in ProjectUtilCore.getOpenProjects()) {
          if (!project.isDisposed) {
            project.service<PsiVFSListener>().before(events)
          }
        }
      }

      override fun after(events: List<VFileEvent>) {
        val projects = ProjectUtilCore.getOpenProjects()
        // let PushedFilePropertiesUpdater process all pending VFS events and update file properties before we issue PSI events
        for (project in projects) {
          val updater = PushedFilePropertiesUpdater.getInstance(project)
          if (updater is PushedFilePropertiesUpdaterImpl) {
            updater.processAfterVfsChanges(events)
          }
        }
        for (project in projects) {
          project.service<PsiVFSListener>().after(events)
        }
      }
    })
  }
}

internal class MyFileDocumentManagerListener(private val project: Project) : FileDocumentManagerListener {
  private val fileManager = PsiManagerEx.getInstanceEx(project).fileManager as FileManagerEx
  override fun fileWithNoDocumentChanged(file: VirtualFile) {
    val viewProviders = fileManager.findCachedViewProviders(file)
    if (viewProviders.isEmpty()) {
      project.service<PsiVFSListener>().handleVfsChangeWithoutPsi(file)
    }
    else {
      runWriteActionWithExternalChange {
        if (FileDocumentManagerImpl.recomputeFileTypeIfNecessary(file)) {
          fileManager.forceReload(file)
        }
        else {
          for (viewProvider in viewProviders) {
            fileManager.reloadPsiAfterTextChange(viewProvider, file)
          }
        }
      }
    }
  }

  override fun fileContentReloaded(file: VirtualFile, document: Document) {
    val psiFile = fileManager.findCachedViewProvider(file)
    if (file.isValid && psiFile != null && file.isTooLarge() && psiFile !is PsiLargeFile) {
      runWriteActionWithExternalChange {
        fileManager.reloadPsiAfterTextChange(psiFile, file)
      }
    }
  }
}

private val globalListenerInstalled = AtomicBoolean(false)

