// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij.ui;

import com.intellij.util.ui.JBInsets;
import sun.awt.AWTAccessor;

import javax.swing.*;
import java.awt.*;

/**
 * Cell renderer CPU optimization.
 *
 * @author gregsh
 * @see javax.swing.table.DefaultTableCellRenderer#invalidate()
 */
public class CellRendererPanel extends JPanel {

  private boolean mySelected;

  public CellRendererPanel() {
    super(null); // we do the layout ourselves
    super.setOpaque(false); // to be consistent with #isOpaque
  }

  public final boolean isSelected() {
    return mySelected;
  }

  public final void setSelected(boolean isSelected) {
    mySelected = isSelected;
  }

  // property change support ----------------
  protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
  }

  public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {
  }

  // isOpaque() optimization ----------------
  public final boolean isOpaque() {
    return false;
  }

  @Override
  public final void setOpaque(boolean isOpaque) {
  }

  @Override
  protected void paintComponent(Graphics g) {
    if (mySelected) {
      g.setColor(getBackground());
      g.fillRect(0, 0, getWidth(), getHeight());
    }
  }

  // BEGIN no validation methods --------------
  @Override
  public void doLayout() {
    synchronized (getTreeLock()) {
      int count = getComponentCount();
      if (count == 1) {
        Rectangle bounds = new Rectangle(getWidth(), getHeight());
        JBInsets.removeFrom(bounds, getInsets());
        Component child = getComponent(0);
        child.setBounds(bounds);
        if (child instanceof CellRendererPanel) {
          ((CellRendererPanel)child).invalidateLayout();
          child.doLayout();
        }
      }
      else {
        invalidateLayout();
        super.doLayout();
        for (int i = 0; i < count; i++) {
          Component c = getComponent(i);
          if (c instanceof CellRendererPanel) {
            c.doLayout();
          }
        }
      }
    }
  }

  @Override
  public Dimension getPreferredSize() {
    if (getComponentCount() != 1) {
      return super.getPreferredSize();
    }
    return getComponent(0).getPreferredSize();
  }

  protected final Dimension super_getPreferredSize() {
    return super.getPreferredSize();
  }

  @Override
  public void reshape(int x, int y, int w, int h) {
    // suppress per-cell "moved" and "resized" events on paint
    // see Component#setBounds, Component#notifyNewBounds
    AWTAccessor.getComponentAccessor().setLocation(this, x, y);
    AWTAccessor.getComponentAccessor().setSize(this, w, h);
  }

  public void invalidate() {
  }

  public void forceInvalidate() {
    super.invalidate();
  }

  private void invalidateLayout() {
    LayoutManager layout = getLayout();
    if (layout instanceof LayoutManager2) {
      ((LayoutManager2)layout).invalidateLayout(this);
    }
  }

  public void validate() {
    doLayout();
  }

  protected final void super_validate() {
    super.validate();
  }

  public void revalidate() {
  }

  public void repaint(long tm, int x, int y, int width, int height) {
  }

  public void repaint(Rectangle r) {
  }

  public void repaint() {
  }

  // END no validation methods --------------

  public static class SuperPreferredSize extends CellRendererPanel {
    @Override
    public Dimension getPreferredSize() {
      return super_getPreferredSize();
    }
  }

  public static class SuperPreferredSizeWithBackground extends SuperPreferredSize {
    @Override
    protected void paintComponent(Graphics g) {
      g.setColor(getBackground());
      g.fillRect(0, 0, getWidth(), getHeight());
    }
  }

  public static class SuperValidate extends SuperPreferredSize {
    @Override
    public void validate() {
      super_validate();
    }
  }
}
