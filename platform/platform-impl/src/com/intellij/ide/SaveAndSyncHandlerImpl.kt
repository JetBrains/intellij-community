// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.util.SingleAlarm
import java.beans.PropertyChangeListener
import java.util.concurrent.atomic.AtomicInteger

private val LOG = Logger.getInstance(SaveAndSyncHandler::class.java)

class SaveAndSyncHandlerImpl(private val settings: GeneralSettings, fileDocumentManager: FileDocumentManager) : SaveAndSyncHandler(), Disposable {
  private val generalSettingsListener: PropertyChangeListener
  private val refreshDelayAlarm = SingleAlarm(Runnable { this.doScheduledRefresh() }, delay = 300, parentDisposable = this)
  private val blockSaveOnFrameDeactivationCount = AtomicInteger()
  private val blockSyncOnFrameActivationCount = AtomicInteger()
  @Volatile
  private var refreshSessionId: Long = 0

  private val idleListener = {
    if (settings.isAutoSaveIfInactive && canSyncOrSave()) {
      submitTransaction {
        (fileDocumentManager as FileDocumentManagerImpl).saveAllDocuments(false)
      }
    }
  }

  init {
    IdeEventQueue.getInstance().addIdleListener(idleListener, settings.inactiveTimeout * 1000)

    generalSettingsListener = PropertyChangeListener { e ->
      if (GeneralSettings.PROP_INACTIVE_TIMEOUT == e.propertyName) {
        val eventQueue = IdeEventQueue.getInstance()
        eventQueue.removeIdleListener(idleListener)
        eventQueue.addIdleListener(idleListener, (e.newValue as Int) * 1000)
      }
    }
    settings.addPropertyChangeListener(generalSettingsListener)

    val busConnection = ApplicationManager.getApplication().messageBus.connect(this)
    busConnection.subscribe(FrameStateListener.TOPIC, object : FrameStateListener {
      override fun onFrameDeactivated() {
        LOG.debug("save(): enter")
        submitTransaction {
          if (canSyncOrSave()) {
            saveProjectsAndDocuments()
          }
          LOG.debug("save(): exit")
        }
      }

      override fun onFrameActivated() {
        if (!ApplicationManager.getApplication().isDisposed && settings.isSyncOnFrameActivation) {
          scheduleRefresh()
        }
      }
    })
  }

  override fun dispose() {
    RefreshQueue.getInstance().cancelSession(refreshSessionId)
    settings.removePropertyChangeListener(generalSettingsListener)
    IdeEventQueue.getInstance().removeIdleListener(idleListener)
  }

  private fun canSyncOrSave(): Boolean {
    return !LaterInvocator.isInModalContext() && !ProgressManager.getInstance().hasModalProgressIndicator()
  }

  override fun saveProjectsAndDocuments() {
    val app = ApplicationManager.getApplication()
    if (!app.isDisposed &&
        settings.isSaveOnFrameDeactivation &&
        blockSaveOnFrameDeactivationCount.get() == 0) {
      app.saveAll()
    }
  }

  override fun scheduleRefresh() {
    refreshDelayAlarm.cancelAndRequest()
  }

  private fun doScheduledRefresh() {
    submitTransaction {
      if (canSyncOrSave()) {
        refreshOpenFiles()
      }
      maybeRefresh(ModalityState.NON_MODAL)
    }
  }

  fun maybeRefresh(modalityState: ModalityState) {
    if (blockSyncOnFrameActivationCount.get() == 0 && settings.isSyncOnFrameActivation) {
      val queue = RefreshQueue.getInstance()
      queue.cancelSession(refreshSessionId)

      val session = queue.createSession(true, true, null, modalityState)
      session.addAllFiles(*ManagingFS.getInstance().localRoots)
      refreshSessionId = session.id
      session.launch()
      LOG.debug("vfs refreshed")
    }
    else {
      LOG.debug { "vfs refresh rejected, blocked: ${blockSyncOnFrameActivationCount.get() != 0}, isSyncOnFrameActivation: ${settings.isSyncOnFrameActivation}" }
    }
  }

  override fun refreshOpenFiles() {
    val files = ArrayList<VirtualFile>()

    for (project in ProjectManager.getInstance().openProjects) {
      for (file in FileEditorManager.getInstance(project).selectedFiles) {
        if (file is NewVirtualFile) {
          files.add(file)
        }
      }
    }

    if (!files.isEmpty()) {
      // refresh open files synchronously so it doesn't wait for potentially longish refresh request in the queue to finish
      RefreshQueue.getInstance().refresh(false, false, null, files)
    }
  }

  override fun blockSaveOnFrameDeactivation() {
    LOG.debug("save blocked")
    blockSaveOnFrameDeactivationCount.incrementAndGet()
  }

  override fun unblockSaveOnFrameDeactivation() {
    blockSaveOnFrameDeactivationCount.decrementAndGet()
    LOG.debug("save unblocked")
  }

  override fun blockSyncOnFrameActivation() {
    LOG.debug("sync blocked")
    blockSyncOnFrameActivationCount.incrementAndGet()
  }

  override fun unblockSyncOnFrameActivation() {
    blockSyncOnFrameActivationCount.decrementAndGet()
    LOG.debug("sync unblocked")
  }

  private inline fun submitTransaction(crossinline handler: () -> Unit) {
    TransactionGuard.submitTransaction(this, Runnable { handler() })
  }
}
