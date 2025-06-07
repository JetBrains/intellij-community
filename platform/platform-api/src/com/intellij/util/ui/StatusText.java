// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.ClickListener;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.UIBundle;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBViewport;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class StatusText {
  public static final SimpleTextAttributes DEFAULT_ATTRIBUTES = SimpleTextAttributes.GRAYED_ATTRIBUTES;

  private static final int DEFAULT_Y_GAP = 2;

  private @Nullable Component myOwner;
  private @Nullable Component myMouseTarget;
  private final @NotNull MouseMotionListener myMouseMotionListener;
  private final @NotNull ClickListener myClickListener;
  private final @NotNull HierarchyListener myHierarchyListener;

  private boolean myIsDefaultText;
  private boolean myInLoadingPanel;

  private String myText = "";
  private int yGap = DEFAULT_Y_GAP;
  private boolean forceGapAfterLastLine = false;

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

      @Override
      public void updateUI() {
        super.updateUI();
        setOpaque(false);
        if (myFont == null) {
          setFont(StartupUiUtil.getLabelFont());
        }
        updateBounds();
      }
    };

    private final Rectangle boundsInColumn = new Rectangle();
    private final List<ActionListener> myClickListeners = ContainerUtil.createLockFreeCopyOnWriteList();
    private final int gapAfter;

    Fragment(int gapAfter) {
      this.gapAfter = gapAfter;
      myComponent.setOpaque(false);
      myComponent.setFont(StartupUiUtil.getLabelFont());
    }
  }

  private final List<Column> columns = new ArrayList<>(List.of(new Column()));
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
        if (isStatusVisibleInner()) {
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

    myHierarchyListener = event -> {
      if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) <= 0) return;
      myInLoadingPanel = UIUtil.getParentOfType(JBLoadingPanel.class, myOwner) != null;
    };

    setText(getDefaultEmptyText(), DEFAULT_ATTRIBUTES);
    myIsDefaultText = true;
  }

  protected boolean isFontSet() {
    return myFont != null;
  }

  public void setFont(@NotNull Font font) {
    setFontImpl(font);
  }

  public void resetFont() {
    setFontImpl(null);
  }

  private void setFontImpl(Font font) {
    for (var column : columns) {
      for (var fragment : column.fragments) {
        fragment.myComponent.setFont(font);
      }
    }
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
    if (myOwner != null) {
      myOwner.removeHierarchyListener(myHierarchyListener);
    }
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
    if (myOwner != null) {
      myOwner.addHierarchyListener(myHierarchyListener);
    }
  }

  private boolean isStatusVisibleInner() {
    if (!isStatusVisible()) return false;
    if (!myInLoadingPanel) return true;
    JBLoadingPanel loadingPanel = UIUtil.getParentOfType(JBLoadingPanel.class, myOwner);
    return loadingPanel == null || !loadingPanel.isLoading();
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
    if (!myHasActiveClickListeners || !isStatusVisibleInner()) return null;

    point = SwingUtilities.convertPoint(myMouseTarget, point, myOwner);

    Rectangle commonBounds = getTextComponentBound();
    if (commonBounds.contains(point)) {
      for (int columnId = 0; columnId < columns.size(); columnId++) {
        ActionListener listener = getListener(columnId, point, commonBounds);
        if (listener != null) {
          return listener;
        }
      }
    }
    return null;
  }

  private @Nullable ActionListener getListener(int columnId, Point point, Rectangle commonBounds) {
    Point primaryLocation = getColumnLocation(columnId, commonBounds);
    var column = columns.get(columnId);
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
    for (var column : columns) {
      column.fragments.clear();
    }

    myHasActiveClickListeners = false;
    repaintOwner();
    return this;
  }

  private void repaintOwner() {
    if (myOwner != null && myOwner.isShowing() && isStatusVisibleInner()) myOwner.repaint();
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
    return appendText(0, Math.max(0, columns.get(0).fragments.size() - 1), null, text, attrs, listener);
  }

  public StatusText appendText(int columnIndex,
                               int rowIndex,
                               @Nullable Icon icon,
                               @NlsContexts.StatusText String text,
                               @NotNull SimpleTextAttributes attrs,
                               @Nullable ActionListener listener) {
    Fragment fragment = getOrCreateFragment(columnIndex, rowIndex);
    fragment.myComponent.setIcon(icon);
    fragment.myComponent.append(text, attrs);
    fragment.myClickListeners.add(listener);
    myHasActiveClickListeners |= listener != null;
    updateBounds();
    repaintOwner();
    return this;
  }

  public StatusText appendText(int columnIndex,
                               int rowIndex,
                               @NlsContexts.StatusText String text,
                               @NotNull SimpleTextAttributes attrs,
                               @Nullable ActionListener listener) {
    return appendText(columnIndex, rowIndex, null, text, attrs, listener);
  }

  private void updateBounds() {
    for (var column : columns) {
      updateBounds(column);
    }
  }

  private void updateBounds(Column column) {
    Dimension size = new Dimension();
    for (int i = 0; i < column.fragments.size(); i++) {
      Fragment fragment = column.fragments.get(i);
      Dimension d = fragment.myComponent.getPreferredSize();
      fragment.boundsInColumn.setBounds(0, size.height, d.width, d.height);
      size.height += d.height;
      if (i != column.fragments.size() - 1 || forceGapAfterLastLine) size.height += JBUIScale.scale(fragment.gapAfter);
      size.width = Math.max(size.width, d.width);
    }
    if (myCenterAlignText) {
      for (int i = 0; i < column.fragments.size(); i++) {
        Fragment fragment = column.fragments.get(i);
        fragment.boundsInColumn.x += (size.width - fragment.boundsInColumn.width) / 2;
      }
    }
    column.preferredSize.setSize(size);
  }

  public Iterable<JComponent> getWrappedFragmentsIterable() {
    return new Iterable<>() {
      @Override
      public @NotNull Iterator<JComponent> iterator() {
        return columns.stream()
          .flatMap(column -> column.fragments.stream())
          .<JComponent>map(fragment -> fragment.myComponent)
          .toList()
          .iterator();
      }
    };
  }

  private Fragment getOrCreateFragment(int columnIndex, int rowIndex) {
    if (columns.size() < columnIndex) {
      throw new IllegalStateException("Cannot add text to column " + columnIndex +
                                      " as there are only " + columns.size() + " columns available");
    }
    if (columns.size() == columnIndex) {
      var column = new Column();
      column.fragments.add(new Fragment(yGap));
      columns.add(column);
      return column.fragments.get(0);
    }
    var column = columns.get(columnIndex);
    if (column.fragments.size() < rowIndex) {
      throw new IllegalStateException("Cannot add text to rowIndex " + rowIndex +
                                      " as in column " + columnIndex +
                                      " there are " + column.fragments.size() + " rows only");
    }
    Fragment fragment;
    if (column.fragments.size() == rowIndex) {
      fragment = new Fragment(yGap);
      if (myFont != null) {
        fragment.myComponent.setFont(myFont);
      }
      column.fragments.add(fragment);
    }
    else {
      fragment = column.fragments.get(rowIndex);
    }
    return fragment;
  }

  public @NotNull StatusText appendSecondaryText(@NotNull @NlsContexts.StatusText String text,
                                                 @NotNull SimpleTextAttributes attrs,
                                                 @Nullable ActionListener listener) {
    return appendText(0, 1, null, text, attrs, listener);
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
    return appendText(0, columns.get(0).fragments.size(), icon, text, attrs, listener);
  }

  /**
   * Sets the gap between the lines of text.
   * <p>
   * Affects all lines added <em>after</em> invocation of this method.
   * Supposed to be used like this:
   * <pre>{@code
   * emptyText.withUnscaledGapAfter(5).appendLine("Some text");
   * }</pre>
   * Note that the value set persists until changed, so if the gap is needed for just one line,
   * it has to be updated again after appending that line.
   * For example:
   * <pre>{@code
   * emptyText.withUnscaledGapAfter(5).appendLine("Some text").withUnscaledGapAfter(2);
   * }</pre>
   * </p>
   * <p>
   * If this method is never called, the gap between lines will be 2 px.
   * </p>
   *
   * @param gap the gap between lines, specified in pixels before applying any scaling factors
   * @return {@code this} instance
   */
  public @NotNull StatusText withUnscaledGapAfter(int gap) {
    this.yGap = gap;
    return this;
  }

  /**
   * Forces the vertical gap to appear even after the last line.
   *
   * @return {@code this} instance
   * @deprecated exists only to emulate an old bug because there's a test that relies on that bug
   */
  @ApiStatus.Internal
  @Deprecated(forRemoval = true)
  public @NotNull StatusText forceGapAfterLastLine() {
    this.forceGapAfterLastLine = true;
    return this;
  }

  public void paint(Component owner, Graphics g) {
    if (!isStatusVisibleInner()) {
      return;
    }
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

  private Point getColumnLocation(int columnIndex, Rectangle bounds) {
    var column = columns.get(columnIndex);
    if (columns.size() == 1) { // center
      return new Point(bounds.x + (bounds.width - column.preferredSize.width) / 2, bounds.y);
    }
    else if (columnIndex == 0) { // stick to the left
      return new Point(bounds.x, bounds.y);
    }
    else if (columnIndex == columns.size() - 1) { // stick to the right
      return new Point(bounds.x + bounds.width - column.preferredSize.width, bounds.y);
    }
    else { // calc appropriate location according to free space and sum of columns' width
      var allColumnsWidth = columns.stream().mapToInt(it -> it.preferredSize.width).sum();
      var gap = (bounds.width - allColumnsWidth) / (columns.size() - 1);
      var prevColumnsWidth = columns.stream().limit(columnIndex).mapToInt(it -> it.preferredSize.width).sum();
      return new Point(bounds.x + prevColumnsWidth + columnIndex * gap, bounds.y);
    }
  }

  private void doPaintStatusText(@NotNull Graphics g, @NotNull Rectangle bounds) {
    for (int columnId = 0; columnId < columns.size(); columnId++) {
      paintColumnInBounds(columns.get(columnId), g, getColumnLocation(columnId, bounds), bounds);
    }
  }

  /**
   * This was once valuable, but by the current moment, allegedly,
   * only Math.min parts (here) make some sense. It's suggested to remove
   * this method and e.g., allow override
   * {@link #getFragmentBounds(Column, Point, Rectangle, Fragment)} instead.
   */
  protected @NotNull Rectangle adjustComponentBounds(@NotNull JComponent component, @NotNull Rectangle bounds) {
    Dimension size = component.getPreferredSize();
    int width = Math.min(size.width, bounds.width);
    int height = Math.min(size.height, bounds.height);

    if (columns.size() == 1) {
      return new Rectangle(bounds.x + (bounds.width - width) / 2, bounds.y, width, height);
    }
    else {
      return component == getComponent()
             ? new Rectangle(bounds.x, bounds.y, width, height)
             : new Rectangle(bounds.x + bounds.width - width, bounds.y, width, height);
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
    return getOrCreateFragment(0, 0).myComponent;
  }

  public @NotNull SimpleColoredComponent getSecondaryComponent() {
    return getOrCreateFragment(0, 1).myComponent;
  }

  public Dimension getPreferredSize() {
    var allColumnsWidth = columns.stream().mapToInt(it -> it.preferredSize.width).sum();
    var maxColumnHeight = columns.stream().mapToInt(it -> it.preferredSize.height).max().orElse(0);
    return new Dimension(allColumnsWidth, maxColumnHeight);
  }

  public static @NlsContexts.StatusText String getDefaultEmptyText() {
    return UIBundle.message("message.nothingToShow");
  }

  @Override
  public String toString() {
    StringBuilder text = new StringBuilder();
    for (var column : columns) {
      for (var fragment : column.fragments) {
        if (!text.isEmpty()) text.append("\n");
        text.append(fragment.myComponent);
      }
    }
    return text.toString();
  }

  /**
   * @deprecated There are no primary and secondary columns anymore, they're 0 and 1.
   * Use {@link #appendText(int, int, String, SimpleTextAttributes, ActionListener)} instead.
   */
  @Deprecated(forRemoval = true)
  public StatusText appendText(boolean isPrimaryColumn,
                               int row,
                               @NlsContexts.StatusText String text,
                               SimpleTextAttributes attrs,
                               ActionListener listener) {
    return appendText(isPrimaryColumn ? 0 : 1, row, null, text, attrs, listener);
  }

  /**
   * @deprecated There are no primary and secondary columns anymore, they're 0 and 1.
   * Use {@link #appendText(int, int, Icon, String, SimpleTextAttributes, ActionListener)} instead.
   */
  @Deprecated(forRemoval = true)
  public StatusText appendText(boolean isPrimaryColumn,
                               int row,
                               @Nullable Icon icon,
                               @NlsContexts.StatusText String text,
                               SimpleTextAttributes attrs,
                               ActionListener listener) {
    return appendText(isPrimaryColumn ? 0 : 1, row, icon, text, attrs, listener);
  }
}
