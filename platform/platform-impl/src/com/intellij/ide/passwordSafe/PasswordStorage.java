/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ide.passwordSafe;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

/**
 * The interface defines basic password management operations
 */
public interface PasswordStorage {
  /**
   * Get password stored in a password safe
   *
   * @param project   the project, that is used to ask for the master password if this is the first access to password safe
   * @param requester the requester class
   * @param key       the key for the password
   * @return the stored password or null if the password record was not found or was removed
   * @throws PasswordSafeException if password safe cannot be accessed
   */
  @Nullable
  String getPassword(Project project, Class requester, String key) throws PasswordSafeException;
  /**
   * Remove password stored in a password safe
   *
   * @param project   the project, that is used to ask for the master password if this is the first access to password safe
   * @param requester the requester class
   * @param key       the key for the password
   * @return the plugin key
   * @throws PasswordSafeException if password safe cannot be accessed
   */
  void removePassword(Project project, Class requester, String key) throws PasswordSafeException;
  /**
   * Store password in password safe
   *
   * @param project   the project, that is used to ask for the master password if this is the first access to password safe
   * @param requester the requester class
   * @param key       the key for the password
   * @param value     the value to store
   * @throws PasswordSafeException if password safe cannot be accessed
   */
  void storePassword(Project project, Class requester, String key, String value) throws PasswordSafeException;
}
