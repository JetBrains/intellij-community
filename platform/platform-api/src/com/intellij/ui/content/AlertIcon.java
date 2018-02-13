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
package com.intellij.ui.content;

import javax.swing.*;
import java.awt.*;

public class AlertIcon implements Icon {

  private final Icon myIcon;
  private final int myVShift;
  private final int myHShift;

  public AlertIcon(final Icon icon) {
    this(icon, 0, 0);
  }

  public AlertIcon(final Icon icon, final int VShift, final int HShift) {
    myIcon = icon;
    myVShift = VShift;
    myHShift = HShift;
  }

  public Icon getIcon() {
    return myIcon;
  }

  public int getVShift() {
    return myVShift;
  }

  public int getHShift() {
    return myHShift;
  }

  public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
    myIcon.paintIcon(c, g, x + myHShift, y + myVShift);
  }

  public int getIconWidth() {
    return myIcon.getIconWidth();
  }

  public int getIconHeight() {
    return myIcon.getIconHeight();
  }
}
