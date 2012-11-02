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
package com.intellij.ide;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class OpenJdkNotifier {
  public static void checkJdk(MessageBus bus) {
    final String vendor = System.getProperty("java.vendor").toLowerCase();
    if (!vendor.contains("sun") && !vendor.contains("apple") && !vendor.contains("oracle")) {
      bus.connect().subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener.Adapter() {
        @Override
        public void appFrameCreated(String[] commandLineArgs, @NotNull Ref<Boolean> willOpenProject) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              showWrongJavaVersionBaloon();
            }
          });
        }
      });
    }
  }

  private static void showWrongJavaVersionBaloon() {
    // Just a copy paste from ChangesViewBaloonProblemNotifier
    final JFrame frame = WindowManager.getInstance().findVisibleFrame();
    final JComponent component = frame.getRootPane();
    if (component == null) {
      return;
    }
    final Rectangle rect = component.getVisibleRect();
    final Point p = new Point(rect.x + 30, rect.y + rect.height - 10);
    final RelativePoint point = new RelativePoint(component, p);

    final MessageType messageType = MessageType.ERROR;
    final BalloonBuilder builder = JBPopupFactory.getInstance()
      .createHtmlTextBalloonBuilder(IdeBundle.message("use.sun.jdk.prompt"), messageType.getDefaultIcon(), messageType.getPopupBackground(),
                                    null);
    builder.setFadeoutTime(-1);
    builder.createBalloon().show(point, Balloon.Position.above);
  }

}
