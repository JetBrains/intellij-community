/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ui.ColorUtil;
import com.intellij.ui.DarculaColors;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicEditorPaneUI;
import javax.swing.text.EditorKit;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaEditorPaneUI extends BasicEditorPaneUI {
  private final JEditorPane myEditorPane;

  public DarculaEditorPaneUI(JComponent comp) {
    myEditorPane = ((JEditorPane)comp);
  }

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static ComponentUI createUI(JComponent comp) {
    return new DarculaEditorPaneUI(comp);
  }

  @Override
  protected void installDefaults() {
    super.installDefaults();
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (myEditorPane == null) return;
        Color fg = myEditorPane.getForeground();
        if ((fg == null) || (fg instanceof UIResource)) {
          myEditorPane.setForeground(UIManager.getColor(getPropertyPrefix() + ".foreground"));
          final EditorKit kit = myEditorPane.getEditorKit();
          if (kit instanceof HTMLEditorKit) {
            ((HTMLEditorKit)kit).getStyleSheet().addRule("body {color: #" + ColorUtil.toHex(UIUtil.getLabelForeground()) + ";}");
            ((HTMLEditorKit)kit).getStyleSheet().addRule("a {color: #" + ColorUtil.toHex(DarculaColors.BLUE) + ";}");
            //((HTMLEditorKit)kit).getStyleSheet().getRule("a").addAttribute(StyleConstants.Foreground, DarculaColors.BLUE);
          }
        }
      }
    });
  }

}
