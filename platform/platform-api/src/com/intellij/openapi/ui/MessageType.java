/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.ui;

import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationType;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class MessageType {

  public static final MessageType ERROR = new MessageType(AllIcons.General.NotificationError,
                                                          JBColor.namedColor("Notification.ToolWindow.errorBackground", new JBColor(0xffcccc, 0x704745)),
                                                          JBColor.namedColor("Notification.ToolWindow.errorForeground", UIUtil.getToolTipForeground()),
                                                          JBColor.namedColor("Notification.ToolWindow.errorBorderColor", new JBColor(0xd69696, 0x998a8a)));

  public static final MessageType INFO = new MessageType(AllIcons.General.NotificationInfo,
                                                         JBColor.namedColor("Notification.ToolWindow.informativeBackground", new JBColor(0xbaeeba, 0x33412E)),
                                                         JBColor.namedColor("Notification.ToolWindow.informativeForeground", UIUtil.getToolTipForeground()),
                                                         JBColor.namedColor("Notification.ToolWindow.informativeBorderColor", new JBColor(0xa0bf9d, 0x85997a)));

  public static final MessageType WARNING = new MessageType(AllIcons.General.NotificationWarning,
                                                            JBColor.namedColor("Notification.ToolWindow.warningBackground", new JBColor(0xf9f78e, 0x5a5221)),
                                                            JBColor.namedColor("Notification.ToolWindow.warningForeground", UIUtil.getToolTipForeground()),
                                                            JBColor.namedColor("Notification.ToolWindow.warningBorderColor", new JBColor(0xbab824, 0xa69f63)));

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
