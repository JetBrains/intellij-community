// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.credentialStore

import com.intellij.notification.NotificationAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.*
import java.awt.Component

interface CredentialStoreUiService {
  companion object {
    fun getInstance(): CredentialStoreUiService = service()
  }

  fun notify(@NotificationTitle title: String, @NotificationContent content: String, project: Project?, action: NotificationAction?)

  fun showChangeMainPasswordDialog(contextComponent: Component?,
                                   setNewMainPassword: (current: CharArray, new: CharArray) -> Boolean): Boolean

  fun showRequestMainPasswordDialog(@DialogTitle title: String,
                                    @DialogMessage topNote: String? = null,
                                    contextComponent: Component? = null,
                                    @DialogMessage ok: (value: ByteArray) -> String?): Boolean

  fun showErrorMessage(parent: Component?, @DialogTitle title: String, @DialogMessage message: String)

  fun openSettings(project: Project?)
}
