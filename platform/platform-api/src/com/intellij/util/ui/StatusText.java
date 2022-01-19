// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.ClickListener;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.UIBundle;
import com.intellij.ui.components.JBViewport;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

public abstract class StatusText {
  public static final SimpleTextAttributes DEFAULT_ATTRIBUTES = SimpleTextAttributes.GRAYED_ATTRIBUTES;

  private static final int Y_GAP = 2;

  private @Nullable Component myOwner;
  private Component myMouseTarget;
  private final @NotNull MouseMotionListener myMouseMotionListener;
  private final @NotNull ClickListener myClickListener;

  private boolean myIsDefaultText;

  private String myText = "";

  // Hardcoded layout manages two columns (primary and secondary) with vertically aligned components inside
  protected final class Column {
    List<Fragment> fragments = new ArrayList<>();
    private final Dimension preferredSize = new Dimension();
  }

  protected final class Fragment {
    private final SimpleColoredComponent myComponent = new SimpleColoredComponent() {
      @Override
      protected void revalidateAndRepaint() {
        super.revalidateAndRepaint();
        updateBounds();
      }
    };

    private final Rectangle boundsInColumn = new Rectangle();
    private final List<ActionListener> myClickListeners = ContainerUtil.createLockFreeCopyOnWriteList();

    public Fragment() {
      myComponent.setOpaque(false);
      myComponent.setFont(StartupUiUtil.getLabelFont());
    }
  }

  private final Column myPrimaryColumn = new Column();
  private final Column mySecondaryColumn = new Column();
  private boolean myHasActiveClickListeners; // calculated field for performance optimization
  private boolean myShowAboveCenter = true;
  private Font myFont = null;
  private boolean myCenterAlignText = true;

  protected StatusText(JComponent owner) {
    this();
    attachTo(owner);
  }

  public StatusText() {
    myClickListener = new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        if (e.getButton() == MouseEvent.BUTTON1 && clickCount == 1) {
          ActionListener actionListener = findActionListenerAt(e.getPoint());
          if (actionListener != null) {
            actionListener.actionPerformed(new ActionEvent(e, 0, ""));
            return true;
          }
        }
        return false;
      }
    };

    myMouseMotionListener = new MouseAdapter() {
      private Cursor myOriginalCursor;

      @Override
      public void mouseMoved(final MouseEvent e) {
        if (isStatusVisible()) {
          if (findActionListenerAt(e.getPoint()) != null) {
            if (myOriginalCursor == null) {
              myOriginalCursor = myMouseTarget.getCursor();
              myMouseTarget.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }
          }
          else if (myOriginalCursor != null) {
            myMouseTarget.setCursor(myOriginalCursor);
            myOriginalCursor = null;
          }
        }
      }
    };

    setText(getDefaultEmptyText(), DEFAULT_ATTRIBUTES);
    myIsDefaultText = true;
  }

  protected boolean isFontSet() {
    return myFont != null;
  }

  public void setFont(@NotNull Font font) {
    myPrimaryColumn.fragments.forEach(fragment -> fragment.myComponent.setFont(font));
    mySecondaryColumn.fragments.forEach(fragment -> fragment.myComponent.setFont(font));
    myFont = font;
  }

  public boolean isCenterAlignText() {
    return myCenterAlignText;
  }

  public void setCenterAlignText(boolean centerAlignText) {
    myCenterAlignText = centerAlignText;
  }

  public void attachTo(@Nullable Component owner) {
    attachTo(owner, owner);
  }

  public void attachTo(@Nullable Component owner, @Nullable Component mouseTarget) {
    if (myMouseTarget != null) {
      myClickListener.uninstall(myMouseTarget);
      myMouseTarget.removeMouseMotionListener(myMouseMotionListener);
    }

    myOwner = owner;
    myMouseTarget = mouseTarget;

    if (myMouseTarget != null) {
      myClickListener.installOn(myMouseTarget);
      myMouseTarget.addMouseMotionListener(myMouseMotionListener);
    }
  }

  protected abstract boolean isStatusVisible();

  private static @Nullable ActionListener findListener(@NotNull SimpleColoredComponent component,
                                                       @NotNull List<? extends ActionListener> listeners,
                                                       int xCoord) {
    int index = component.findFragmentAt(xCoord);
    if (index >= 0 && index < listeners.size()) {
      return listeners.get(index);
    }
    return null;
  }

  private @Nullable ActionListener findActionListenerAt(Point point) {
    if (!myHasActiveClickListeners || !isStatusVisible()) return null;

    point = SwingUtilities.convertPoint(myMouseTarget, point, myOwner);

    Rectangle commonBounds = getTextComponentBound();
    if (commonBounds.contains(point)) {
      ActionListener listener = getListener(myPrimaryColumn, point, commonBounds);
      if (listener != null) return listener;
      listener = getListener(mySecondaryColumn, point, commonBounds);
      if (listener != null) return listener;
    }
    return null;
  }

  private @Nullable ActionListener getListener(Column column, Point point, Rectangle commonBounds) {
    Point primaryLocation = getColumnLocation(column == myPrimaryColumn, commonBounds);
    for (Fragment fragment : column.fragments) {
      Rectangle fragmentBounds = getFragmentBounds(column, primaryLocation, commonBounds, fragment);
      if (!fragmentBounds.contains(new Point(point.x, point.y))) continue;
      ActionListener listener = findListener(fragment.myComponent, fragment.myClickListeners, point.x - fragmentBounds.x);
      if (listener != null) return listener;
    }
    return null;
  }

  protected Rectangle getTextComponentBound() {
    Rectangle ownerRec = myOwner == null ? new Rectangle(0, 0, 0, 0) : myOwner.getBounds();

    Dimension size = getPreferredSize();
    int x = (ownerRec.width - size.width) / 2;
    int y = (ownerRec.height - size.height) / (myShowAboveCenter ? 3 : 2);
    return new Rectangle(x, y, size.width, size.height);
  }

  public Point getPointBelow() {
    final var textComponentBound = getTextComponentBound();
    return new Point(textComponentBound.x, textComponentBound.y + textComponentBound.height);
  }

  public final boolean isShowAboveCenter() {
    return myShowAboveCenter;
  }

  public final StatusText setShowAboveCenter(boolean showAboveCenter) {
    myShowAboveCenter = showAboveCenter;
    return this;
  }

  public @NotNull String getText() {
    return myText;
  }

  public StatusText setText(@NlsContexts.StatusText String text) {
    return setText(text, DEFAULT_ATTRIBUTES);
  }

  public StatusText setText(@NlsContexts.StatusText String text, SimpleTextAttributes attrs) {
    return clear().appendText(text, attrs);
  }

  public StatusText clear() {
    myText = "";
    myPrimaryColumn.fragments.clear();
    mySecondaryColumn.fragments.clear();

    myHasActiveClickListeners = false;
    repaintOwner();
    return this;
  }

  private void repaintOwner() {
    if (myOwner != null && isStatusVisible()) myOwner.repaint();
  }

  public StatusText appendText(@NlsContexts.StatusText String text) {
    return appendText(text, DEFAULT_ATTRIBUTES);
  }

  public StatusText appendText(@NlsContexts.StatusText String text, SimpleTextAttributes attrs) {
    return appendText(text, attrs, null);
  }

  public StatusText appendText(@NlsContexts.StatusText String text, SimpleTextAttributes attrs, ActionListener listener) {
    if (myIsDefaultText) {
      clear();
      myIsDefaultText = false;
    }

    myText += text;
    return appendText(true, Math.max(0, myPrimaryColumn.fragments.size() - 1), text, attrs, listener);
  }

  public StatusText appendText(boolean isPrimaryColumn, int row, @NlsContexts.StatusText String text, SimpleTextAttributes attrs, ActionListener listener) {
    return appendText(isPrimaryColumn, row, null, text, attrs, listener);
  }

  public StatusText appendText(boolean isPrimaryColumn,
                               int row,
                               @Nullable Icon icon,
                               @NlsContexts.StatusText String text,
                               SimpleTextAttributes attrs,
                               ActionListener listener) {
    Fragment fragment = getOrCreateFragment(isPrimaryColumn, row);
    fragment.myComponent.setIcon(icon);
    fragment.myComponent.append(text, attrs);
    fragment.myClickListeners.add(listener);
    myHasActiveClickListeners |= listener != null;
    updateBounds();
    repaintOwner();
    return this;
  }

  private void updateBounds() {
    updateBounds(myPrimaryColumn);
    updateBounds(mySecondaryColumn);
  }

  private void updateBounds(Column column) {
    Dimension size = new Dimension();
    for (int i = 0; i < column.fragments.size(); i++) {
      Fragment fragment = column.fragments.get(i);
      Dimension d = fragment.myComponent.getPreferredSize();
      fragment.boundsInColumn.setBounds(0, size.height, d.width, d.height);
      size.height += d.height;
      if (i > 0) size.height += JBUIScale.scale(Y_GAP);
      size.width = Math.max(size.width, d.width);
    }
    if (myCenterAlignText) {
      for (int i = 0; i < column.fragments.size(); i++) {
        Fragment fragment = column.fragments.get(i);
        fragment.boundsInColumn.x += (size.width - fragment.boundsInColumn.width)/2;
      }
    }
    column.preferredSize.setSize(size);
  }

  private Fragment getOrCreateFragment(boolean isPrimaryColumn, int row) {
    Column column = isPrimaryColumn ? myPrimaryColumn : mySecondaryColumn;
    if (column.fragments.size() < row) {
      throw new IllegalStateException("Cannot add text to row " + row +
                                      " as in " + (isPrimaryColumn ? "left" : "right") +
                                      " column there are " + column.fragments.size() + " rows only");
    }
    Fragment fragment;
    if (column.fragments.size() == row) {
      fragment = new Fragment();
      if (myFont != null) {
        fragment.myComponent.setFont(myFont);
      }
      column.fragments.add(fragment);
    }
    else {
      fragment = column.fragments.get(row);
    }
    return fragment;
  }

  public @NotNull StatusText appendSecondaryText(@NotNull @NlsContexts.StatusText String text, @NotNull SimpleTextAttributes attrs, @Nullable ActionListener listener) {
    return appendText(true, 1, text, attrs, listener);
  }

  public @NotNull StatusText appendLine(@NotNull @NlsContexts.StatusText String text) {
    return appendLine(text, DEFAULT_ATTRIBUTES, null);
  }

  public StatusText appendLine(@NotNull @NlsContexts.StatusText String text,
                               @NotNull SimpleTextAttributes attrs,
                               @Nullable ActionListener listener) {
    return appendLine(null, text, attrs, listener);
  }

  public StatusText appendLine(@Nullable Icon icon,
                               @NotNull @NlsContexts.StatusText String text,
                               @NotNull SimpleTextAttributes attrs,
                               @Nullable ActionListener listener) {
    if (myIsDefaultText) {
      clear();
      myIsDefaultText = false;
    }
    return appendText(true, myPrimaryColumn.fragments.size(), icon, text, attrs, listener);
  }

  public void paint(Component owner, Graphics g) {
    if (!isStatusVisible()) return;

    if (owner == myOwner) {
      doPaintStatusText(g, getTextComponentBound());
    }
    else {
      paintOnComponentUnderViewport(owner, g);
    }
  }

  private void paintOnComponentUnderViewport(Component component, Graphics g) {
    JBViewport viewport = ObjectUtils.tryCast(myOwner, JBViewport.class);
    if (viewport == null || viewport.getView() != component || viewport.isPaintingNow()) return;

    // We're painting a component which has a viewport as it's ancestor.
    // As the viewport paints status text, we'll erase it, so we need to schedule a repaint for the viewport with status text's bounds.
    // But it causes flicker, so we paint status text over the component first and then schedule the viewport repaint.

    Rectangle textBoundsInViewport = getTextComponentBound();

    int xInOwner = textBoundsInViewport.x - component.getX();
    int yInOwner = textBoundsInViewport.y - component.getY();
    Rectangle textBoundsInOwner = new Rectangle(xInOwner, yInOwner, textBoundsInViewport.width, textBoundsInViewport.height);
    doPaintStatusText(g, textBoundsInOwner);

    viewport.repaint(textBoundsInViewport);
  }

  private Point getColumnLocation(boolean isPrimary, Rectangle bounds) {
    if (isPrimary && mySecondaryColumn.fragments.isEmpty()) {
      return new Point(bounds.x + (bounds.width - myPrimaryColumn.preferredSize.width) / 2, bounds.y);
    }
    if (isPrimary) return new Point(bounds.x, bounds.y);
    return new Point(bounds.x + bounds.width - mySecondaryColumn.preferredSize.width, bounds.y);
  }

  private void doPaintStatusText(@NotNull Graphics g, @NotNull Rectangle bounds) {
    paintColumnInBounds(myPrimaryColumn, g, getColumnLocation(true, bounds), bounds);
    paintColumnInBounds(mySecondaryColumn, g, getColumnLocation(false, bounds), bounds);
  }

  protected @NotNull Rectangle adjustComponentBounds(@NotNull JComponent component, @NotNull Rectangle bounds) {
    Dimension size = component.getPreferredSize();

    if (mySecondaryColumn.fragments.isEmpty()) {
      return new Rectangle(bounds.x + (bounds.width - size.width) / 2, bounds.y, size.width, size.height);
    }
    else {
      return component == getComponent()
             ? new Rectangle(bounds.x, bounds.y, size.width, size.height)
             : new Rectangle(bounds.x + bounds.width - size.width, bounds.y, size.width, size.height);
    }
  }

  private void paintColumnInBounds(Column column, Graphics g, Point location, Rectangle bounds) {
    for (Fragment fragment : column.fragments) {
      Rectangle r = getFragmentBounds(column, location, bounds, fragment);
      paintComponentInBounds(fragment.myComponent, g, r);
    }
  }

  private @NotNull Rectangle getFragmentBounds(Column column, Point columnLocation, Rectangle bounds, Fragment fragment) {
    Rectangle r = new Rectangle();
    r.setBounds(fragment.boundsInColumn);
    r.x += columnLocation.x;
    r.y += columnLocation.y;
    if (column.fragments.size() == 1) {
      r = adjustComponentBounds(fragment.myComponent, bounds);
    }
    return r;
  }

  private static void paintComponentInBounds(@NotNull SimpleColoredComponent component, @NotNull Graphics g, @NotNull Rectangle bounds) {
    Graphics2D g2 = (Graphics2D)g.create(bounds.x, bounds.y, bounds.width, bounds.height);
    try {
      component.setBounds(0, 0, bounds.width, bounds.height);
      component.paint(g2);
    }
    finally {
      g2.dispose();
    }
  }

  public @NotNull SimpleColoredComponent getComponent() {
    return getOrCreateFragment(true, 0).myComponent;
  }

  public @NotNull SimpleColoredComponent getSecondaryComponent() {
    return getOrCreateFragment(true, 1).myComponent;
  }

  public Dimension getPreferredSize() {
    return new Dimension(myPrimaryColumn.preferredSize.width + mySecondaryColumn.preferredSize.width,
                         Math.max(myPrimaryColumn.preferredSize.height, mySecondaryColumn.preferredSize.height));
  }

  public static @NlsContexts.StatusText String getDefaultEmptyText() {
    return UIBundle.message("message.nothingToShow");
  }
}
