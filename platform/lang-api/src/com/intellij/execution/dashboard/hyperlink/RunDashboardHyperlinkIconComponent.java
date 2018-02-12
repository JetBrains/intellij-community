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
package com.intellij.execution.dashboard.hyperlink;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Aleev
 */
public class RunDashboardHyperlinkIconComponent extends RunDashboardHyperlinkComponentBase implements Icon {
  @NotNull private final Icon myIcon;
  @Nullable private final Icon myHoveredIcon;

  public RunDashboardHyperlinkIconComponent(@Nullable RunDashboardHyperlinkComponentBase.LinkListener listener,
                                            @NotNull Icon icon,
                                            @Nullable Icon hoveredIcon) {
    super(listener);
    myIcon = icon;
    myHoveredIcon = hoveredIcon;
  }

  @NotNull
  public Icon getIcon() {
    return myHoveredIcon != null && isAimed() ? myHoveredIcon : myIcon;
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    getIcon().paintIcon(c, g, x, y);
  }

  @Override
  public int getIconWidth() {
    return getIcon().getIconWidth();
  }

  @Override
  public int getIconHeight() {
    return getIcon().getIconHeight();
  }
}
