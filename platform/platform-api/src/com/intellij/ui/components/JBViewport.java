/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ui.components;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.ui.TypingTarget;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.components.JBScrollPane.Alignment;
import com.intellij.ui.table.JBTable;
import com.intellij.util.MethodInvocator;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.plaf.TreeUI;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicTreeUI;
import java.awt.*;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;

import static com.intellij.util.ui.JBUI.emptyInsets;

public class JBViewport extends JViewport implements ZoomableViewport {
  private static final MethodInvocator ourCanUseWindowBlitterMethod = new MethodInvocator(JViewport.class, "canUseWindowBlitter");
  private static final MethodInvocator ourGetPaintManagerMethod = new MethodInvocator(RepaintManager.class, "getPaintManager");
  private static final MethodInvocator ourGetUseTrueDoubleBufferingMethod = new MethodInvocator(JRootPane.class, "getUseTrueDoubleBuffering");

  private static final NotificationGroup NOTIFICATION_GROUP = NotificationGroup.logOnlyGroup("scrolling-capabilities-debug");
  private static final int NOTIFICATION_TIMEOUT = 1500;

  private Notification myPreviousNotification;

  private static final ViewportLayout ourLayoutManager = new ViewportLayout() {

    @Override
    public void layoutContainer(Container parent) {
      if (parent instanceof JViewport) {
        JViewport viewport = (JViewport)parent;
        Component view = viewport.getView();
        if (view != null) {
          Container grand = viewport.getParent();
          if (grand instanceof JScrollPane) {
            doLayout((JScrollPane)grand, viewport, view);
          }
          else {
            super.layoutContainer(parent);
          }
        }
      }
    }
  };

  private StatusText myEmptyText;
  private boolean myPaintingNow;

  private ZoomingDelegate myZoomer;

  private volatile boolean myBackgroundRequested; // avoid cyclic references

  public JBViewport() {
    addContainerListener(new ContainerListener() {
      @Override
      public void componentAdded(ContainerEvent e) {
        Component child = e.getChild();
        if (child instanceof JBTable) {
          myEmptyText = ((ComponentWithEmptyText)child).getEmptyText();
          myEmptyText.attachTo(JBViewport.this, child);
        }
      }

      @Override
      public void componentRemoved(ContainerEvent e) {
        Component child = e.getChild();
        if (child instanceof JBTable) {
          ((ComponentWithEmptyText)child).getEmptyText().attachTo(child);
          myEmptyText = null;
        }
      }
    });
  }

  @Override
  public void setViewPosition(Point p) {
    if (ScrollSettings.isDebugEnabled() && !p.equals(getViewPosition()) && !isInsideLogToolWindow()) {
      checkScrollingCapabilities();
    }
    super.setViewPosition(p);
  }

  // A heuristic to detect whether this viewport belongs to the "Event Log" tool window (which we use for output)
  private boolean isInsideLogToolWindow() {
    Container parent1 = getParent();
    if (parent1 instanceof JScrollPane) {
      Container parent2 = parent1.getParent();
      if (parent2 instanceof JPanel) {
        Container parent3 = parent2.getParent();
        if (parent3 instanceof JPanel) {
          return parent3.getClass().getName().startsWith("com.intellij.notification.EventLogToolWindowFactory");
        }
      }
    }

    return false;
  }

  // Checks whether blit-accelerated scrolling is feasible, and if so, checks whether true double buffering is available.
  private void checkScrollingCapabilities() {
    if (myPreviousNotification == null || myPreviousNotification.isExpired()) {
      if (!Boolean.TRUE.equals(isWindowBlitterAvailableFor(this))) {
        myPreviousNotification = notify("Scrolling: cannot use window blitter");
      }
      else {
        if (!Boolean.TRUE.equals(isTrueDoubleBufferingAvailableFor(this))) {
          myPreviousNotification = notify("Scrolling: cannot use true double buffering");
        }
      }
    }
  }

  /* Blit-acceleration copies as much of the rendered area as possible and then repaints only newly exposed region.
     This helps to improve scrolling performance and to reduce CPU usage (especially if drawing is compute-intensive).

     Generally, this requires that viewport must not be obscured by its ancestors and must be showing. */
  @Nullable
  private static Boolean isWindowBlitterAvailableFor(JViewport viewport) {
    if (ourCanUseWindowBlitterMethod.isAvailable()) {
      return (Boolean)ourCanUseWindowBlitterMethod.invoke(viewport);
    }

    return null;
  }

  @Override
  public void setView(Component view) {
    super.setView(view);
    updateBorder(view);
  }

  /* True double buffering is needed to eliminate tearing on blit-accelerated scrolling and to restore
     frame buffer content without the usual repainting, even when the EDT is blocked.

     Generally, this requires default RepaintManager, swing.bufferPerWindow = true and
     no prior direct invocations of JComponent.getGraphics() within JRootPane.

     Use a breakpoint in JRootPane.disableTrueDoubleBuffering() to detect direct getGraphics() calls.

     See GraphicsUtil.safelyGetGraphics() for more info. */
  @Nullable
  private static Boolean isTrueDoubleBufferingAvailableFor(JComponent component) {
    if (ourGetPaintManagerMethod.isAvailable()) {
      Object paintManager = ourGetPaintManagerMethod.invoke(RepaintManager.currentManager(component));

      if (!"javax.swing.BufferStrategyPaintManager".equals(paintManager.getClass().getName())) {
        return false;
      }

      if (ourGetUseTrueDoubleBufferingMethod.isAvailable()) {
        JRootPane rootPane = component.getRootPane();

        if (rootPane != null) {
          return (Boolean)ourGetUseTrueDoubleBufferingMethod.invoke(rootPane);
        }
      }
    }

    return null;
  }

  private static Notification notify(String message) {
    Notification notification = NOTIFICATION_GROUP.createNotification(message, NotificationType.INFORMATION);
    notification.notify(null);

    Timer timer = new Timer(NOTIFICATION_TIMEOUT, event -> notification.expire());
    timer.setRepeats(false);
    timer.start();

    return notification;
  }

  @Override
  public Color getBackground() {
    Color color = super.getBackground();
    if (!myBackgroundRequested && EventQueue.isDispatchThread() && ScrollSettings.isBackgroundFromView()) {
      if (!isBackgroundSet() || color instanceof UIResource) {
        Component child = getView();
        if (child != null) {
          try {
            myBackgroundRequested = true;
            return child.getBackground();
          }
          finally {
            myBackgroundRequested = false;
          }
        }
      }
    }
    return color;
  }

  @Override
  protected LayoutManager createLayoutManager() {
    return ourLayoutManager;
  }

  @Override
  protected Graphics getComponentGraphics(Graphics graphics) {
    return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics));
  }

  @Override
  public void paint(Graphics g) {
    myPaintingNow = true;
    if (myZoomer != null && myZoomer.isActive()) {
      myZoomer.paint(g);
    }
    else {
      super.paint(g);

      if (myEmptyText != null) {
        myEmptyText.paint(this, g);
      }
    }
    myPaintingNow = false;
  }

  @Nullable
  @Override
  public Magnificator getMagnificator() {
    return UIUtil.getClientProperty(getView(), Magnificator.CLIENT_PROPERTY_KEY);
  }

  @Override
  public void magnificationStarted(Point at) {
    myZoomer = new ZoomingDelegate((JComponent)getView(), this);
    myZoomer.magnificationStarted(at);
  }

  @Override
  public void magnificationFinished(double magnification) {
    myZoomer.magnificationFinished(magnification);
    myZoomer = null;
  }

  @Override
  public void magnify(double magnification) {
    myZoomer.magnify(magnification);
  }

  public boolean isPaintingNow() {
    return myPaintingNow;
  }

  @Override
  public void scrollRectToVisible(Rectangle bounds) {
    Component view = getView();
    if (view instanceof JComponent && !isAutoscroll(bounds)) {
      JBInsets.addTo(bounds, getViewInsets((JComponent)view));
    }
    super.scrollRectToVisible(bounds);
  }

  /**
   * @param bounds a bounds passed to {@link #scrollRectToVisible}
   * @return {@code true} if the specified bounds requested by auto-scrolling
   */
  private boolean isAutoscroll(Rectangle bounds) {
    if (bounds.x == -bounds.width || bounds.x == getWidth()) {
      if (bounds.y + bounds.height + bounds.y == getHeight()) {
        // Horizontal auto-scrolling:
        //          /---   or   ---\
        //          y              y
        //  /-width-!              !-width-\
        //  !       !              !       !
        //  h       !              !       h
        //  e       !              !       e
        //  i       !              !       i
        //  g       !              !       g
        //  h       !              !       h
        //  t       !              !       t
        //  !       !              !       !
        //  \-------!              !-------/
        //          y              y
        //          \---   or   ---/
        return true;
      }
    }
    if (bounds.y == -bounds.height || bounds.y == getHeight()) {
      if (bounds.x + bounds.width + bounds.x == getWidth()) {
        // Vertical auto-scrolling is symmetric to horizontal one
        return true;
      }
    }
    return false;
  }

  private static boolean isAlignmentNeeded(JComponent view, boolean horizontal) {
    return (!SystemInfo.isMac || horizontal && ScrollSettings.isHorizontalGapNeededOnMac()) &&
           (view instanceof JList || view instanceof JTree || (!SystemInfo.isMac && ScrollSettings.isGapNeededForAnyComponent()));
  }

  static Insets getViewInsets(JComponent view) {
    Border border = view.getBorder();
    if (border instanceof ViewBorder) {
      ViewBorder vb = (ViewBorder)border;
      Insets insets = emptyInsets();
      vb.addViewInsets(view, insets);
      return insets;
    }
    return null;
  }

  private static void doLayout(JScrollPane pane, JViewport viewport, Component view) {
    updateBorder(view);

    Dimension actualSize = viewport.getSize();
    Dimension extentSize = viewport.toViewCoordinates(actualSize);
    Dimension viewPreferredSize = view.getPreferredSize();
    Dimension viewSize = new Dimension(viewPreferredSize);
    Point viewPosition = viewport.getViewPosition();

    Scrollable scrollable = null;
    if (view instanceof Scrollable) {
      scrollable = (Scrollable)view;
      if (scrollable.getScrollableTracksViewportWidth()) viewSize.width = actualSize.width;
      if (scrollable.getScrollableTracksViewportHeight()) viewSize.height = actualSize.height;
    }
    // If the new viewport size would leave empty space to the right of the view,
    // right justify the view or left justify the view
    // when the width of the view is smaller than the container.
    int maxX = viewSize.width - extentSize.width;
    if (scrollable == null || pane.getComponentOrientation().isLeftToRight()) {
      if (viewPosition.x > maxX) {
        viewPosition.x = Math.max(0, maxX);
      }
    }
    else {
      viewPosition.x = maxX < 0 ? maxX : Math.max(0, Math.min(maxX, viewPosition.x));
    }
    // If the new viewport size would leave empty space below the view,
    // bottom justify the view or top justify the view
    // when the height of the view is smaller than the container.
    int maxY = viewSize.height - extentSize.height;
    if (viewPosition.y > maxY) {
      viewPosition.y = Math.max(0, maxY);
    }
    // If we haven't been advised about how the viewports size should change wrt to the viewport,
    // i.e. if the view isn't an instance of Scrollable, then adjust the views size as follows.
    if (scrollable == null) {
      // If the origin of the view is showing and the viewport is bigger than the views preferred size,
      // then make the view the same size as the viewport.
      if (viewPosition.x == 0 && actualSize.width > viewPreferredSize.width) viewSize.width = actualSize.width;
      if (viewPosition.y == 0 && actualSize.height > viewPreferredSize.height) viewSize.height = actualSize.height;
    }
    // do not force viewport size on editor component, e.g. EditorTextField and LanguageConsole
    if (!(view instanceof TypingTarget)) {
      if (ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER == pane.getHorizontalScrollBarPolicy()) {
        viewPosition.x = 0;
        viewSize.width = extentSize.width;
      }
      if (ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER == pane.getVerticalScrollBarPolicy()) {
        viewPosition.y = 0;
        viewSize.height = extentSize.height;
      }
    }
    viewport.setViewPosition(viewPosition);
    viewport.setViewSize(viewSize);
  }

  private static void updateBorder(@Nullable Component view) {
    if (ScrollSettings.isNotSupportedYet(view)) return;
    if (view instanceof JComponent) {
      JComponent component = (JComponent)view;
      Border border = component.getBorder();
      if (border instanceof ViewBorder) return; // already set
      component.setBorder(border == null || border instanceof UIResource
                          ? new ResourceViewBorder(border)
                          : new ViewBorder(border));
    }
  }

  /**
   * This border is used to add additional space for a view
   * and can be changed on UI update.
   */
  private static class ResourceViewBorder extends ViewBorder implements UIResource {
    ResourceViewBorder(Border border) {
      super(border);
    }
  }

  /**
   * This border is used to add additional space for a view.
   */
  private static class ViewBorder extends AbstractBorder {
    private final Insets myInsets = emptyInsets();
    private final Border myBorder;

    ViewBorder(Border border) {
      myBorder = border;
    }

    @Override
    public Insets getBorderInsets(Component view, Insets insets) {
      if (insets == null) {
        insets = emptyInsets();
      }
      else {
        insets.set(0, 0, 0, 0);
      }
      if (myBorder != null) {
        Insets inner = myBorder.getBorderInsets(view);
        if (inner != null) insets.set(inner.top, inner.left, inner.bottom, inner.right);
      }
      if (view instanceof JComponent) {
        addViewInsets((JComponent)view, insets);
      }
      if (!myInsets.equals(insets)) {
        myInsets.set(insets.top, insets.left, insets.bottom, insets.right);
        if (view instanceof JComponent) {
          JComponent component = (JComponent)view;
          if (component instanceof JTree) {
            // invalidate cached preferred size
            JTree tree = (JTree)component;
            TreeUI ui = tree.getUI();
            if (ui instanceof BasicTreeUI) {
              BasicTreeUI basic = (BasicTreeUI)ui;
              basic.setLeftChildIndent(basic.getLeftChildIndent());
            }
          }
          component.revalidate();
        }
        else {
          view.invalidate();
          view.repaint();
        }
      }
      return insets;
    }

    @Override
    public void paintBorder(Component view, Graphics g, int x, int y, int width, int height) {
      if (myBorder != null) {
        // additional insets are used inside a custom border
        myBorder.paintBorder(view, g, x, y, width, height);
      }
    }

    private void addViewInsets(JComponent view, Insets insets) {
      if (this == view.getBorder()) {
        Container parent = view.getParent();
        if (parent instanceof JViewport) {
          JViewport viewport = (JViewport)parent;
          Container grand = viewport.getParent();
          if (grand instanceof JScrollPane) {
            JScrollPane pane = (JScrollPane)grand;
            // calculate empty border under vertical scroll bar
            JScrollBar vsb = pane.getVerticalScrollBar();
            if (vsb != null && vsb.isVisible()) {
              boolean opaque = vsb.isOpaque();
              if (viewport == pane.getColumnHeader()
                  ? (!opaque || ScrollSettings.isHeaderOverCorner(pane.getViewport()))
                  : (!opaque && viewport == pane.getViewport())) {
                Alignment va = Alignment.get(vsb);
                if (va == Alignment.LEFT) {
                  insets.left += vsb.getWidth();
                }
                else if (va == Alignment.RIGHT && (opaque || isAlignmentNeeded(view, false))) {
                  insets.right += vsb.getWidth();
                }
              }
            }
            // calculate empty border under horizontal scroll bar
            JScrollBar hsb = pane.getHorizontalScrollBar();
            if (hsb != null && hsb.isVisible()) {
              boolean opaque = hsb.isOpaque();
              if (viewport == pane.getRowHeader()
                  ? (!opaque || ScrollSettings.isHeaderOverCorner(pane.getViewport()))
                  : (!opaque && viewport == pane.getViewport())) {
                Alignment ha = Alignment.get(hsb);
                if (ha == Alignment.TOP) {
                  insets.top += hsb.getHeight();
                }
                else if (ha == Alignment.BOTTOM && (opaque || isAlignmentNeeded(view, true))) {
                  insets.bottom += hsb.getHeight();
                }
              }
            }
          }
        }
      }
    }
  }
}
