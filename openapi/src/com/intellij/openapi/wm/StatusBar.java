/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import javax.swing.*;
import java.awt.*;

public interface StatusBar {
  /**
   * Set status bar text
   * @param s text to be shown in the status bar
   */
  void setInfo(String s);

  /**
   * Add arbitrary component indicating something related to status in the status bar. The component shall be
   * small enough to keep visual status bar appearance metaphor.
   * @param c - custom component to be added to the status bar.
   */
  void addCustomIndicationComponent(JComponent c);

  /**
   * Shows animated notification popup.
   * @param content Content of the notification
   * @param backgroundColor background color for the notification. Be sure use light colors since bright onces
   * look noisy. See {@link com.intellij.ui.LightColors} for convinient colors
   */
  void fireNotificationPopup(JComponent content, final Color backgroundColor);
}
