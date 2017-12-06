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

/*
 * @author: Eugene Zhuravlev
 */
package com.intellij.debugger.ui;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

public class GetJPDADialog extends DialogWrapper {
  private static final @NonNls String JPDA_URL = "http://java.sun.com/products/jpda";

  public GetJPDADialog() {
    super(false);
    setTitle(DebuggerBundle.message("get.jpda.dialog.title"));
    setResizable(false);
    init();
  }

  @NotNull
  protected Action[] createActions() {
    return new Action[]{getOKAction()};
  }

  protected JComponent createCenterPanel() {
    final JPanel _panel1 = new JPanel(new BorderLayout());

    JPanel _panel2 = new JPanel(new BorderLayout());
    _panel2.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    //"Debug libraries are missig from JDK home.\nIn order for debugger to start, the libraries should be installed.\nPlease visit http://java.sun.com/products/jpda"
    JLabel label1 = new JLabel(DebuggerBundle.message("label.get.jpda.dialog.prompt"));
    //label1.setForeground(Color.black);
    JLabel label2 = new JLabel(JPDA_URL);
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent e) {
        BrowserUtil.browse(JPDA_URL);
        return true;
      }
    }.installOn(label2);
    label2.setForeground(JBColor.BLUE.darker());
    label2.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    _panel2.add(new JLabel(DebuggerBundle.message("label.get.jpda.dialog.error.description")), BorderLayout.NORTH);
    _panel2.add(label1, BorderLayout.WEST);
    _panel2.add(label2, BorderLayout.EAST);
    _panel1.add(_panel2, BorderLayout.NORTH);

    JPanel content = new JPanel(new GridLayout(2, 1, 10, 10));

    _panel1.add(content, BorderLayout.CENTER);
    return _panel1;
  }
}

