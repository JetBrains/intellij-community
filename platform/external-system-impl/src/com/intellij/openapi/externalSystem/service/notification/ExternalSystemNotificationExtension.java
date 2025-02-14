/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.service.notification;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows to customize {@link ExternalSystemNotificationManager external system notifications} shown to end-user by the ide.
 */
public interface ExternalSystemNotificationExtension {

  ExtensionPointName<ExternalSystemNotificationExtension> EP_NAME
    = ExtensionPointName.create("com.intellij.externalSystemNotificationExtension");

  @NotNull
  ProjectSystemId getTargetExternalSystemId();

  /**
   * Allows customizing external system processing notification.
   *
   * @param notificationData    notification data
   * @param project             target ide project
   * @param externalProjectPath path of the target external project
   * @param error               error occurred during external system processing
   */
  default void customize(
    @NotNull NotificationData notificationData,
    @NotNull Project project,
    @NotNull String externalProjectPath,
    @Nullable Throwable error
  ) { }

  /**
   * @deprecated Use {@link #customize(NotificationData, Project, String, Throwable)} instead
   */
  @Deprecated(forRemoval = true)
  default void customize(
    @NotNull NotificationData notificationData,
    @NotNull Project project,
    @Nullable Throwable error
  ) { }

  /**
   * Allows determining internal errors comes from an external system, which might be confusing for IDE users.
   * Such errors shouldn't be shown to the end user on UI.
   *
   * @param error error occurred during external system processing
   * @return true if the error shouldn't be shown to the end user on UI w/o additional processing, false otherwise
   */
  @ApiStatus.Experimental
  default boolean isInternalError(@NotNull Throwable error) {
    return false;
  }
}
