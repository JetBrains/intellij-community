// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.WriteActionPresenceService
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service

/**
 * A utility for marking some of the operation as external changes.
 * It is useful for detecting of some of the changes to [com.intellij.openapi.editor.Document] were performed by some other party than the user themselves
 */
object ExternalChangeActionUtil {

  @JvmStatic
  fun isExternalChangeInProgress(): Boolean {
    return PsiExternalChangeService.getInstance().isExternalChangeActionInProgress
  }

  @JvmStatic
  fun isExternalDocumentChangeInProgress(): Boolean {
    return PsiExternalChangeService.getInstance().isExternalDocumentChangeInProgress
  }

  @JvmStatic
  fun externalChangeAction(action: Runnable): Runnable {
    return Runnable {
      PsiExternalChangeService.getInstance().withExternalChange(action)
    }
  }

  @JvmStatic
  fun externalDocumentChangeAction(action: Runnable): Runnable {
    return Runnable {
      PsiExternalChangeService.getInstance().withExternalDocumentChange(action)
    }
  }


  @Service
  private class PsiExternalChangeService() {
    companion object {
      fun getInstance(): PsiExternalChangeService = ApplicationManager.getApplication().getService(PsiExternalChangeService::class.java)
    }

    @Volatile
    var isExternalChangeActionInProgress: Boolean = false

    @Volatile
    var isExternalDocumentChangeInProgress: Boolean = false


    fun withExternalChange(action: Runnable) {
      val currentValue = isExternalChangeActionInProgress
      isExternalChangeActionInProgress = true
      val clazzService = service<WriteActionPresenceService>()
      val toRemove = clazzService.addWriteActionClass(ExternalChangeAction::class.java)
      try {
        action.run()
      }
      finally {
        if (toRemove) {
          clazzService.removeWriteActionClass(ExternalChangeAction::class.java)
        }
        isExternalChangeActionInProgress = currentValue
      }
    }

    fun withExternalDocumentChange(action: Runnable) {
      withExternalChange {
        val currentValue = isExternalDocumentChangeInProgress
        isExternalDocumentChangeInProgress = true
        try {
          action.run()
        }
        finally {
          isExternalDocumentChangeInProgress = currentValue
        }
      }
    }
  }


}