// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Weighted;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.ui.ClickListener;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.UIBundle;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.EventDispatcher;
import com.intellij.util.MathUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;

import static com.intellij.util.ui.FocusUtil.*;

/**
 * @author Vladimir Kondratyev
 */
public class ThreeComponentsSplitter extends JPanel implements Disposable {
  private static final Icon SplitGlueV = EmptyIcon.create(17, 6);
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
  private boolean myVerticalSplit;
  private boolean myHonorMinimumSize = false;

  private final Divider myFirstDivider;
  private final Divider myLastDivider;
  private EventDispatcher<ComponentListener> myDividerDispatcher;

  @Nullable private JComponent myFirstComponent;
  @Nullable private JComponent myInnerComponent;
  @Nullable private JComponent myLastComponent;

  private int myFirstSize = 0;
  private int myLastSize = 0;
  private int myMinSize = 0;

  private boolean myShowDividerControls;
  private int myDividerZone;

  private final class MyFocusTraversalPolicy extends LayoutFocusTraversalPolicy {
    @Override
    @SuppressWarnings("Duplicates")
    public Component getComponentAfter(Container aContainer, Component aComponent) {
      Component comp;
      if (SwingUtilities.isDescendingFrom(aComponent, myFirstComponent)) {
        Component next = nextVisible(myFirstComponent);
        comp = (next != null) ? findChildToFocus(next) : aComponent;
      }
      else if (SwingUtilities.isDescendingFrom(aComponent, myInnerComponent)) {
        Component next = nextVisible(myInnerComponent);
        comp = (next != null) ? findChildToFocus(next) : aComponent;
      }
      else {
        Component next = nextVisible(myLastComponent);
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
      if (SwingUtilities.isDescendingFrom(aComponent, myInnerComponent)) {
        Component prev = prevVisible(myInnerComponent);
        comp = (prev != null) ? findChildToFocus(prev) : aComponent;
      }
      else if (SwingUtilities.isDescendingFrom(aComponent, myLastComponent)) {
        Component prev = prevVisible(myLastComponent);
        comp = (prev != null) ? findChildToFocus(prev) : aComponent;
      }
      else {
        Component prev = prevVisible(myFirstComponent);
        comp = (prev != null) ? findChildToFocus(prev) : aComponent;
      }
      if (comp == aComponent) {
        // if focus is stuck on the component let it go further
        return super.getComponentBefore(aContainer, aComponent);
      }
      return comp;
    }

    private Component nextVisible(Component comp) {
      if (comp == myFirstComponent) return innerVisible() ? myInnerComponent : lastVisible() ? myLastComponent : null;
      if (comp == myInnerComponent) return lastVisible() ? myLastComponent : firstVisible() ? myFirstComponent : null;
      if (comp == myLastComponent) return firstVisible() ? myFirstComponent : innerVisible() ? myInnerComponent : null;
      return null;
    }


    private Component prevVisible(Component comp) {
      if (comp == myFirstComponent) return lastVisible() ? myLastComponent : innerVisible() ? myInnerComponent : null;
      if (comp == myInnerComponent) return firstVisible() ? myFirstComponent : lastVisible() ? myLastComponent : null;
      if (comp == myLastComponent) return innerVisible() ? myInnerComponent : firstVisible() ? myFirstComponent : null;
      return null;
    }

    @Override
    public Component getFirstComponent(Container aContainer) {
      if (firstVisible()) return findChildToFocus(myFirstComponent);
      Component next = nextVisible(myFirstComponent);
      return next != null ? findChildToFocus(next) : null;
    }

    @Override
    public Component getLastComponent(Container aContainer) {
      if (lastVisible()) return findChildToFocus(myLastComponent);
      Component prev = prevVisible(myLastComponent);
      return prev != null ? findChildToFocus(prev) : null;
    }

    private boolean myReentrantLock = false;
    @Override
    public Component getDefaultComponent(Container aContainer) {
      if (myReentrantLock) return null;
      try {
        myReentrantLock = true;
        if (innerVisible()) return findChildToFocus(myInnerComponent);
        Component next = nextVisible(myLastComponent);
        return next != null ? findChildToFocus(next) : null;
      } finally {
        myReentrantLock = false;
      }
    }

    Component findChildToFocus (Component component) {
      final Window ancestor = SwingUtilities.getWindowAncestor(ThreeComponentsSplitter.this);
      // Step 1 : We should take into account cases with detached toolwindows and editors
      //       - find the recent focus owner for the window of the splitter and
      //         make sure that the most recently focused component is inside the
      //         passed component. By the way, the recent focused component is supposed to be focusable

      final Component mostRecentFocusOwner = getMostRecentComponent(component, ancestor);
      if (mostRecentFocusOwner != null) return mostRecentFocusOwner;

      // Step 2 : If the best candidate to focus is a panel, usually it does not
      //          have focus representation for showing the focused state
      //          Let's ask the focus traversal policy what is the best candidate

      Component defaultComponentInPanel = getDefaultComponentInPanel(component);
      if (defaultComponentInPanel != null) return defaultComponentInPanel;

      //Step 3 : Return the component, but find the first focusable component first

      return findFocusableComponentIn((JComponent)component, null);
    }
  }

  /**
   * Creates horizontal split with proportion equals to .5f
   */
  public ThreeComponentsSplitter(@NotNull Disposable parentDisposable) {
    this(false, parentDisposable);
  }

  /**
   * @deprecated Use {@link #ThreeComponentsSplitter(Disposable)}
   */
  @Deprecated
  public ThreeComponentsSplitter() {
    this(false, false, null, true);
  }

  public ThreeComponentsSplitter(boolean vertical, @NotNull Disposable parentDisposable) {
    this(vertical, false, parentDisposable);
  }

  /**
   * @deprecated Use {@link #ThreeComponentsSplitter(Disposable)}
   */
  @Deprecated
  public ThreeComponentsSplitter(boolean vertical) {
    this(vertical, false, null, true);
  }

  public ThreeComponentsSplitter(boolean vertical, boolean onePixelDividers, @NotNull Disposable parentDisposable) {
    this(vertical, onePixelDividers, parentDisposable, true);
  }

  /**
   * @deprecated Use {@link #ThreeComponentsSplitter(Disposable)}
   */
  @Deprecated
  public ThreeComponentsSplitter(boolean vertical, boolean onePixelDividers) {
    this(vertical, onePixelDividers, null, true);
  }

  private ThreeComponentsSplitter(boolean vertical, boolean onePixelDividers, @Nullable Disposable parentDisposable, @SuppressWarnings("unused") boolean __) {
    myVerticalSplit = vertical;
    myShowDividerControls = false;
    myFirstDivider = new Divider(true, onePixelDividers, parentDisposable == null ? this : parentDisposable);
    myLastDivider = new Divider(false, onePixelDividers, parentDisposable == null ? this : parentDisposable);

    myDividerWidth = onePixelDividers ? 1 : 7;
    if (onePixelDividers) {
      Color bg = UIUtil.CONTRAST_BORDER_COLOR;
      myFirstDivider.setBackground(bg);
      myLastDivider.setBackground(bg);
    }
    setFocusCycleRoot(true);
    setFocusTraversalPolicy(new MyFocusTraversalPolicy());
    setOpaque(false);
    add(myFirstDivider);
    add(myLastDivider);
  }

  public void setShowDividerControls(boolean showDividerControls) {
    myShowDividerControls = showDividerControls;
    setOrientation(myVerticalSplit);
  }

  public void setDividerMouseZoneSize(int size) {
    myDividerZone = JBUIScale.scale(size);
  }

  public boolean isHonorMinimumSize() {
    return myHonorMinimumSize;
  }

  public void setHonorComponentsMinimumSize(boolean honorMinimumSize) {
    myHonorMinimumSize = honorMinimumSize;
  }

  @Override
  public boolean isVisible() {
    return super.isVisible() && (firstVisible() || innerVisible() || lastVisible());
  }

  private boolean lastVisible() {
    return !Splitter.isNull(myLastComponent) && myLastComponent.isVisible();
  }

  private boolean innerVisible() {
    return !Splitter.isNull(myInnerComponent) && myInnerComponent.isVisible();
  }

  private boolean firstVisible() {
    return !Splitter.isNull(myFirstComponent) && myFirstComponent.isVisible();
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
      final Dimension firstSize = myFirstComponent != null ? myFirstComponent.getMinimumSize() : JBUI.emptySize();
      final Dimension lastSize = myLastComponent != null ? myLastComponent.getMinimumSize() : JBUI.emptySize();
      final Dimension innerSize = myInnerComponent != null ? myInnerComponent.getMinimumSize() : JBUI.emptySize();
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
          firstComponentSize -= sizeLack * firstSizeRatio;
          firstComponentSize = Math.max(myMinSize, firstComponentSize);
        }
        if (lastComponentSize > 0) {
          lastComponentSize -= sizeLack * (1 - firstSizeRatio);
          lastComponentSize = Math.max(myMinSize, lastComponentSize);
        }
        innerComponentSize = getMinSize(myInnerComponent);
      }
      else {
        innerComponentSize = Math.max(getMinSize(myInnerComponent), componentSize - dividersCount * dividerWidth - getFirstSize() - getLastSize());
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

    myFirstDivider.setVisible(firstDividerVisible());
    myFirstDivider.setBounds(firstDividerRect);
    myFirstDivider.doLayout();

    myLastDivider.setVisible(lastDividerVisible());
    myLastDivider.setBounds(lastDividerRect);
    myLastDivider.doLayout();

    validateIfNeeded(myFirstComponent, firstRect);
    validateIfNeeded(myInnerComponent, innerRect);
    validateIfNeeded(myLastComponent, lastRect);
  }

  private static void validateIfNeeded(final JComponent c, final Rectangle rect) {
    if (!Splitter.isNull(c)) {
      if (!c.getBounds().equals(rect)) {
        c.setBounds(rect);
        c.revalidate();
      }
    } else {
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
    return myVerticalSplit;
  }

  /**
   * @param verticalSplit {@code true} means that splitter will have vertical split
   */
  public void setOrientation(boolean verticalSplit) {
    myVerticalSplit = verticalSplit;
    myFirstDivider.setOrientation(verticalSplit);
    myLastDivider.setOrientation(verticalSplit);
    doLayout();
    repaint();
  }

  @Nullable
  public JComponent getFirstComponent() {
    return myFirstComponent;
  }

  /**
   * Sets component which is located as the "first" split area. The method doesn't validate and
   * repaint the splitter if there is one already.
   *
   */
  public void setFirstComponent(@Nullable JComponent component) {
    if (myFirstComponent == component) {
      return;
    }

    if (myFirstComponent != null) {
      remove(myFirstComponent);
    }
    myFirstComponent = component;
    doAddComponent(component);
  }

  @Nullable
  public JComponent getLastComponent() {
    return myLastComponent;
  }

  /**
   * Sets component which is located as the "second" split area. The method doesn't validate and
   * repaint the splitter.
   */
  public void setLastComponent(@Nullable JComponent component) {
    if (myLastComponent == component) {
      return;
    }

    if (myLastComponent != null) {
      remove(myLastComponent);
    }
    myLastComponent = component;
    doAddComponent(component);
  }

  @Nullable
  public JComponent getInnerComponent() {
    return myInnerComponent;
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
    if (myInnerComponent == component) {
      return;
    }

    if (myInnerComponent != null) {
      remove(myInnerComponent);
    }
    myInnerComponent = component;
    doAddComponent(component);
  }

  private void doAddComponent(@Nullable JComponent component) {
    if (component != null) {
      updateComponentTreeUI(component);
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
    return getMinSize(first? myFirstComponent : myLastComponent);
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
    if (myDividerDispatcher == null) {
      myDividerDispatcher = EventDispatcher.create(ComponentListener.class);
    }
    myDividerDispatcher.addListener(listener);
  }

  private final class Divider extends JPanel {
    private final boolean myIsOnePixel;
    private boolean myDragging;
    private Point myPoint;
    private final boolean myIsFirst;

    private IdeGlassPane myGlassPane;
    private Disposable myGlassPaneDisposable;

    private class MyMouseAdapter extends MouseAdapter implements Weighted {
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
        MouseEvent event = getTargetEvent(e);
        processMouseMotionEvent(event);
        if (event.isConsumed()) {
          e.consume();
        }
      }

      private void _processMouseEvent(MouseEvent e) {
        MouseEvent event = getTargetEvent(e);
        processMouseEvent(event);
        if (event.isConsumed()) {
          e.consume();
        }
      }
    }

    private final MouseAdapter myListener = new MyMouseAdapter();

    private MouseEvent getTargetEvent(@NotNull MouseEvent e) {
      return SwingUtilities.convertMouseEvent(e.getComponent(), e, this);
    }

    private boolean myWasPressedOnMe;

    Divider(boolean isFirst, boolean isOnePixel, @NotNull Disposable parentDisposable) {
      super(new GridBagLayout());
      myIsOnePixel = isOnePixel;
      setFocusable(false);
      enableEvents(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
      myIsFirst = isFirst;
      setOrientation(myVerticalSplit);

      Disposer.register(parentDisposable, new UiNotifyConnector(this, new Activatable() {
        @Override
        public void showNotify() {
          initGlassPane(parentDisposable);
        }

        @Override
        public void hideNotify() {
          releaseGlassPane();
        }
      }));
    }

    private boolean isInside(Point p) {
      if (!isVisible()) return false;
      Window window = ComponentUtil.getWindow(this);
      if (window != null) {
        Point point = SwingUtilities.convertPoint(this, p, window);
        Component component = UIUtil.getDeepestComponentAt(window, point.x, point.y);
        List<Component> components = Arrays.asList(myFirstComponent, myFirstDivider, myInnerComponent, myLastDivider, myLastComponent);
        if (ComponentUtil.findParentByCondition(component, c -> c != null && components.contains(c)) == null) return false;
      }

      int dndOff = myIsOnePixel ? JBUIScale.scale(Registry.intValue("ide.splitter.mouseZone")) / 2 : 0;
      if (myVerticalSplit) {
        if (p.x >= 0 && p.x < getWidth()) {
          if (getHeight() > 0) {
            return p.y >= -dndOff && p.y < getHeight() + dndOff;
          }
          else {
            return p.y >= -myDividerZone / 2 && p.y <= myDividerZone / 2;
          }
        }
      }
      else {
        if (p.y >= 0 && p.y < getHeight()) {
          if (getWidth() > 0) {
            return p.x >= -dndOff && p.x < getWidth() + dndOff;
          }
          else {
            return p.x >= -myDividerZone / 2 && p.x <= myDividerZone / 2;
          }
        }
      }

      return false;
    }

    private void initGlassPane(@NotNull Disposable parentDisposable) {
      IdeGlassPane glassPane = IdeGlassPaneUtil.find(this);
      if (glassPane == myGlassPane) {
        return;
      }
      releaseGlassPane();
      myGlassPane = glassPane;
      myGlassPaneDisposable = Disposer.newDisposable();
      Disposer.register(parentDisposable, myGlassPaneDisposable);
      myGlassPane.addMouseMotionPreprocessor(myListener, myGlassPaneDisposable);
      myGlassPane.addMousePreprocessor(myListener, myGlassPaneDisposable);
    }

    private void releaseGlassPane() {
      if (myGlassPaneDisposable != null) {
        Disposer.dispose(myGlassPaneDisposable);
        myGlassPaneDisposable = null;
        myGlassPane = null;
      }
    }

    private void setOrientation(boolean isVerticalSplit) {
      removeAll();

      if (!myShowDividerControls) {
        return;
      }

      int xMask = isVerticalSplit ? 1 : 0;
      int yMask = isVerticalSplit ? 0 : 1;

      Icon glueIcon = isVerticalSplit ? SplitGlueV : AllIcons.General.ArrowSplitCenterH;
      int glueFill = isVerticalSplit ? GridBagConstraints.VERTICAL : GridBagConstraints.HORIZONTAL;
      add(new JLabel(glueIcon),
          new GridBagConstraints(0, 0, 1, 1, 0, 0, isVerticalSplit ? GridBagConstraints.EAST : GridBagConstraints.NORTH, glueFill, new Insets(0, 0, 0, 0), 0, 0));
      JLabel splitDownlabel = new JLabel(isVerticalSplit ? AllIcons.General.ArrowDown : AllIcons.General.ArrowRight);
      splitDownlabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      splitDownlabel.setToolTipText(isVerticalSplit ? UIBundle.message("splitter.down.tooltip.text") : UIBundle
        .message("splitter.right.tooltip.text"));
      new ClickListener() {
        @Override
        public boolean onClick(@NotNull MouseEvent e, int clickCount) {
          if (myInnerComponent != null) {
            final int income = myVerticalSplit ? myInnerComponent.getHeight() : myInnerComponent.getWidth();
            if (myIsFirst) {
              setFirstSize(myFirstSize + income);
            }
            else {
              setLastSize(myLastSize + income);
            }
          }
          return true;
        }
      }.installOn(splitDownlabel);

      add(splitDownlabel,
          new GridBagConstraints(isVerticalSplit ? 1 : 0,
                                 isVerticalSplit ? 0 : 5,
                                 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
      //
      add(new JLabel(glueIcon),
          new GridBagConstraints(2 * xMask, 2 * yMask, 1, 1, 0, 0, GridBagConstraints.CENTER, glueFill, new Insets(0, 0, 0, 0), 0, 0));
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
          new GridBagConstraints(3 * xMask, 3 * yMask, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
      add(new JLabel(glueIcon),
          new GridBagConstraints(4 * xMask, 4 * yMask, 1, 1, 0, 0, GridBagConstraints.CENTER, glueFill, new Insets(0, 0, 0, 0), 0, 0));
      //
      JLabel splitUpLabel = new JLabel(isVerticalSplit ? AllIcons.General.ArrowUp : AllIcons.General.ArrowLeft);
      splitUpLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      splitUpLabel.setToolTipText(isVerticalSplit ? UIBundle.message("splitter.up.tooltip.text") : UIBundle
        .message("splitter.left.tooltip.text"));
      new ClickListener() {
        @Override
        public boolean onClick(@NotNull MouseEvent e, int clickCount) {
          if (myInnerComponent != null) {
            final int income = myVerticalSplit ? myInnerComponent.getHeight() : myInnerComponent.getWidth();
            if (myIsFirst) {
              setFirstSize(myFirstSize + income);
            }
            else {
              setLastSize(myLastSize + income);
            }
          }
          return true;
        }
      }.installOn(splitUpLabel);

      add(splitUpLabel,
          new GridBagConstraints(isVerticalSplit ? 5 : 0,
                                 isVerticalSplit ? 0 : 1,
                                 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
      add(new JLabel(glueIcon),
          new GridBagConstraints(6 * xMask, 6 * yMask, 1, 1, 0, 0,
                                 isVerticalSplit ? GridBagConstraints.WEST : GridBagConstraints.SOUTH, glueFill, new Insets(0, 0, 0, 0), 0, 0));
    }

    private void center() {
      if (myInnerComponent != null) {
        final int total = myFirstSize + (myVerticalSplit ? myInnerComponent.getHeight() : myInnerComponent.getWidth());
        if (myIsFirst) {
          setFirstSize(total / 2);
        }
        else {
          setLastSize(total / 2);
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

        if (myGlassPane != null) {
          myGlassPane.setCursor(getResizeCursor(), myListener);
        }

        myPoint = SwingUtilities.convertPoint(this, e.getPoint(), ThreeComponentsSplitter.this);
        final int size = getOrientation() ? ThreeComponentsSplitter.this.getHeight() : ThreeComponentsSplitter.this.getWidth();
        if (getOrientation()) {
          if (size > 0 || myDividerZone > 0) {
            if (myIsFirst) {
              setFirstSize(MathUtil.clamp(myPoint.y, getMinSize(myFirstComponent),
                                          size - myLastSize - getMinSize(myInnerComponent) - getDividerWidth() * visibleDividersCount()));
            }
            else {
              setLastSize(MathUtil.clamp(size - myPoint.y - getDividerWidth(), getMinSize(myLastComponent), 
                                         size - myFirstSize - getMinSize(myInnerComponent) - getDividerWidth() * visibleDividersCount()));
            }
          }
        }
        else {
          if (size > 0 || myDividerZone > 0) {
            if (myIsFirst) {
              setFirstSize(MathUtil.clamp(myPoint.x, getMinSize(myFirstComponent), 
                                          size - myLastSize - getMinSize(myInnerComponent) - getDividerWidth() * visibleDividersCount()));
            }
            else {
              setLastSize(MathUtil.clamp(size - myPoint.x - getDividerWidth(), getMinSize(myLastComponent), 
                                         size - myFirstSize - getMinSize(myInnerComponent) - getDividerWidth() * visibleDividersCount()));
            }
          }
        }
        ThreeComponentsSplitter.this.doLayout();
      }
      else if (MouseEvent.MOUSE_MOVED == e.getID()) {
        if (myGlassPane != null) {
          if (isInside(e.getPoint())) {
            myGlassPane.setCursor(getResizeCursor(), myListener);
            e.consume();
          }
          else {
            myGlassPane.setCursor(null, myListener);
          }
        }
      }

      if (myWasPressedOnMe) {
        e.consume();
      }
    }

    @Override
    protected void processMouseEvent(MouseEvent e) {
      super.processMouseEvent(e);
      if (!isShowing()) {
        return;
      }
      switch (e.getID()) {
        case MouseEvent.MOUSE_ENTERED:
          setCursor(getResizeCursor());
          break;
        case MouseEvent.MOUSE_EXITED:
          if (!myDragging) {
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
          }
          break;
        case MouseEvent.MOUSE_PRESSED:
          if (isInside(e.getPoint())) {
            myWasPressedOnMe = true;
            if (myGlassPane != null) {
              myGlassPane.setCursor(getResizeCursor(), myListener);
            }
            e.consume();
          } else {
            myWasPressedOnMe = false;
          }
          break;
        case MouseEvent.MOUSE_RELEASED:
          if (myWasPressedOnMe) {
            e.consume();
          }
          if (isInside(e.getPoint()) && myGlassPane != null) {
            myGlassPane.setCursor(getResizeCursor(), myListener);
          }
          if (myDragging && myDividerDispatcher != null) {
            myDividerDispatcher.getMulticaster().componentResized(new ComponentEvent(this, ComponentEvent.COMPONENT_RESIZED));
          }
          myWasPressedOnMe = false;
          myDragging = false;
          myPoint = null;
          break;
        case MouseEvent.MOUSE_CLICKED:
          if (e.getClickCount() == 2) {
            center();
          }
          break;
      }
    }
    private Cursor getResizeCursor() {
      return getOrientation()
             ? Cursor.getPredefinedCursor(myIsFirst ? Cursor.S_RESIZE_CURSOR : Cursor.N_RESIZE_CURSOR)
             : Cursor.getPredefinedCursor(myIsFirst ? Cursor.W_RESIZE_CURSOR : Cursor.E_RESIZE_CURSOR);
    }
  }

  // backward compatibility
  @Override
  public void dispose() {
  }
}