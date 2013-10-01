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
package com.intellij.ide;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class SystemHealthMonitor extends ApplicationComponent.Adapter {
  public SystemHealthMonitor(@NotNull Application application) {
    checkJdk(application);
  }

  private static void checkJdk(final Application app) {
    String vmName = System.getProperty("java.vm.name");
    if (vmName != null && StringUtil.containsIgnoreCase(vmName, "OpenJDK") && !SystemInfo.isJavaVersionAtLeast("1.7")) {
      app.getMessageBus().connect(app).subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener.Adapter() {
        @Override
        public void appFrameCreated(String[] commandLineArgs, @NotNull Ref<Boolean> willOpenProject) {
          app.invokeLater(new Runnable() {
            public void run() {
              notifyWrongJavaVersion();
            }
          });
        }
      });
    }
  }

  private static void notifyWrongJavaVersion() {
    JComponent component = WindowManager.getInstance().findVisibleFrame().getRootPane();
    if (component != null) {
      Rectangle rect = component.getVisibleRect();
      JBPopupFactory.getInstance()
        .createHtmlTextBalloonBuilder(IdeBundle.message("unsupported.jdk.message"), MessageType.WARNING, null)
        .setFadeoutTime(-1)
        .setHideOnFrameResize(false)
        .createBalloon()
        .show(new RelativePoint(component, new Point(rect.x + 30, rect.y + rect.height - 10)), Balloon.Position.above);
    }
  }
}
