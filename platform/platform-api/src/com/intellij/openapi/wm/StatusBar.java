// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Status bar shown on the bottom of IDE frame.
 * <p>
 * Displays {@link Info#set(String, Project) status text} and
 * a number of {@link StandardWidgets builtin} and custom {@link StatusBarWidget widgets}.
 *
 * @see StatusBarWidgetProvider
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

  /**
   * Adds the given widget on the right.
   *
   * @deprecated Use {@link StatusBarWidgetProvider}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  void addWidget(@NotNull StatusBarWidget widget);

  /**
   * Adds the given widget positioned according to given anchor (see {@link Anchors}).
   *
   * @deprecated Use {@link StatusBarWidgetProvider}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  void addWidget(@NotNull StatusBarWidget widget, @NotNull String anchor);

  /**
   * @deprecated Use {@link StatusBarWidgetProvider}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  void addWidget(@NotNull StatusBarWidget widget, @NotNull Disposable parentDisposable);

  /**
   * @deprecated Use {@link StatusBarWidgetProvider}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  void addWidget(@NotNull StatusBarWidget widget, @NotNull String anchor, @NotNull Disposable parentDisposable);

  /**
   * @deprecated Use {@link StatusBarWidgetProvider}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  void addCustomIndicationComponent(@NotNull JComponent c);

  /**
   * @deprecated Use {@link StatusBarWidgetProvider}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  void removeCustomIndicationComponent(@NotNull JComponent c);

  /**
   * @deprecated Use {@link StatusBarWidgetProvider}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  void removeWidget(@NotNull String id);

  void updateWidget(@NotNull String id);

  @Nullable
  StatusBarWidget getWidget(String id);

  void fireNotificationPopup(@NotNull JComponent content, Color backgroundColor);

  @Nullable
  StatusBar createChild(@NotNull IdeFrame frame);

  JComponent getComponent();

  StatusBar findChild(Component c);

  @Nullable
  IdeFrame getFrame();

  @Nullable
  Project getProject();

  /**
   * @deprecated use {@link #createChild(IdeFrame)} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  default StatusBar createChild() {
    return this;
  }

  /**
   * @deprecated frame is immutable now
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  default void install(@NotNull IdeFrame frame) {}

  final class Anchors {
    public static final String DEFAULT_ANCHOR = after(StandardWidgets.COLUMN_SELECTION_MODE_PANEL);

    public static String before(String widgetId) {
      return "before " + widgetId;
    }

    public static String after(String widgetId) {
      return "after " + widgetId;
    }
  }

  final class StandardWidgets {
    public static final String ENCODING_PANEL = "Encoding";
    public static final String COLUMN_SELECTION_MODE_PANEL = "InsertOverwrite"; // Keep the old ID for backwards compatibility
    public static final String READONLY_ATTRIBUTE_PANEL = "ReadOnlyAttribute";
    public static final String POSITION_PANEL = "Position";
  }
}
