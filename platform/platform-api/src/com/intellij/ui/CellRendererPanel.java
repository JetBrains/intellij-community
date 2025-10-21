// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.util.ui.JBInsets;
import sun.awt.AWTAccessor;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
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
    this(null);
  }

  public CellRendererPanel(LayoutManager lm) {
    super(lm);
    super.setOpaque(false); // to be consistent with #isOpaque
    super.setFont(null);
  }

  public final boolean isSelected() {
    return mySelected;
  }

  public final void setSelected(boolean isSelected) {
    mySelected = isSelected;
  }

  public void setForcedBackground(Color bg) {
    super.setBackground(bg);
    if (bg != null && !mySelected) {
      setSelected(true);
    }
  }

  // property change support ----------------
  @Override
  protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
  }

  @Override
  public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {
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
    if (getWidth() == 0 || getHeight() == 0) return;
    synchronized (getTreeLock()) {
      int count = getComponentCount();
      if (count == 1) {
        Rectangle bounds = new Rectangle(getWidth(), getHeight());
        JBInsets.removeFrom(bounds, getInsets());
        JComponent child = (JComponent)getComponent(0);
        reshapeImpl(child, bounds.x, bounds.y, bounds.width, bounds.height);
        invalidateLayout(child);
        child.validate();
      }
      else {
        invalidateLayout(this);
        super.doLayout();
        for (int i = 0; i < count; i++) {
          Component c = getComponent(i);
          c.validate();
        }
      }
    }
  }

  @Override
  public Dimension getPreferredSize() {
    if (getComponentCount() != 1 || super.getBorder() != null) {
      return super.getPreferredSize();
    }
    return getComponent(0).getPreferredSize();
  }

  protected final Dimension super_getPreferredSize() {
    return super.getPreferredSize();
  }

  /**
   * Calculate preferred size via layout manager every time.
   *
   * <p>
   *   When running {@link Container#validateTree()} the flag {@link Component#valid}
   * can change its value to {@code true}. But {@link CellRendererPanel#invalidate()} has empty body and never rewrites the flag value.
   * Therefore {@link Component#preferredSize()} uses a cached value for preferred size and never changes it after.
   * </p>
   *
   * <p>
   *   To avoid that CellRendererPanel overrides default implementation to calculate preferred size via layout manager every time.
   * </p>
   *
   * @deprecated do not this method directly, use {@link #getPreferredSize()} instead
   */
  @Deprecated
  @Override
  public final Dimension preferredSize() {
    LayoutManager layoutMgr = getLayout();
    return (layoutMgr != null) ?
           layoutMgr.preferredLayoutSize(this) :
           super.preferredSize();
  }

  @Override
  public void reshape(int x, int y, int w, int h) {
    reshapeImpl(this, x, y, w, h);
  }

  static void reshapeImpl(JComponent component, int x, int y, int w, int h) {
    // suppress per-cell "moved" and "resized" events on paint
    // see Component#setBounds, Component#notifyNewBounds
    AWTAccessor.getComponentAccessor().setLocation(component, x, y);
    AWTAccessor.getComponentAccessor().setSize(component, w, h);
  }

  @Override
  public void invalidate() {
  }

  public void forceInvalidate() {
    super.invalidate();
  }

  private static void invalidateLayout(JComponent component) {
    LayoutManager layout = component.getLayout();
    if (layout instanceof LayoutManager2) {
      ((LayoutManager2)layout).invalidateLayout(component);
    }
  }

  @Override
  public void validate() {
    doLayout();
  }

  protected final void super_validate() {
    super.validate();
  }

  @Override
  public void revalidate() {
  }

  @Override
  public void repaint(long tm, int x, int y, int width, int height) {
  }

  @Override
  public void repaint(Rectangle r) {
  }

  @Override
  public void repaint() {
  }

  // END no validation methods --------------

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleJPanel() {
        @Override
        public AccessibleRole getAccessibleRole() {
          return AccessibleRole.LABEL;
        }
      };
    }
    return accessibleContext;
  }
}
