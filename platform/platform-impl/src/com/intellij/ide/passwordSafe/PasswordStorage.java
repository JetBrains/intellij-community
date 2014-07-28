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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The interface defines basic password management operations
 */
public interface PasswordStorage {

  /**
   * @deprecated To remove in IDEA 15. Use {@link #getPassword(Project, Class, String, ModalityState)}
   */
  @Deprecated
  @Nullable
  String getPassword(@Nullable Project project, @NotNull Class requestor, String key) throws PasswordSafeException;

  /**
   * Get password stored in a password safe.
   * <p/>
   * The method may be called from any thread. It may need to show a master password dialog to unlock the password database,
   * and then will use {@link Application#invokeAndWait(Runnable, ModalityState) invokeAndWait()}.
   * So make sure to pass correct {@link ModalityState} to the method to make sure the dialog is shown above all other dialog or progress
   * windows.
   *
   * @param project   the project, that is used to ask for the master password if this is the first access to password safe
   * @param requestor the requestor class
   * @param key       the key for the password
   * @return the stored password or null if the password record was not found or was removed
   * @throws PasswordSafeException if password safe cannot be accessed
   * @throws IllegalStateException if the method is called from the read action.
   */
  @Nullable
  String getPassword(@Nullable Project project, @NotNull Class requestor, String key,
                     @Nullable ModalityState state) throws PasswordSafeException;

  /**
   * Store password in password safe
   * <p/>
   * The method may be called from any thread. It may need to show a master password dialog to unlock the password database,
   * and then will use {@link Application#invokeAndWait(Runnable, ModalityState) invokeAndWait()}.
   * So make sure to pass correct {@link ModalityState} to the method to make sure the dialog is shown above all other dialog or progress
   * windows.
   *
   * @param project   the project, that is used to ask for the master password if this is the first access to password safe
   * @param requestor the requestor class
   * @param key       the key for the password
   * @param value     the value to store
   * @throws PasswordSafeException if password safe cannot be accessed
   */
  void storePassword(@Nullable Project project, @NotNull Class requestor, String key, String value,
                     @Nullable ModalityState modalityState) throws PasswordSafeException;

  /**
   * @deprecated To remove in IDEA 15. Use {@link #storePassword(Project, Class, String, String, ModalityState)}
   */
  @Deprecated
  void storePassword(@Nullable Project project, @NotNull Class requestor, String key, String value) throws PasswordSafeException;

  /**
   * @deprecated To remove in IDEA 15. Use {@link #removePassword(Project, Class, String, ModalityState)}
   */
  @Deprecated
  void removePassword(@Nullable Project project, @NotNull Class requestor, String key) throws PasswordSafeException;

  /**
   * Remove password stored in a password safe
   * <p/>
   * The method may be called from any thread. It may need to show a master password dialog to unlock the password database,
   * and then will use {@link Application#invokeAndWait(Runnable, ModalityState) invokeAndWait()}.
   * So make sure to pass correct {@link ModalityState} to the method to make sure the dialog is shown above all other dialog or progress
   * windows.
   *
   * @param project   the project, that is used to ask for the master password if this is the first access to password safe
   * @param requestor the requestor class
   * @param key       the key for the password
   * @return the plugin key
   * @throws PasswordSafeException if password safe cannot be accessed
   */
  void removePassword(@Nullable Project project, @NotNull Class requestor, String key,
                      @Nullable ModalityState modalityState) throws PasswordSafeException;

}
