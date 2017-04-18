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
package com.intellij.ui.components.breadcrumbs;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JLabel;

/**
 * @author Sergey.Malenkov
 */
final class CrumbLabel extends JLabel {
  Crumb crumb;

  void setCrumb(Crumb crumb) {
    this.crumb = crumb;
    if (crumb == null) {
      setVisible(false);
      setBounds(0, 0, 0, 0);
    }
    else {
      setVisible(true);
      setIcon(crumb.getIcon());
      setText(crumb.getText());
      setToolTipText(crumb.getTooltip());
    }
  }

  private Breadcrumbs getBreadcrumbs() {
    Component component = crumb == null ? null : getParent();
    return component instanceof Breadcrumbs ? (Breadcrumbs)component : null;
  }

  @Override
  protected void paintComponent(Graphics g) {
    Breadcrumbs breadcrumbs = getBreadcrumbs();
    if (breadcrumbs != null && g instanceof Graphics2D) {
      breadcrumbs.paint((Graphics2D)g, 0, 0, getWidth(), getHeight(), crumb);
    }
    super.paintComponent(g);
  }

  @Override
  public Font getFont() {
    Breadcrumbs breadcrumbs = getBreadcrumbs();
    if (breadcrumbs != null) {
      Font font = breadcrumbs.getFont(crumb);
      if (font != null) return font;
    }
    return super.getFont();
  }

  @Override
  public Color getForeground() {
    Breadcrumbs breadcrumbs = getBreadcrumbs();
    return breadcrumbs != null
           ? breadcrumbs.getForeground(crumb)
           : super.getForeground();
  }

  @Override
  public void setFont(Font font) {
    // do not revalidate the label
  }

  @Override
  public void setForeground(Color color) {
    // do not revalidate the label
  }

  @Override
  public void setBackground(Color color) {
    // do not revalidate the label
  }

  @Override
  public void setOpaque(boolean isOpaque) {
    // do not paint background
  }
}
