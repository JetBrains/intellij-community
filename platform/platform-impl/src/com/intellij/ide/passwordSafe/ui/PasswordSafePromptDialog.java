/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.passwordSafe.ui

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.askPassword
import com.intellij.openapi.project.Project

@Deprecated("")
object PasswordSafePromptDialog {
  @Deprecated("Use {@link CredentialPromptDialog}",
              ReplaceWith("askPassword(project, title, message, CredentialAttributes(requestor, key), resetPassword, error)", "com.intellij.credentialStore.askPassword",
                                        "com.intellij.credentialStore.CredentialAttributes"))
  fun askPassword(project: Project?,
                  title: String,
                  message: String,
                  requestor: Class<*>,
                  key: String,
                  resetPassword: Boolean,
                  error: String): String? {
    return askPassword(project, title, message, CredentialAttributes(requestor, key), resetPassword, error)
  }
}

