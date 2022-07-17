// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui;

import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationType;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public final class MessageType {

  public static final MessageType ERROR = new MessageType(AllIcons.General.BalloonError,
                                                          JBUI.CurrentTheme.NotificationError.backgroundColor(),
                                                          JBUI.CurrentTheme.NotificationError.foregroundColor(),
                                                          JBUI.CurrentTheme.NotificationError.borderColor());

  public static final MessageType INFO = new MessageType(AllIcons.General.BalloonInformation,
                                                         JBUI.CurrentTheme.NotificationInfo.backgroundColor(),
                                                         JBUI.CurrentTheme.NotificationInfo.foregroundColor(),
                                                         JBUI.CurrentTheme.NotificationInfo.borderColor());

  public static final MessageType WARNING = new MessageType(AllIcons.General.BalloonWarning,
                                                            JBUI.CurrentTheme.NotificationWarning.backgroundColor(),
                                                            JBUI.CurrentTheme.NotificationWarning.foregroundColor(),
                                                            JBUI.CurrentTheme.NotificationWarning.borderColor());
  private final Icon myDefaultIcon;
  private final Color myPopupBackground;
  private final Color myForeground;
  private final Color myBorderColor;

  private MessageType(@NotNull Icon defaultIcon, @NotNull Color popupBackground, @NotNull Color foreground, @NotNull Color borderColor) {
    myDefaultIcon = defaultIcon;
    myPopupBackground = popupBackground;
    myForeground = foreground;
    myBorderColor = borderColor;
  }

  @NotNull
  public Icon getDefaultIcon() {
    return myDefaultIcon;
  }

  @NotNull
  public Color getPopupBackground() {
    return myPopupBackground;
  }

  @NotNull
  public Color getTitleForeground() {
    return myForeground;
  }

  @NotNull
  public Color getBorderColor() {
    return myBorderColor;
  }

  @NotNull
  public NotificationType toNotificationType() {
    return this == ERROR ? NotificationType.ERROR : this == WARNING ? NotificationType.WARNING : NotificationType.INFORMATION;
  }
}
