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
package com.intellij.ui;

import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.openapi.util.Pass;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.BaseButtonBehavior;
import com.intellij.util.ui.CenteredIcon;
import com.intellij.util.ui.TimedDeadzone;
import com.intellij.util.ui.UIUtil;

import javax.accessibility.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

public class InplaceButton extends JComponent implements ActiveComponent, Accessible {

  private boolean myPainting = true;
  private boolean myActive = true;

  private BaseButtonBehavior myBehavior;
  private ActionListener myListener;

  private CenteredIcon myRegular;
  private CenteredIcon myHovered;
  private CenteredIcon myInactive;

  private int myXTransform = 0;
  private int myYTransform = 0;
  private boolean myFill;

  private boolean myHoveringEnabled;

  public InplaceButton(String tooltip, final Icon icon, final ActionListener listener) {
    this(new IconButton(tooltip, icon, icon), listener, null);
  }

  public InplaceButton(String tooltip, final Icon icon, final ActionListener listener, final Pass<MouseEvent> me) {
    this(new IconButton(tooltip, icon, icon), listener, me);
  }

  public InplaceButton(IconButton source, final ActionListener listener) {
    this(source, listener, null);
  }

  public InplaceButton(IconButton source, final ActionListener listener, final Pass<MouseEvent> me) {
    this(source, listener, me, TimedDeadzone.DEFAULT);
  }

  public InplaceButton(IconButton source, final ActionListener listener, final Pass<MouseEvent> me, TimedDeadzone.Length mouseDeadzone) {
    myListener = listener;
    myBehavior = new BaseButtonBehavior(this, mouseDeadzone) {
      @Override
      protected void execute(final MouseEvent e) {
        doClick(e);
      }

      @Override
      protected void repaint(Component c) {
        doRepaintComponent(c);
      }

      @Override
      protected void pass(final MouseEvent e) {
        if (me != null) {
          me.pass(e);
        }
      }
    };

    setIcons(source);

    //setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    setToolTipText(source.getTooltip());
    setOpaque(false);
    setHoveringEnabled(true);
  }

  protected void doRepaintComponent(Component c) {
    c.repaint();
  }

  public void doClick() {
    RelativePoint point = new RelativePoint(this, new Point(this.getWidth() / 2, this.getHeight() / 2));
    doClick(point.toMouseEvent());
  }

  public void doClick(final MouseEvent e) {
    if (myListener != null) {
      myListener.actionPerformed(new ActionEvent(e, ActionEvent.ACTION_PERFORMED, "execute", e.getModifiers()));
    }
  }

  public void setMouseDeadzone(final TimedDeadzone.Length deadZone) {
    myBehavior.setMouseDeadzone(deadZone);
  }

  public void setIcons(IconButton source) {
    setIcons(source.getRegular(), source.getInactive(), source.getHovered());
  }

  public void setIcons(final Icon regular, Icon inactive, Icon hovered) {
    if (regular == null) return;
    if (inactive == null) inactive = regular;
    if (hovered == null) hovered = regular;

    int width = Math.max(regular.getIconWidth(), inactive.getIconWidth());
    width = Math.max(width, hovered.getIconWidth());
    int height = Math.max(regular.getIconHeight(), inactive.getIconHeight());
    height = Math.max(height, hovered.getIconHeight());


    setPreferredSize(new Dimension(width, height));

    myRegular = new CenteredIcon(regular, width, height);
    myHovered = new CenteredIcon(hovered, width, height);
    myInactive = new CenteredIcon(inactive, width, height);
  }

  public InplaceButton setFillBg(boolean fill) {
    myFill = fill;
    return this;
  }

  public void setPainting(final boolean active) {
    if (myPainting == active) return;

    myPainting = active;

    repaint();
  }

  @Override
  public void setActive(final boolean active) {
    myActive = active;
    repaint();
  }

  public void setIcon(final Icon icon) {
    setIcons(icon, icon, icon);
  }

  @Override
  public JComponent getComponent() {
    return this;
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    if (!myPainting) return;

    if (myFill) {
      g.setColor(UIUtil.getBgFillColor(this));
      g.fillRect(0, 0, getWidth(), getHeight());
    }

    g.translate(myXTransform, myYTransform);


    if (myBehavior.isHovered() && myHoveringEnabled) {
      if (myBehavior.isPressedByMouse()) {
        myHovered.paintIcon(this, g, 1, 1);
      }
      else {
        myHovered.paintIcon(this, g, 0, 0);
      }
    }
    else {
      if (isActive()) {
        myRegular.paintIcon(this, g, 0, 0);
      }
      else {
        myInactive.paintIcon(this, g, 0, 0);
      }
    }

    g.translate(0, 0);
  }

  public void setTransform(int x, int y) {
    myXTransform = x;
    myYTransform = y;
  }

  public void setHoveringEnabled(boolean enabled) {
    myHoveringEnabled = enabled;
  }

  public boolean isActive() {
    return myActive;
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleInplaceButton();
    }
    return accessibleContext;
  }

  /**
   * The Accessible implementation of InplaceButton is a subset of AccessibleAbstractButton.
   */
  protected class AccessibleInplaceButton extends AccessibleJComponent implements AccessibleAction, AccessibleExtendedComponent {

    @Override
    public String getAccessibleName() {
      String name = accessibleName;

      if (name == null) {
        name = (String)getClientProperty(AccessibleContext.ACCESSIBLE_NAME_PROPERTY);
      }
      if (name == null) {
        name = InplaceButton.this.getToolTipText();
      }
      if (name == null) {
        name = super.getAccessibleName();
      }
      return name;
    }

    @Override
    public AccessibleRole getAccessibleRole() {
      return AccessibleRole.PUSH_BUTTON;
    }

    @Override
    public int getAccessibleActionCount() {
      return 1;
    }

    @Override
    public String getAccessibleActionDescription(int i) {
      if (i == 0) {
        return "Click";
      } else {
        return null;
      }
    }

    @Override
    public boolean doAccessibleAction(int i) {
      if (i == 0) {
        doClick();
        return true;
      } else {
        return false;
      }
    }

    @Override
    public AccessibleAction getAccessibleAction() {
      return this;
    }

    @Override
    public AccessibleIcon[] getAccessibleIcon() {
      Icon[] icons = {myRegular, myInactive, myHovered};
      ArrayList<AccessibleIcon> accessibleIconList = new ArrayList<>();
      for (Icon icon : icons) {
        if (icon instanceof Accessible) {
          AccessibleContext ac = ((Accessible)icon).getAccessibleContext();
          if (ac != null && ac instanceof AccessibleIcon) {
            accessibleIconList.add((AccessibleIcon)ac);
          }
        }
      }
      if (accessibleIconList.size() == 0) {
        return null;
      }

      return accessibleIconList.toArray(new AccessibleIcon[accessibleIconList.size()]);
    }

    @Override
    public AccessibleStateSet getAccessibleStateSet() {
      AccessibleStateSet states = super.getAccessibleStateSet();
      if (isFocusOwner()) {
        states.add(AccessibleState.FOCUSED);
      }
      return states;
    }

    // ----- AccessibleExtendedComponent

    @SuppressWarnings("unused")
    AccessibleExtendedComponent getAccessibleExtendedComponent() {
      return this;
    }

    @Override
    public String getToolTipText() {
      return InplaceButton.this.getToolTipText();
    }

    @Override
    public String getTitledBorderText() {
      return null;
    }

    @Override
    public AccessibleKeyBinding getAccessibleKeyBinding() {
      return null;
    }
  }
}
