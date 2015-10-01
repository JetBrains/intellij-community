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
package com.intellij.openapi.ui;

import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationType;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class MessageType {

  public static final MessageType ERROR = new MessageType(AllIcons.General.NotificationError,
                                                          new JBColor(new Color(255, 204, 204), new Color(112, 71, 69)),
                                                          new JBColor(new Color(0xAC0013), new Color(0xEF5F65)));
  public static final MessageType INFO = new MessageType(AllIcons.General.NotificationInfo,
                                                         new JBColor(new Color(186, 238, 186), new Color(73, 117, 73)),
                                                         new JBColor(new Color(0x000000), new Color(0xbbbbbb)));
  public static final MessageType WARNING = new MessageType(AllIcons.General.NotificationWarning,
                                                            new JBColor(new Color(249, 247, 142), new Color(90, 82, 33)),
                                                            new JBColor(new Color(164, 145, 82), new Color(0xBBB529)));

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
