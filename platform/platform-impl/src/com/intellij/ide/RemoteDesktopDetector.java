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
package com.intellij.ide;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.sun.jna.platform.win32.User32;

public class RemoteDesktopDetector {
  private static final Logger LOG = Logger.getInstance(RemoteDesktopDetector.class);
  
  public static RemoteDesktopDetector getInstance() {
    return ServiceManager.getService(RemoteDesktopDetector.class);
  }
  
  private volatile boolean myFailureDetected;
  private volatile boolean myRemoteDesktopConnected;
  
  private RemoteDesktopDetector() {
    if (SystemInfo.isWindows) {
      DisplayChangeDetector.getInstance().addListener(new DisplayChangeDetector.Listener() {
        @Override
        public void displayChanged() {
          updateState();
        }
      });
      updateState();
    }
  }
  
  private void updateState() {
    if (!myFailureDetected) {
      try {
        // This might not work in all cases, but hopefully is a more reliable method than the current one (checking for font smoothing)
        // see https://msdn.microsoft.com/en-us/library/aa380798%28v=vs.85%29.aspx
        boolean newValue = User32.INSTANCE.GetSystemMetrics(0x1000) != 0; // 0x1000 is SM_REMOTESESSION
        LOG.debug("Detected remote desktop: ", newValue);
        if (newValue != myRemoteDesktopConnected) {
          myRemoteDesktopConnected = newValue;
          if (myRemoteDesktopConnected) {
            // We postpone notification to avoid recursive initialization of RemoteDesktopDetector 
            // (in case it's initialized by request from com.intellij.notification.EventLog)
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                Notifications.Bus.notify(new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID,
                                                          ApplicationBundle.message("remote.desktop.detected.title"),
                                                          ApplicationBundle
                                                            .message("remote.desktop.detected.message"),
                                                          NotificationType.INFORMATION));
              }
            });
          }
        }
      }
      catch (Throwable e) {
        myRemoteDesktopConnected = false;
        myFailureDetected = true;
        LOG.warn("Error while calling GetSystemMetrics", e);
      }
    }
  }
  
  public boolean isRemoteDesktopConnected() {
    return myRemoteDesktopConnected;
  }
  
  public static boolean isRemoteSession() {
    return getInstance().isRemoteDesktopConnected();
  }
}
