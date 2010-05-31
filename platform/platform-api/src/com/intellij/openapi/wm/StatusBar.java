/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.wm;

import com.intellij.openapi.*;
import com.intellij.openapi.application.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.startup.*;
import com.intellij.util.messages.*;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;

/**
 * @author spleaner
 */
public interface StatusBar extends StatusBarInfo {
  @SuppressWarnings({"AbstractClassNeverImplemented"})
  abstract class Info implements StatusBarInfo {
    public static final Topic<StatusBarInfo> TOPIC = Topic.create("IdeStatusBar.Text", StatusBarInfo.class);

    private Info() {}

    public static void set(@Nullable final String text, @Nullable final Project project) {
      if (project != null && !project.isInitialized() && !project.isDisposed()) {
        StartupManager.getInstance(project).runWhenProjectIsInitialized(new Runnable() {
           public void run() {
             project.getMessageBus().syncPublisher(TOPIC).setInfo(text);
           }
         });
        return;
      }

      final MessageBus bus = project == null ? ApplicationManager.getApplication().getMessageBus() : project.getMessageBus();
      bus.syncPublisher(TOPIC).setInfo(text);
    }
  }

  void addWidget(@NotNull StatusBarWidget widget);
  void addWidget(@NotNull StatusBarWidget widget, @NotNull String anchor);
  
  void addWidget(@NotNull StatusBarWidget widget, @NotNull Disposable parentDisposable);
  void addWidget(@NotNull StatusBarWidget widget, @NotNull String anchor, @NotNull Disposable parentDisposable);

  /**
   * @deprecated use addWidget instead
   */
  @Deprecated
  void addCustomIndicationComponent(@NotNull JComponent c);
  void removeCustomIndicationComponent(@NotNull JComponent c);

  void removeWidget(@NotNull String id);
  void updateWidget(@NotNull String id);

  void fireNotificationPopup(@NotNull JComponent content, Color backgroundColor);
}
