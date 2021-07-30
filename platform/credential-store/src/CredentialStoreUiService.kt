// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore

import com.intellij.notification.NotificationAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.Nls
import java.awt.Component

interface CredentialStoreUiService {
  fun notify(title: String, content: String, project: Project?, action: NotificationAction?) {
  }

  fun showChangeMasterPasswordDialog(contextComponent: Component?,
                                          setNewMasterPassword: (current: CharArray, new: CharArray) -> Boolean): Boolean {
    return false
  }

  fun showRequestMasterPasswordDialog(@NlsContexts.DialogTitle title: String,
                                           @Nls(capitalization = Nls.Capitalization.Sentence) topNote: String? = null,
                                           contextComponent: Component? = null,
                                           @NlsContexts.DialogMessage ok: (value: ByteArray) -> String?): Boolean {
    return false
  }

  fun openSettings(project: Project?) {
  }

  companion object {
    fun getInstance(): CredentialStoreUiService {
      return ApplicationManager.getApplication().getService(CredentialStoreUiService::class.java)
    }
  }
}
