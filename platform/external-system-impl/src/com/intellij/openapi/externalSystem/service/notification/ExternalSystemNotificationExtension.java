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

import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows to customize {@link ExternalSystemIdeNotificationManager external system notifications} shown to end-user by the ide.
 * 
 * @author Denis Zhdanov
 * @since 8/5/13 8:52 AM
 */
public interface ExternalSystemNotificationExtension {

  ExtensionPointName<ExternalSystemNotificationExtension> EP_NAME
    = ExtensionPointName.create("com.intellij.externalSystemNotificationExtension");
  
  @NotNull
  ProjectSystemId getTargetExternalSystemId();

  /**
   * Allows to customize external system processing error.
   * 
   * @param project  target ide project
   * @param error    error occurred during external system processing
   * @param hint     hint for a use-case during processing of which given error occurs
   * @return         customization result (if applicable)
   */
  @Nullable
  CustomizationResult customize(@NotNull Project project, @NotNull Throwable error, @Nullable UsageHint hint);
  
  enum UsageHint {
    PROJECT_REFRESH
  }
  
  class CustomizationResult {

    @Nullable private final String               myTitle;
    @Nullable private final String               myMessage;
    @Nullable private final NotificationType     myNotificationType;
    @Nullable private final NotificationListener myListener;

    public CustomizationResult(@Nullable String title,
                               @Nullable String message,
                               @Nullable NotificationType notificationType,
                               @Nullable NotificationListener listener)
    {
      myTitle = title;
      myMessage = message;
      myNotificationType = notificationType;
      myListener = listener;
    }

    @Nullable
    public String getTitle() {
      return myTitle;
    }

    @Nullable
    public String getMessage() {
      return myMessage;
    }

    @Nullable
    public NotificationType getNotificationType() {
      return myNotificationType;
    }

    @Nullable
    public NotificationListener getListener() {
      return myListener;
    }
  }
}
