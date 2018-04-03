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
package com.intellij.ide.ui.laf.intellij;

import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class MacIntelliJCheckBoxUI extends IntelliJCheckBoxUI {
  public static final Icon DEFAULT_ICON = JBUI.scale(EmptyIcon.create(22));

  public MacIntelliJCheckBoxUI(JCheckBox c) {
    c.setOpaque(false);
  }

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new MacIntelliJCheckBoxUI(((JCheckBox)c));
  }

  @Override
  protected void drawCheckIcon(JComponent c, Graphics2D g, AbstractButton b, Rectangle iconRect, boolean selected, boolean enabled) {
    String iconName = isIndeterminate(b) ? "checkBoxIndeterminate" : "checkBox";
    Icon icon = MacIntelliJIconCache.getIcon(iconName, selected || isIndeterminate(b), c.hasFocus(), b.isEnabled());
    icon.paintIcon(c, g, iconRect.x, iconRect.y);
  }

  @Override
  public Icon getDefaultIcon() {
    return DEFAULT_ICON;
  }
}
