package com.intellij.database.datagrid

import com.intellij.database.DataGridBundle
import com.intellij.database.connection.throwable.info.SimpleErrorInfo
import com.intellij.database.datagrid.DocumentDataHookUp.UpdateSession
import com.intellij.database.run.ui.grid.LongActionRequestPlace
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Document
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import kotlinx.coroutines.*


class DocumentUpdaterWithComputeOnBG {

  fun <T> run(
    cs: CoroutineScope,
    source: GridRequestSource,
    action: UpdateActionWithComputeOnBG<T>,
    document: Document,
    documentListener: DocumentDataHookUp.MyDocumentListener,
    hookUp: DocumentDataHookUp,
    createSession: () -> UpdateSessionCloser,
  ) = cs.launch(Dispatchers.EDT) {
    if (!ReadonlyStatusHandler.ensureDocumentWritable(hookUp.project, document)) {
      hookUp.notifyRequestError(source, SimpleErrorInfo.create(DataGridBundle.message("cannot.update.document")))
      hookUp.notifyRequestFinished(source, false)
      return@launch
    }

    var success = false
    try {
      withLoadingIfNeeded(source.place) {
        createSession().use { updateSessionInfo ->
          success = doUpdate(updateSessionInfo, source, documentListener, hookUp, action)
        }
      }
      hookUp.notifyRequestFinished(source, success)
    }
    catch (e: Exception) {
      hookUp.notifyRequestError(source, SimpleErrorInfo.create(e))
      throw e
    }
  }

  private suspend fun <T> doUpdate(
    updateSessionInfo: UpdateSessionCloser,
    source: GridRequestSource,
    documentListener: DocumentDataHookUp.MyDocumentListener,
    hookUp: DocumentDataHookUp,
    action: UpdateActionWithComputeOnBG<T>,
  ): Boolean {
    var success = false
    val session = updateSessionInfo.session

    val preparedData = withContext(Dispatchers.Default) {
      action.prepareData()
    }

    edtWriteAction {
      executeCommand(hookUp.project, DataGridBundle.message("command.name.update.values"), null) {
        try {
          documentListener.muteChangeEvents()
          success = action.performUpdate(session, preparedData)
        }
        finally {
          documentListener.unmuteChangeEvents(source)
        }
        updateSessionInfo.success = success
        updateSessionInfo.close()
      }
    }
    return success
  }

  private suspend fun withLoadingIfNeeded(place: GridRequestSource.RequestPlace, block: suspend () -> Unit) {
    if (place is LongActionRequestPlace) {
      place.loadingUI().use {
        block()
      }
    }
    else {
      block()
    }
  }
}

interface UpdateActionWithComputeOnBG<T> {
  fun prepareData(): T

  @Throws(java.lang.Exception::class)
  fun performUpdate(session: UpdateSession, data: T): Boolean
}

