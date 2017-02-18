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
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class MessageType {

  public static final MessageType ERROR = new MessageType(AllIcons.General.NotificationError,
                                                          new JBColor(0xffcccc, 0x704745),
                                                          new JBColor(0xac0013, 0xef5f65));

  public static final MessageType INFO = new MessageType(AllIcons.General.NotificationInfo,
                                                         new JBColor(0xbaeeba, 0x33412E),
                                                         new JBColor(0x000000, 0xbbbbbb));

  public static final MessageType WARNING = new MessageType(AllIcons.General.NotificationWarning,
                                                            new JBColor(0xf9f78e, 0x5a5221),
                                                            new JBColor(0xa49152, 0xbbb529));

  private final Icon myDefaultIcon;
  private final Color myPopupBackground;
  @NotNull private final Color myForeground;

  private MessageType(@NotNull Icon defaultIcon, @NotNull Color popupBackground, @NotNull Color foreground) {
    myDefaultIcon = defaultIcon;
    myPopupBackground = popupBackground;
    myForeground = foreground;
  }

  @NotNull
  public Icon getDefaultIcon() {
    return myDefaultIcon;
  }

  @NotNull
  public Color getPopupBackground() {
    return myPopupBackground;
  }

  public Color getTitleForeground() {
    return myForeground;
  }

  @NotNull
  public NotificationType toNotificationType() {
    return this == ERROR ? NotificationType.ERROR : this == WARNING ? NotificationType.WARNING : NotificationType.INFORMATION;
  }
}
