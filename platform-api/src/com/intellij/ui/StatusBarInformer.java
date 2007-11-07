/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.ui;

import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.IdeFrame;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.*;

import org.jetbrains.annotations.Nullable;

public class StatusBarInformer {

  private String myText;
  private StatusBar myStatusBar;
  private JComponent myComponent;

  public StatusBarInformer(final JComponent component, String text) {
    this(component, text, null);
  }

  public StatusBarInformer(final JComponent component, String text, StatusBar statusBar) {
    myText = text;
    myStatusBar = statusBar;
    myComponent = component;
    myComponent.addMouseListener(new MouseAdapter() {
      public void mouseEntered(final MouseEvent e) {
        final StatusBar bar = getStatusBar();
        final String text = getText();
        if (bar != null) {
          bar.setInfo(text);
        }
        myComponent.setToolTipText(text);
      }

      public void mouseExited(final MouseEvent e) {
        final StatusBar bar = getStatusBar();
        if (bar != null) {
          bar.setInfo(null);
        }
        myComponent.setToolTipText(null);
      }
    });
  }

  @Nullable
  protected String getText() {
    return myText;
  }

  @Nullable
  protected StatusBar getStatusBar() {
    if (myStatusBar != null) return myStatusBar;
    final Window window = SwingUtilities.getWindowAncestor(myComponent);
    if (window instanceof IdeFrame) {
      return ((IdeFrame)window).getStatusBar();
    }
    return null;
  }
}
