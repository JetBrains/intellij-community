// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui;

import static com.intellij.util.ui.FocusUtil.findFocusableComponentIn;
import static com.intellij.util.ui.FocusUtil.getDefaultComponentInPanel;
import static com.intellij.util.ui.FocusUtil.getMostRecentComponent;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Weighted;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.ui.ClickListener;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.UIBundle;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.EventDispatcher;
import com.intellij.util.MathUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.LayoutFocusTraversalPolicy;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ThreeComponentsSplitter extends JPanel implements Disposable {
  private static final Icon SplitGlueV = EmptyIcon.create(17, 6);

  private boolean isLookAndFeelUpdated = false;

  private int myDividerWidth;
  /**
   *                        /------/
   *                        |  1   |
   * This is vertical split |------|
   *                        |  2   |
   *                        /------/
   *
   *                          /-------/
   *                          |   |   |
   * This is horizontal split | 1 | 2 |
   *                          |   |   |
   *                          /-------/
   */
  private boolean verticalSplit;
  private boolean honorMinimumSize = false;

  private final Divider firstDivider;
  private final Divider lastDivider;
  private EventDispatcher<ComponentListener> dividerDispatcher;

  @Nullable private JComponent firstComponent;
  @Nullable private JComponent innerComponent;
  @Nullable private JComponent lastComponent;

  private int myFirstSize = 0;
  private int myLastSize = 0;
  private int myMinSize = 0;

  private boolean showDividerControls;
  private int dividerZone;

  private final class MyFocusTraversalPolicy extends LayoutFocusTraversalPolicy {
    @Override
    @SuppressWarnings("Duplicates")
    public Component getComponentAfter(Container aContainer, Component aComponent) {
      Component comp;
      if (SwingUtilities.isDescendingFrom(aComponent, firstComponent)) {
        Component next = nextVisible(firstComponent);
        comp = (next != null) ? findChildToFocus(next) : aComponent;
      }
      else if (SwingUtilities.isDescendingFrom(aComponent, innerComponent)) {
        Component next = nextVisible(innerComponent);
        comp = (next != null) ? findChildToFocus(next) : aComponent;
      }
      else {
        Component next = nextVisible(lastComponent);
        comp = (next != null) ? findChildToFocus(next) : aComponent;
      }
      if (comp == aComponent) {
        // if focus is stuck on the component let it go further
        return super.getComponentAfter(aContainer, aComponent);
      }
      return comp;
    }

    @Override
    @SuppressWarnings("Duplicates")
    public Component getComponentBefore(Container aContainer, Component aComponent) {
      Component comp;
      if (SwingUtilities.isDescendingFrom(aComponent, innerComponent)) {
        Component prev = prevVisible(innerComponent);
        comp = (prev != null) ? findChildToFocus(prev) : aComponent;
      }
      else if (SwingUtilities.isDescendingFrom(aComponent, lastComponent)) {
        Component prev = prevVisible(lastComponent);
        comp = (prev != null) ? findChildToFocus(prev) : aComponent;
      }
      else {
        Component prev = prevVisible(firstComponent);
        comp = (prev != null) ? findChildToFocus(prev) : aComponent;
      }
      if (comp == aComponent) {
        // if focus is stuck on the component let it go further
        return super.getComponentBefore(aContainer, aComponent);
      }
      return comp;
    }

    private Component nextVisible(Component comp) {
      if (comp == firstComponent) return innerVisible() ? innerComponent : lastVisible() ? lastComponent : null;
      if (comp == innerComponent) return lastVisible() ? lastComponent : firstVisible() ? firstComponent : null;
      if (comp == lastComponent) return firstVisible() ? firstComponent : innerVisible() ? innerComponent : null;
      return null;
    }

    private Component prevVisible(Component comp) {
      if (comp == firstComponent) return lastVisible() ? lastComponent : innerVisible() ? innerComponent : null;
      if (comp == innerComponent) return firstVisible() ? firstComponent : lastVisible() ? lastComponent : null;
      if (comp == lastComponent) return innerVisible() ? innerComponent : firstVisible() ? firstComponent : null;
      return null;
    }

    @Override
    public Component getFirstComponent(Container aContainer) {
      if (firstVisible()) return findChildToFocus(firstComponent);
      Component next = nextVisible(firstComponent);
      return next != null ? findChildToFocus(next) : null;
    }

    @Override
    public Component getLastComponent(Container aContainer) {
      if (lastVisible()) return findChildToFocus(lastComponent);
      Component prev = prevVisible(lastComponent);
      return prev != null ? findChildToFocus(prev) : null;
    }

    private boolean myReentrantLock = false;
    @Override
    public Component getDefaultComponent(Container aContainer) {
      if (myReentrantLock) return null;
      try {
        myReentrantLock = true;
        if (innerVisible()) return findChildToFocus(innerComponent);
        Component next = nextVisible(lastComponent);
        return next != null ? findChildToFocus(next) : null;
      }
      finally {
        myReentrantLock = false;
      }
    }

    Component findChildToFocus (Component component) {
      Window ancestor = SwingUtilities.getWindowAncestor(ThreeComponentsSplitter.this);
      // Step 1 : We should take into account cases with detached toolwindows and editors
      //       - find the recent focus owner for the window of the splitter and
      //         make sure that the most recently focused component is inside the
      //         passed component. By the way, the recent focused component is supposed to be focusable

      Component mostRecentFocusOwner = getMostRecentComponent(component, ancestor);
      if (mostRecentFocusOwner != null) {
        return mostRecentFocusOwner;
      }

      // Step 2 : If the best candidate to focus is a panel, usually it does not
      //          have focus representation for showing the focused state
      //          Let's ask the focus traversal policy what is the best candidate

      Component defaultComponentInPanel = getDefaultComponentInPanel(component);
      if (defaultComponentInPanel != null) {
        return defaultComponentInPanel;
      }

      //Step 3 : Return the component, but find the first focusable component first
      return findFocusableComponentIn(component, null);
    }
  }

  /**
   * Creates horizontal split with proportion equals to .5f
   */
  public ThreeComponentsSplitter() {
    this(false);
  }

  /**
   * @deprecated Use {@link #ThreeComponentsSplitter()}
   */
  @Deprecated
  public ThreeComponentsSplitter(@SuppressWarnings("unused") @NotNull Disposable parentDisposable) {
    this(false);
  }

  public ThreeComponentsSplitter(boolean vertical) {
    this(vertical, false);
  }

  public ThreeComponentsSplitter(boolean vertical, boolean onePixelDividers) {
    verticalSplit = vertical;
    showDividerControls = false;
    firstDivider = new Divider(this, true, onePixelDividers);
    lastDivider = new Divider(this, false, onePixelDividers);
    myDividerWidth = onePixelDividers ? 1 : 7;

    setFocusCycleRoot(true);
    setFocusTraversalPolicy(new MyFocusTraversalPolicy());
    setOpaque(false);
    add(firstDivider);
    add(lastDivider);
  }

  @Override
  public void updateUI() {
    super.updateUI();

    // if null, it means that `updateUI` is called as a part of init
    if (firstDivider != null) {
      isLookAndFeelUpdated = true;
    }
  }

  public void setShowDividerControls(boolean showDividerControls) {
    this.showDividerControls = showDividerControls;
    setOrientation(verticalSplit);
  }

  public void setDividerMouseZoneSize(int size) {
    dividerZone = JBUIScale.scale(size);
  }

  public boolean isHonorMinimumSize() {
    return honorMinimumSize;
  }

  public void setHonorComponentsMinimumSize(boolean honorMinimumSize) {
    this.honorMinimumSize = honorMinimumSize;
  }

  @Override
  public boolean isVisible() {
    return super.isVisible() && (firstVisible() || innerVisible() || lastVisible());
  }

  protected boolean lastVisible() {
    return !Splitter.isNull(lastComponent) && lastComponent.isVisible();
  }

  private boolean innerVisible() {
    return !Splitter.isNull(innerComponent) && innerComponent.isVisible();
  }

  protected boolean firstVisible() {
    return !Splitter.isNull(firstComponent) && firstComponent.isVisible();
  }

  private int visibleDividersCount() {
    int count = 0;
    if (firstDividerVisible()) count++;
    if (lastDividerVisible()) count++;
    return count;
  }

  private boolean firstDividerVisible() {
    return firstVisible() && innerVisible() || firstVisible() && lastVisible() && !innerVisible();
  }

  private boolean lastDividerVisible() {
    return innerVisible() && lastVisible();
  }

  @Override
  public Dimension getMinimumSize() {
    if (isHonorMinimumSize()) {
      final int dividerWidth = getDividerWidth();
      final Dimension firstSize = firstComponent != null ? firstComponent.getMinimumSize() : JBUI.emptySize();
      final Dimension lastSize = lastComponent != null ? lastComponent.getMinimumSize() : JBUI.emptySize();
      final Dimension innerSize = innerComponent != null ? innerComponent.getMinimumSize() : JBUI.emptySize();
      if (getOrientation()) {
        int width = Math.max(firstSize.width, Math.max(lastSize.width, innerSize.width));
        int height = visibleDividersCount() * dividerWidth;
        height += firstSize.height;
        height += lastSize.height;
        height += innerSize.height;
        return new Dimension(width, height);
      }
      else {
        int height = Math.max(firstSize.height, Math.max(lastSize.height, innerSize.height));
        int width = visibleDividersCount() * dividerWidth;
        width += firstSize.width;
        width += lastSize.width;
        width += innerSize.width;
        return new Dimension(width, height);
      }
    }
    return super.getMinimumSize();
  }

  @Override
  public void doLayout() {
    final int width = getWidth();
    final int height = getHeight();

    Rectangle firstRect = new Rectangle();
    Rectangle firstDividerRect = new Rectangle();
    Rectangle lastDividerRect = new Rectangle();
    Rectangle lastRect = new Rectangle();
    Rectangle innerRect = new Rectangle();
    final int componentSize = getOrientation() ? height : width;
    int dividerWidth = getDividerWidth();
    int dividersCount = visibleDividersCount();

    int firstComponentSize;
    int lastComponentSize;
    int innerComponentSize;
    if(componentSize <= dividersCount * dividerWidth) {
      firstComponentSize = 0;
      lastComponentSize = 0;
      innerComponentSize = 0;
      dividerWidth = componentSize;
    }
    else {
      firstComponentSize = getFirstSize();
      lastComponentSize = getLastSize();
      int sizeLack = firstComponentSize + lastComponentSize - (componentSize - dividersCount * dividerWidth - myMinSize);
      if (sizeLack > 0) {
        // Lacking size. Reduce first & last component's size, inner -> MIN_SIZE
        double firstSizeRatio = (double)firstComponentSize / (firstComponentSize + lastComponentSize);
        if (firstComponentSize > 0) {
          firstComponentSize -= (int)(sizeLack * firstSizeRatio);
          firstComponentSize = Math.max(myMinSize, firstComponentSize);
        }
        if (lastComponentSize > 0) {
          lastComponentSize -= (int)(sizeLack * (1 - firstSizeRatio));
          lastComponentSize = Math.max(myMinSize, lastComponentSize);
        }
        innerComponentSize = getMinSize(innerComponent);
      }
      else {
        innerComponentSize = Math.max(getMinSize(innerComponent), componentSize - dividersCount * dividerWidth - getFirstSize() - getLastSize());
      }

      if (!innerVisible()) {
        lastComponentSize += innerComponentSize;
        innerComponentSize = 0;
        if (!lastVisible()) {
          firstComponentSize = componentSize;
        }
      }
    }

    int space = firstComponentSize;
    if (getOrientation()) {
      firstRect.setBounds(0, 0, width, firstComponentSize);
      if (firstDividerVisible()) {
        firstDividerRect.setBounds(0, space, width, dividerWidth);
        space += dividerWidth;
      }

      innerRect.setBounds(0, space, width, innerComponentSize);
      space += innerComponentSize;

      if (lastDividerVisible()) {
        lastDividerRect.setBounds(0, space, width, dividerWidth);
        space += dividerWidth;
      }

      lastRect.setBounds(0, space, width, lastComponentSize);
    }
    else {
      firstRect.setBounds(0, 0, firstComponentSize, height);

      if (firstDividerVisible()) {
        firstDividerRect.setBounds(space, 0, dividerWidth, height);
        space += dividerWidth;
      }

      innerRect.setBounds(space, 0, innerComponentSize, height);
      space += innerComponentSize;

      if (lastDividerVisible()) {
        lastDividerRect.setBounds(space, 0, dividerWidth, height);
        space += dividerWidth;
      }

      lastRect.setBounds(space, 0, lastComponentSize, height);
    }

    firstDivider.setVisible(firstDividerVisible());
    firstDivider.setBounds(firstDividerRect);
    firstDivider.doLayout();

    lastDivider.setVisible(lastDividerVisible());
    lastDivider.setBounds(lastDividerRect);
    lastDivider.doLayout();

    validateIfNeeded(firstComponent, firstRect);
    validateIfNeeded(innerComponent, innerRect);
    validateIfNeeded(lastComponent, lastRect);
  }

  private static void validateIfNeeded(final JComponent c, final Rectangle rect) {
    if (!Splitter.isNull(c)) {
      if (!c.getBounds().equals(rect)) {
        c.setBounds(rect);
        c.revalidate();
      }
    }
    else {
      Splitter.hideNull(c);
    }
  }


  public int getDividerWidth() {
    return myDividerWidth;
  }

  public void setDividerWidth(int width) {
    if (width < 0) {
      throw new IllegalArgumentException("Wrong divider width: " + width);
    }
    if (myDividerWidth != width) {
      myDividerWidth = width;
      doLayout();
      repaint();
    }
  }

  /**
   * @return {@code true} if splitter has vertical orientation, {@code false} otherwise
   */
  public boolean getOrientation() {
    return verticalSplit;
  }

  /**
   * @param verticalSplit {@code true} means that splitter will have vertical split
   */
  public void setOrientation(boolean verticalSplit) {
    this.verticalSplit = verticalSplit;
    firstDivider.setOrientation(verticalSplit);
    lastDivider.setOrientation(verticalSplit);
    doLayout();
    repaint();
  }

  @Nullable
  public JComponent getFirstComponent() {
    return firstComponent;
  }

  /**
   * Sets component which is located as the "first" split area. The method doesn't validate and
   * repaint the splitter if there is one already.
   *
   */
  public void setFirstComponent(@Nullable JComponent component) {
    if (firstComponent == component) {
      return;
    }

    if (firstComponent != null) {
      remove(firstComponent);
    }
    firstComponent = component;
    doAddComponent(component);
  }

  @Nullable
  public JComponent getLastComponent() {
    return lastComponent;
  }

  /**
   * Sets component which is located as the "second" split area. The method doesn't validate and
   * repaint the splitter.
   */
  public void setLastComponent(@Nullable JComponent component) {
    if (lastComponent == component) {
      return;
    }

    if (lastComponent != null) {
      remove(lastComponent);
    }
    lastComponent = component;
    doAddComponent(component);
  }

  @Nullable
  public JComponent getInnerComponent() {
    return innerComponent;
  }

  private static void updateComponentTreeUI(@Nullable JComponent rootComponent) {
    for (Component component : UIUtil.uiTraverser(rootComponent).postOrderDfsTraversal()) {
      if (component instanceof JComponent) {
        ((JComponent)component).updateUI();
      }
    }
  }

  /**
   * Sets component which is located as the "inner" splitted area. The method doesn't validate and
   * repaint the splitter.
   */
  public void setInnerComponent(@Nullable JComponent component) {
    if (innerComponent == component) {
      return;
    }

    if (innerComponent != null) {
      remove(innerComponent);
    }
    innerComponent = component;
    doAddComponent(component);
  }

  private void doAddComponent(@Nullable JComponent component) {
    if (component != null) {
      if (isLookAndFeelUpdated) {
        updateComponentTreeUI(component);
        isLookAndFeelUpdated = false;
      }
      add(component);
      component.invalidate();
    }
  }

  public void setMinSize(int minSize) {
    myMinSize = Math.max(0, minSize);
    doLayout();
    repaint();
  }


  public void setFirstSize(final int size) {
    int oldSize = myFirstSize;
    myFirstSize = Math.max(getMinSize(true), size);
    if (firstVisible() && oldSize != myFirstSize) {
      doLayout();
      repaint();
    }
  }

  public void setLastSize(final int size) {
    int oldSize = myLastSize;
    myLastSize = Math.max(getMinSize(false), size);
    if (lastVisible() && oldSize != myLastSize) {
      doLayout();
      repaint();
    }
  }

  public int getFirstSize() {
    return firstVisible() ? myFirstSize : 0;
  }

  public int getLastSize() {
    return lastVisible() ? myLastSize : 0;
  }

  public int getMinSize(boolean first) {
    return getMinSize(first ? firstComponent : lastComponent);
  }

  public int getMaxSize(boolean first) {
    final int size = getOrientation() ? this.getHeight() : this.getWidth();
    return size - (first? myLastSize: myFirstSize) - myMinSize;
  }

  private int getMinSize(JComponent component) {
    if (isHonorMinimumSize() && component != null && component.isVisible()) {
      return getOrientation() ? component.getMinimumSize().height : component.getMinimumSize().width;
    }
    return myMinSize;
  }

  public void addDividerResizeListener(@NotNull ComponentListener listener) {
    if (dividerDispatcher == null) {
      dividerDispatcher = EventDispatcher.create(ComponentListener.class);
    }
    dividerDispatcher.addListener(listener);
  }

  private static class Divider extends JPanel {
    private final boolean myIsOnePixel;
    private final ThreeComponentsSplitter splitter;
    private boolean myDragging;
    private Point myPoint;
    private final boolean myIsFirst;

    private IdeGlassPane glassPane;
    private Disposable glassPaneDisposable;

    private final MouseAdapter listener = new MyMouseAdapter(this);

    private MouseEvent getTargetEvent(@NotNull MouseEvent e) {
      return SwingUtilities.convertMouseEvent(e.getComponent(), e, this);
    }

    private boolean myWasPressedOnMe;

    Divider(final ThreeComponentsSplitter splitter, boolean isFirst, boolean isOnePixel) {
      super(new GridBagLayout());
      myIsOnePixel = isOnePixel;
      setFocusable(false);
      enableEvents(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
      myIsFirst = isFirst;
      setOrientation(splitter.verticalSplit);
      this.splitter = splitter;
    }

    @Override
    public void addNotify() {
      super.addNotify();

      if (ScreenUtil.isStandardAddRemoveNotify(this)) {
        initGlassPane();
      }
    }

    @Override
    public void removeNotify() {
      super.removeNotify();

      if (ScreenUtil.isStandardAddRemoveNotify(this)) {
        releaseGlassPane();
      }
    }

    @Override
    public Color getBackground() {
      return myIsOnePixel ? UIUtil.CONTRAST_BORDER_COLOR : super.getBackground();
    }

    private boolean isInside(Point p) {
      if (!isVisible()) return false;
      Window window = ComponentUtil.getWindow(this);
      if (window != null) {
        Point point = SwingUtilities.convertPoint(this, p, window);
        Component component = UIUtil.getDeepestComponentAt(window, point.x, point.y);
        List<Component> components = Arrays.asList(splitter.firstComponent, splitter.firstDivider, splitter.innerComponent,
                                                   splitter.lastDivider, splitter.lastComponent);
        if (ComponentUtil.findParentByCondition(component, c -> c != null && components.contains(c)) == null) {
          return false;
        }
      }

      int dndOff = myIsOnePixel ? JBUIScale.scale(Registry.intValue("ide.splitter.mouseZone")) / 2 : 0;
      if (splitter.verticalSplit) {
        if (p.x >= 0 && p.x < getWidth()) {
          if (getHeight() > 0) {
            return p.y >= -dndOff && p.y < getHeight() + dndOff;
          }
          else {
            return p.y >= -splitter.dividerZone / 2 && p.y <= splitter.dividerZone / 2;
          }
        }
      }
      else {
        if (p.y >= 0 && p.y < getHeight()) {
          if (getWidth() > 0) {
            return p.x >= -dndOff && p.x < getWidth() + dndOff;
          }
          else {
            return p.x >= -splitter.dividerZone / 2 && p.x <= splitter.dividerZone / 2;
          }
        }
      }

      return false;
    }

    private void initGlassPane() {
      IdeGlassPane glassPane = IdeGlassPaneUtil.find(this);
      if (glassPane == this.glassPane) {
        return;
      }

      releaseGlassPane();

      this.glassPane = glassPane;
      glassPaneDisposable = Disposer.newDisposable();
      glassPane.addMouseMotionPreprocessor(listener, glassPaneDisposable);
      glassPane.addMousePreprocessor(listener, glassPaneDisposable);
    }

    private void releaseGlassPane() {
      if (glassPaneDisposable != null) {
        Disposer.dispose(glassPaneDisposable);
        glassPaneDisposable = null;
        glassPane = null;
      }
    }

    private void setOrientation(boolean isVerticalSplit) {
      removeAll();

      if (!splitter.showDividerControls) {
        return;
      }

      int xMask = isVerticalSplit ? 1 : 0;
      int yMask = isVerticalSplit ? 0 : 1;

      Icon glueIcon = isVerticalSplit ? SplitGlueV : AllIcons.General.ArrowSplitCenterH;
      int glueFill = isVerticalSplit ? GridBagConstraints.VERTICAL : GridBagConstraints.HORIZONTAL;
      add(new JLabel(glueIcon),
          new GridBagConstraints(0, 0, 1, 1, 0, 0, isVerticalSplit ? GridBagConstraints.EAST : GridBagConstraints.NORTH, glueFill, JBInsets.emptyInsets(), 0, 0));
      JLabel splitDownlabel = new JLabel(isVerticalSplit ? AllIcons.General.ArrowDown : AllIcons.General.ArrowRight);
      splitDownlabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      splitDownlabel.setToolTipText(isVerticalSplit ? UIBundle.message("splitter.down.tooltip.text") : UIBundle
        .message("splitter.right.tooltip.text"));
      new ClickListener() {
        @Override
        public boolean onClick(@NotNull MouseEvent e, int clickCount) {
          if (splitter.innerComponent != null) {
            final int income = splitter.verticalSplit ? splitter.innerComponent.getHeight() : splitter.innerComponent.getWidth();
            if (myIsFirst) {
              splitter.setFirstSize(splitter.myFirstSize + income);
            }
            else {
              splitter.setLastSize(splitter.myLastSize + income);
            }
          }
          return true;
        }
      }.installOn(splitDownlabel);

      add(splitDownlabel,
          new GridBagConstraints(isVerticalSplit ? 1 : 0,
                                 isVerticalSplit ? 0 : 5,
                                 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE, JBInsets.emptyInsets(), 0, 0));
      //
      add(new JLabel(glueIcon),
          new GridBagConstraints(2 * xMask, 2 * yMask, 1, 1, 0, 0, GridBagConstraints.CENTER, glueFill, JBInsets.emptyInsets(), 0, 0));
      JLabel splitCenterlabel = new JLabel(isVerticalSplit ? AllIcons.General.ArrowSplitCenterV : AllIcons.General.ArrowSplitCenterH);
      splitCenterlabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      splitCenterlabel.setToolTipText(UIBundle.message("splitter.center.tooltip.text"));
      new ClickListener() {
        @Override
        public boolean onClick(@NotNull MouseEvent e, int clickCount) {
          center();
          return true;
        }
      }.installOn(splitCenterlabel);
      add(splitCenterlabel,
          new GridBagConstraints(3 * xMask, 3 * yMask, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE, JBInsets.emptyInsets(), 0, 0));
      add(new JLabel(glueIcon),
          new GridBagConstraints(4 * xMask, 4 * yMask, 1, 1, 0, 0, GridBagConstraints.CENTER, glueFill, JBInsets.emptyInsets(), 0, 0));
      //
      JLabel splitUpLabel = new JLabel(isVerticalSplit ? AllIcons.General.ArrowUp : AllIcons.General.ArrowLeft);
      splitUpLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      splitUpLabel.setToolTipText(isVerticalSplit ? UIBundle.message("splitter.up.tooltip.text") : UIBundle
        .message("splitter.left.tooltip.text"));
      new ClickListener() {
        @Override
        public boolean onClick(@NotNull MouseEvent e, int clickCount) {
          if (splitter.innerComponent != null) {
            final int income = splitter.verticalSplit ? splitter.innerComponent.getHeight() : splitter.innerComponent.getWidth();
            if (myIsFirst) {
              splitter.setFirstSize(splitter.myFirstSize + income);
            }
            else {
              splitter.setLastSize(splitter.myLastSize + income);
            }
          }
          return true;
        }
      }.installOn(splitUpLabel);

      add(splitUpLabel,
          new GridBagConstraints(isVerticalSplit ? 5 : 0,
                                 isVerticalSplit ? 0 : 1,
                                 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE, JBInsets.emptyInsets(), 0, 0));
      add(new JLabel(glueIcon),
          new GridBagConstraints(6 * xMask, 6 * yMask, 1, 1, 0, 0,
                                 isVerticalSplit ? GridBagConstraints.WEST : GridBagConstraints.SOUTH, glueFill, JBInsets.emptyInsets(), 0, 0));
    }

    private void center() {
      if (splitter.innerComponent != null) {
        final int total = splitter.myFirstSize + (splitter.verticalSplit
                                                  ? splitter.innerComponent.getHeight() : splitter.innerComponent.getWidth());
        if (myIsFirst) {
          splitter.setFirstSize(total / 2);
        }
        else {
          splitter.setLastSize(total / 2);
        }
      }
    }

    @Override
    protected void processMouseMotionEvent(MouseEvent e) {
      super.processMouseMotionEvent(e);

      if (!isShowing()) {
        return;
      }

      if (MouseEvent.MOUSE_DRAGGED == e.getID() && myWasPressedOnMe) {
        myDragging = true;
        setCursor(getResizeCursor());

        if (glassPane != null) {
          glassPane.setCursor(getResizeCursor(), listener);
        }

        myPoint = SwingUtilities.convertPoint(this, e.getPoint(), splitter);
        final int size = splitter.getOrientation() ? splitter.getHeight() : splitter.getWidth();
        if (splitter.getOrientation()) {
          if (size > 0 || splitter.dividerZone > 0) {
            if (myIsFirst) {
              splitter.setFirstSize(clamp(myPoint.y, size, splitter.firstComponent, splitter.getLastSize()));
            }
            else {
              splitter.setLastSize(clamp(size - myPoint.y - splitter.getDividerWidth(), size, splitter.lastComponent, splitter.getFirstSize()));
            }
          }
        }
        else {
          if (size > 0 || splitter.dividerZone > 0) {
            if (myIsFirst) {
              splitter.setFirstSize(clamp(myPoint.x, size, splitter.firstComponent, splitter.getLastSize()));
            }
            else {
              splitter.setLastSize(clamp(size - myPoint.x - splitter.getDividerWidth(), size, splitter.lastComponent, splitter.getFirstSize()));
            }
          }
        }
        splitter.doLayout();
      }
      else if (MouseEvent.MOUSE_MOVED == e.getID()) {
        if (glassPane != null) {
          if (isInside(e.getPoint())) {
            glassPane.setCursor(getResizeCursor(), listener);
            e.consume();
          }
          else {
            glassPane.setCursor(null, listener);
          }
        }
      }

      if (myWasPressedOnMe) {
        e.consume();
      }
    }

    private int clamp(int pos, int size, JComponent component, int componentSize) {
      int minSize = splitter.getMinSize(component);
      int maxSize = size - componentSize - splitter.getMinSize(splitter.innerComponent) - splitter.getDividerWidth() * splitter.visibleDividersCount();
      return minSize <= maxSize ? MathUtil.clamp(pos, minSize, maxSize) : pos;
    }

    @Override
    protected void processMouseEvent(MouseEvent e) {
      super.processMouseEvent(e);
      if (!isShowing()) {
        return;
      }
      switch (e.getID()) {
        case MouseEvent.MOUSE_ENTERED -> setCursor(getResizeCursor());
        case MouseEvent.MOUSE_EXITED -> {
          if (!myDragging) {
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
          }
        }
        case MouseEvent.MOUSE_PRESSED -> {
          if (isInside(e.getPoint())) {
            myWasPressedOnMe = true;
            if (glassPane != null) {
              glassPane.setCursor(getResizeCursor(), listener);
            }
            e.consume();
          }
          else {
            myWasPressedOnMe = false;
          }
        }
        case MouseEvent.MOUSE_RELEASED -> {
          if (myWasPressedOnMe) {
            e.consume();
          }
          if (isInside(e.getPoint()) && glassPane != null) {
            glassPane.setCursor(getResizeCursor(), listener);
          }
          if (myDragging && splitter.dividerDispatcher != null) {
            splitter.dividerDispatcher.getMulticaster().componentResized(new ComponentEvent(this, ComponentEvent.COMPONENT_RESIZED));
          }
          myWasPressedOnMe = false;
          myDragging = false;
          myPoint = null;
        }
        case MouseEvent.MOUSE_CLICKED -> {
          if (e.getClickCount() == 2) {
            center();
          }
        }
      }
    }
    private Cursor getResizeCursor() {
      return splitter.getOrientation()
             ? Cursor.getPredefinedCursor(myIsFirst ? Cursor.S_RESIZE_CURSOR : Cursor.N_RESIZE_CURSOR)
             : Cursor.getPredefinedCursor(myIsFirst ? Cursor.W_RESIZE_CURSOR : Cursor.E_RESIZE_CURSOR);
    }
  }

  private static final class MyMouseAdapter extends MouseAdapter implements Weighted {
    private final Divider divider;

    MyMouseAdapter(Divider divider) {
      this.divider = divider;
    }

    @Override
    public void mousePressed(MouseEvent e) {
      _processMouseEvent(e);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      _processMouseEvent(e);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      _processMouseMotionEvent(e);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
      _processMouseMotionEvent(e);
    }

    @Override
    public double getWeight() {
      return 1;
    }

    private void _processMouseMotionEvent(MouseEvent e) {
      MouseEvent event = divider.getTargetEvent(e);
      divider.processMouseMotionEvent(event);
      if (event.isConsumed()) {
        e.consume();
      }
    }

    private void _processMouseEvent(MouseEvent e) {
      MouseEvent event = divider.getTargetEvent(e);
      divider.processMouseEvent(event);
      if (event.isConsumed()) {
        e.consume();
      }
    }
  }

  // backward compatibility
  @Override
  public void dispose() {
  }
}