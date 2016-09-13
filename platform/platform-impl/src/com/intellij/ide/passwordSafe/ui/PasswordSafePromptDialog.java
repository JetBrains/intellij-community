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
package com.intellij.ide.passwordSafe.ui;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.credentialStore.CredentialAttributesKt.CredentialAttributes;

/**
 * The generic password dialog. Use it to ask a password from user with option to remember it.
 */
public class PasswordSafePromptDialog {
  /**
   * @param project       the context project
   * @param title         the dialog title
   * @param message       the message describing a resource for which password is asked
   * @param requestor     the password requestor
   * @param key           the password key
   * @param resetPassword if true, the old password is removed from database and new password will be asked.
   * @param error         the error to show in the dialog
   * @return null if dialog was cancelled or password (stored in database or a entered by user)
   */
  @Nullable
  public static String askPassword(@Nullable Project project,
                                   String title,
                                   String message,
                                   @NotNull Class<?> requestor,
                                   String key,
                                   boolean resetPassword,
                                   String error) {
    return CredentialPromtKt.askPassword(project, title, message, CredentialAttributes(requestor, key), resetPassword, error);
  }

  /**
   * @param dialogTitle The dialog title
   * @param passwordFieldLabel The password field label, describing a resource, for which password is asked
   * @return null if dialog was cancelled or password (stored in database or a entered by user)
   */
  @Nullable
  public static String askPassword(@NotNull String dialogTitle, @NotNull String passwordFieldLabel, @NotNull CredentialAttributes credentialAttributes) {
    return CredentialPromtKt.askPassword(null, dialogTitle, passwordFieldLabel, credentialAttributes);
  }


  /**
   * @param project The context project (might be null)
   * @param dialogTitle The dialog title
   * @param passwordFieldLabel       the message describing a resource for which password is asked
   * @param resetPassword if true, the old password is removed from database and new password will be asked.
   * @param error         the error to show in the dialog
   * @return null if dialog was cancelled or password (stored in database or a entered by user)
   */
  @Nullable
  public static String askPassword(@Nullable Project project,
                                     String dialogTitle,
                                     @NotNull String passwordFieldLabel,
                                     @NotNull CredentialAttributes credentialAttributes,
                                     boolean resetPassword,
                                     String error) {
    return CredentialPromtKt.askPassword(project, dialogTitle, passwordFieldLabel, credentialAttributes, resetPassword, error);
  }
}

