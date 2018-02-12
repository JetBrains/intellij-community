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
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ide.ui.laf.darcula.ui.DarculaEditorTextFieldBorder;
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextFieldUI;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.text.JTextComponent;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 * @author Sergey Malenkov
 */
public class MacIntelliJTextFieldUI extends DarculaTextFieldUI {

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(final JComponent c) {
    return new MacIntelliJTextFieldUI();
  }

  @Override
  protected void updateIconsLayout(Rectangle bounds) {
    super.updateIconsLayout(bounds);
    JTextComponent component = getComponent();
    if (component == null || component.hasFocus()) return;
    IconHolder clear = icons.get("clear");
    if (clear == null || clear.icon != null) return;
    IconHolder search = icons.get("search");
    if (search == null || search.icon == null || search.isClickable()) return;
    search.bounds.x = bounds.x + (bounds.width - search.bounds.width) / 2;
  }

  @Override
  protected int getMinimumHeight() {
    return DarculaEditorTextFieldBorder.isComboBoxEditor(getComponent()) ? JBUI.scale(18) : JBUI.scale(26);
  }

  @Override
  protected float bw() {
    return JBUI.scale(3);
  }
}
