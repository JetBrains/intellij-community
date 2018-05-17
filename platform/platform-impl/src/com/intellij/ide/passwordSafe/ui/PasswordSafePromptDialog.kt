// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.passwordSafe.ui

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.askPassword
import com.intellij.openapi.project.Project

@Deprecated("")
object PasswordSafePromptDialog {
  @Deprecated("Use {@link CredentialPromptDialog}",
              ReplaceWith("askPassword(project, title, message, CredentialAttributes(requestor, key), resetPassword, error)", "com.intellij.credentialStore.askPassword",
                                        "com.intellij.credentialStore.CredentialAttributes"))
  fun askPassword(project: Project?, title: String,
                  message: String,
                  requestor: Class<*>,
                  key: String,
                  resetPassword: Boolean,
                  error: String): String? {
    return askPassword(project, title, message, CredentialAttributes(requestor, key), resetPassword, error)
  }
}