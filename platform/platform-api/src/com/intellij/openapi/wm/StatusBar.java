/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author spleaner
 */
public interface StatusBar extends StatusBarInfo, Disposable {
  @SuppressWarnings({"AbstractClassNeverImplemented"})
  abstract class Info implements StatusBarInfo {
    public static final Topic<StatusBarInfo> TOPIC = Topic.create("IdeStatusBar.Text", StatusBarInfo.class);

    private Info() {
    }

    public static void set(@Nullable final String text, @Nullable final Project project) {
      set(text, project, null);
    }

    public static void set(@Nullable final String text, @Nullable final Project project, @Nullable final String requestor) {
      if (project != null) {
        if (project.isDisposed()) return;
        if (!project.isInitialized()) {
          StartupManager.getInstance(project).runWhenProjectIsInitialized(
            () -> project.getMessageBus().syncPublisher(TOPIC).setInfo(text, requestor));
          return;
        }
      }

      final MessageBus bus = project == null ? ApplicationManager.getApplication().getMessageBus() : project.getMessageBus();
      bus.syncPublisher(TOPIC).setInfo(text, requestor);
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

  /**
   * @deprecated use removeWidget instead
   */
  @Deprecated
  void removeCustomIndicationComponent(@NotNull JComponent c);

  void removeWidget(@NotNull String id);

  void updateWidget(@NotNull String id);

  @Nullable
  StatusBarWidget getWidget(String id);

  void fireNotificationPopup(@NotNull JComponent content, Color backgroundColor);

  StatusBar createChild();

  JComponent getComponent();

  StatusBar findChild(Component c);

  IdeFrame getFrame();

  void install(IdeFrame frame);

  class Anchors {
    public static final String DEFAULT_ANCHOR = after(SystemInfo.isMac
                                                      ? StandardWidgets.ENCODING_PANEL
                                                      : StandardWidgets.INSERT_OVERWRITE_PANEL);

    public static String before(String widgetId) {
      return "before " + widgetId;
    }
    public static String after(String widgetId) {
      return "after " + widgetId;
    }
  }

  class StandardWidgets {
    public static final String ENCODING_PANEL = "Encoding";
    public static final String INSERT_OVERWRITE_PANEL = "InsertOverwrite";
    public static final String READONLY_ATTRIBUTE_PANEL = "ReadOnlyAttribute";
    public static final String POSITION_PANEL = "Position";
  }
}
