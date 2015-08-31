/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.ide.PowerSaveMode;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;

/**
 * @author peter
 */
public class PowerSaveModeNotifier implements StartupActivity {
  private static final NotificationGroup POWER_SAVE_MODE = NotificationGroup.balloonGroup("Power Save Mode");
  private static final String IGNORE_POWER_SAVE_MODE = "ignore.power.save.mode";
  
  @Override
  public void runActivity(@NotNull Project project) {
    if (PowerSaveMode.isEnabled()) {
      notifyOnPowerSaveMode(project);
    }
  }

  static void notifyOnPowerSaveMode(Project project) {
    if (PropertiesComponent.getInstance().getBoolean(IGNORE_POWER_SAVE_MODE)) {
      return;
    }
    
    String message = "Code insight and other background tasks are disabled." +
                     "<br/><a href=\"ignore\">Do not show again</a>" +
                     "<br/><a href=\"turnOff\">Disable Power Save Mode</a>";
    POWER_SAVE_MODE.createNotification("Power save mode is on", message, NotificationType.WARNING, new NotificationListener() {
      @Override
      public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
        final String description = event.getDescription();
        if ("ignore".equals(description)) {
          PropertiesComponent.getInstance().setValue(IGNORE_POWER_SAVE_MODE, true);
          notification.expire();
        }
        else if ("turnOff".equals(description)) {
          PowerSaveMode.setEnabled(false);
          notification.expire();
        }
      }
    }).notify(project);
  }
}
