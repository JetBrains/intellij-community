// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.openapi.util.NlsContexts.Tooltip;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.*;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.NotNull;

import javax.accessibility.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.function.Consumer;

public class InplaceButton extends JComponent implements ActiveComponent, Accessible {
  private boolean myPainting = true;

  private final BaseButtonBehavior myBehavior;
  private final ActionListener myListener;

  private Icon myIcon;
  private CenteredIcon myRegular;
  private CenteredIcon myHovered;
  private CenteredIcon myInactive;

  private int myXTransform = 0;
  private int myYTransform = 0;
  private boolean myFill;

  private JBDimension mySize;

  private boolean myHoveringEnabled;

  public InplaceButton(@Tooltip String tooltip, Icon icon, ActionListener listener) {
    this(new IconButton(tooltip, icon, icon), listener, null, TimedDeadzone.DEFAULT);
  }

  public InplaceButton(IconButton source, ActionListener listener) {
    this(source, listener, null, TimedDeadzone.DEFAULT);
  }

  public InplaceButton(IconButton source, ActionListener listener, Consumer<? super MouseEvent> consumer, TimedDeadzone.Length mouseDeadzone) {
    myListener = listener;
    myBehavior = new BaseButtonBehavior(this, mouseDeadzone, null) {
      @Override
      protected void execute(MouseEvent e) {
        doClick(e);
      }

      @Override
      protected void repaint(Component c) {
        doRepaintComponent(c);
      }

      @Override
      protected void pass(MouseEvent e) {
        if (consumer != null) {
          consumer.accept(e);
        }
      }
    };
    myBehavior.setupListeners();

    setIcons(source);

    //setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    setToolTipText(source.getTooltip());
    setOpaque(false);
    setHoveringEnabled(true);
    if (ScreenReader.isActive()) {
      setFocusable(true);
    }
  }

  protected void doRepaintComponent(Component c) {
    c.repaint();
  }

  public void doClick() {
    RelativePoint point = new RelativePoint(this, new Point(this.getWidth() / 2, this.getHeight() / 2));
    doClick(point.toMouseEvent());
  }

  public void doClick(final MouseEvent e) {
    if (myListener != null && isEnabled()) {
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


    JBDimension size = JBDimension.create(new Dimension(width, height), true);
    if (mySize != null && !mySize.size().equals(size)) {
      invalidate();
    }
    mySize = size;

    myIcon = regular;
    myRegular = new CenteredIcon(regular, width, height);
    myHovered = new CenteredIcon(hovered, width, height);
    myInactive = new CenteredIcon(inactive, width, height);
  }

  @Override
  public Dimension getPreferredSize() {
    if (mySize == null || isPreferredSizeSet()) return super.getPreferredSize();
    return mySize.size();
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
    setEnabled(active);
    repaint();
  }

  public void setIcon(final Icon icon) {
    setIcons(icon, icon, icon);
  }

  public Icon getIcon() {
    return myIcon;
  }

  @NotNull
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

    if (!isEnabled()) {
      myInactive.paintIcon(this, g, 0, 0);
    }
    else if ((myBehavior.isHovered() && myHoveringEnabled) || hasFocus()) {
      paintHover(g);
      myHovered.paintIcon(this, g, 0, 0);
    }
    else {
      myRegular.paintIcon(this, g, 0, 0);
    }

    g.translate(0, 0);
  }

  protected void paintHover(Graphics g) {
  }

  public void setTransform(int x, int y) {
    myXTransform = x;
    myYTransform = y;
  }

  public void setHoveringEnabled(boolean enabled) {
    myHoveringEnabled = enabled;
  }

  public boolean isActive() {
    return isEnabled();
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
    public String getAccessibleDescription() {
      return AccessibleContextUtil.getUniqueDescription(this, super.getAccessibleDescription());
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
          if (ac instanceof AccessibleIcon) {
            accessibleIconList.add((AccessibleIcon)ac);
          }
        }
      }
      if (accessibleIconList.size() == 0) {
        return null;
      }

      return accessibleIconList.toArray(new AccessibleIcon[0]);
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