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
package com.intellij.openapi.ui;

import com.intellij.notification.NotificationType;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

public class MessageType {

  public static final MessageType ERROR = new MessageType(UIUtil.getBalloonErrorIcon(), new Color(255, 204, 204, 230));
  public static final MessageType INFO = new MessageType(UIUtil.getBalloonInformationIcon(), new Color(186, 238, 186, 230));
  public static final MessageType WARNING = new MessageType(UIUtil.getBalloonWarningIcon(), new Color(249, 247, 142, 230));

  private final Icon myDefaultIcon;
  private final Color myPopupBackground;

  private MessageType(final Icon defaultIcon, Color popupBackground) {
    myDefaultIcon = defaultIcon;
    myPopupBackground = popupBackground;
  }

  public Icon getDefaultIcon() {
    return myDefaultIcon;
  }

  public Color getPopupBackground() {
    return myPopupBackground;
  }

  public NotificationType toNotificationType() {
    return this == ERROR ? NotificationType.ERROR : this == WARNING ? NotificationType.WARNING : NotificationType.INFORMATION;
  }
}
