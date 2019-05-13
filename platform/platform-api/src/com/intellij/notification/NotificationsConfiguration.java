/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.notification;

import com.intellij.openapi.application.ApplicationManager;

public abstract class NotificationsConfiguration extends NotificationsAdapter {
  /**
   * If notification group ID starts with this prefix it wouldn't be shown in Preferences
   */
  public static final String LIGHTWEIGHT_PREFIX = "LIGHTWEIGHT";
  public abstract void changeSettings(String groupDisplayName, NotificationDisplayType displayType, boolean shouldLog, boolean shouldReadAloud);

  public static NotificationsConfiguration getNotificationsConfiguration() {
    return ApplicationManager.getApplication().getComponent(NotificationsConfiguration.class);
  }
}
