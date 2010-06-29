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
package com.intellij.openapi.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.ui.UIBundle;
import com.intellij.util.ui.update.LazyUiDisposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * @author Vladimir Kondratyev
 */
public class ThreeComponentsSplitter extends JPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.ui.ThreeComponentsSplitter");

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
   * This is horihontal split | 1 | 2 |
   *                          |   |   |
   *                          /-------/
   */
  private boolean myVerticalSplit;
  private boolean myHonorMinimumSize = false;

  private final Divider myFirstDivider;
  private final Divider myLastDivider;

  private JComponent myFirstComponent;
  private JComponent myInnerComponent;
  private JComponent myLastComponent;

  private int myFirstSize = 10;
  private int myLastSize = 10;

  private boolean myShowDividerControls;
  private int myDividerZone;


  /**
   * Creates horizontal split with proportion equals to .5f
   */
  public ThreeComponentsSplitter() {
    this(false);
  }


  public ThreeComponentsSplitter(boolean vertical) {
    myVerticalSplit = vertical;
    myShowDividerControls = false;
    myFirstDivider = new Divider(true);
    myLastDivider = new Divider(false);

    myDividerWidth = 7;
    setOpaque(false);
    super.add(myFirstDivider);
    super.add(myLastDivider);
  }

  public void setShowDividerControls(boolean showDividerControls) {
    myShowDividerControls = showDividerControls;
    setOrientation(myVerticalSplit);
  }

  public void setDividerMouseZoneSize(int size) {
    myDividerZone = size;
  }

  public boolean isHonorMinimumSize() {
    return myHonorMinimumSize;
  }

  public void setHonorComponentsMinimumSize(boolean honorMinimumSize) {
    myHonorMinimumSize = honorMinimumSize;
  }

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

  public Dimension getMinimumSize() {
    if (isHonorMinimumSize()) {
      final int dividerWidth = getDividerWidth();
      final Dimension firstSize = myFirstComponent != null ? myFirstComponent.getMinimumSize() : new Dimension(0, 0);
      final Dimension lastSize = myLastComponent != null ? myLastComponent.getMinimumSize() : new Dimension(0, 0);
      final Dimension innerSize = myInnerComponent != null ? myInnerComponent.getMinimumSize() : new Dimension(0, 0);
      if (getOrientation()) {
        int width = Math.max(firstSize.width, Math.max(lastSize.width, innerSize.width));
        int height = visibleDividersCount() * dividerWidth;
        height += firstSize.height;
        height += lastSize.height;
        height += innerSize.height;
        return new Dimension(width, height);
      }
      else {
        int heigth = Math.max(firstSize.height, Math.max(lastSize.height, innerSize.height));
        int width = visibleDividersCount() * dividerWidth;
        width += firstSize.width;
        width += lastSize.width;
        width += innerSize.width;
        return new Dimension(width, heigth);
      }
    }
    return super.getMinimumSize();
  }

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

    int firstCompontSize;
    int lastComponentSize;
    int innerComponentSize;
    if(componentSize <= dividersCount * dividerWidth) {
      firstCompontSize = 0;
      lastComponentSize = 0;
      innerComponentSize = 0;
      dividerWidth = componentSize;
    }
    else {
      firstCompontSize = getFirstSize();
      lastComponentSize = getLastSize();
      int sizeLack = (firstCompontSize + lastComponentSize) - (componentSize - dividersCount * dividerWidth);
      if (sizeLack > 0) {
        // Lacking size. Reduce first component's size, inner -> empty
        firstCompontSize -= sizeLack;
        innerComponentSize = 0;
      }
      else {
        innerComponentSize = componentSize - dividersCount * dividerWidth - getFirstSize() - getLastSize();
      }

      if (!innerVisible()) {
        lastComponentSize += innerComponentSize;
        innerComponentSize = 0;
        if (!lastVisible()) {
          firstCompontSize = componentSize;
        }
      }
    }

    if (getOrientation()) {
      int space = firstCompontSize;
      firstRect.setBounds(0, 0, width, firstCompontSize);
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
      int space = firstCompontSize;
      firstRect.setBounds(0, 0, firstCompontSize, height);

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
   * @return <code>true</code> if splitter has vertical orientation, <code>false</code> otherwise
   */
  public boolean getOrientation() {
    return myVerticalSplit;
  }

  /**
   * @param verticalSplit <code>true</code> means that splitter will have vertical split
   */
  public void setOrientation(boolean verticalSplit) {
    myVerticalSplit = verticalSplit;
    myFirstDivider.setOrientation(verticalSplit);
    myLastDivider.setOrientation(verticalSplit);
    doLayout();
    repaint();
  }

  public JComponent getFirstComponent() {
    return myFirstComponent;
  }

  /**
   * Sets component which is located as the "first" splitted area. The method doesn't validate and
   * repaint the splitter. If there is already
   *
   * @param component
   */
  public void setFirstComponent(JComponent component) {
    if (myFirstComponent != component) {
      if (myFirstComponent != null) {
        remove(myFirstComponent);
      }
      myFirstComponent = component;
      if (myFirstComponent != null) {
        super.add(myFirstComponent);
        myFirstComponent.invalidate();
      }
    }
  }

  public JComponent getLastComponent() {
    return myLastComponent;
  }


  /**
   * Sets component which is located as the "secont" splitted area. The method doesn't validate and
   * repaint the splitter.
   *
   * @param component
   */
  public void setLastComponent(JComponent component) {
    if (myLastComponent != component) {
      if (myLastComponent != null) {
        remove(myLastComponent);
      }
      myLastComponent = component;
      if (myLastComponent != null) {
        super.add(myLastComponent);
        myLastComponent.invalidate();
      }
    }
  }


  public JComponent getInnerComponent() {
    return myInnerComponent;
  }


  /**
   * Sets component which is located as the "inner" splitted area. The method doesn't validate and
   * repaint the splitter.
   *
   * @param component
   */
  public void setInnerComponent(JComponent component) {
    if (myInnerComponent != component) {
      if (myInnerComponent != null) {
        remove(myInnerComponent);
      }
      myInnerComponent = component;
      if (myInnerComponent != null) {
        super.add(myInnerComponent);
        myInnerComponent.invalidate();
      }
    }
  }

  public void setFirstSize(final int size) {
    myFirstSize = size;
    doLayout();
    repaint();
  }

  public void setLastSize(final int size) {
    myLastSize = size;
    doLayout();
    repaint();
  }

  public int getFirstSize() {
    return firstVisible() ? myFirstSize : 0;
  }

  public int getLastSize() {
    return lastVisible() ? myLastSize : 0;
  }

  protected class Divider extends JPanel implements Disposable {
    protected boolean myDragging;
    protected Point myPoint;
    private final boolean myIsFirst;

    private IdeGlassPane myGlassPane;

    private MouseAdapter myListener = new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        MouseEvent event = SwingUtilities.convertMouseEvent(e.getComponent(), e, Divider.this);
        processMouseEvent(event);
        if (event.isConsumed()) {
          e.consume();
        }
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        MouseEvent event = SwingUtilities.convertMouseEvent(e.getComponent(), e, Divider.this);
        processMouseEvent(event);
        if (event.isConsumed()) {
          e.consume();
        }
      }

      @Override
      public void mouseMoved(MouseEvent e) {
        MouseEvent event = SwingUtilities.convertMouseEvent(e.getComponent(), e, Divider.this);
        processMouseMotionEvent(event);
        if (event.isConsumed()) {
          e.consume();
        }
      }

      @Override
      public void mouseDragged(MouseEvent e) {
        MouseEvent event = SwingUtilities.convertMouseEvent(e.getComponent(), e, Divider.this);
        processMouseMotionEvent(event);
        if (event.isConsumed()) {
          e.consume();
        }
      }
    };
    private boolean myWasPressedOnMe;

    public Divider(boolean isFirst) {
      super(new GridBagLayout());
      setFocusable(false);
      enableEvents(MouseEvent.MOUSE_EVENT_MASK | MouseEvent.MOUSE_MOTION_EVENT_MASK);
      myIsFirst = isFirst;
      setOrientation(myVerticalSplit);

      new LazyUiDisposable<Divider>(null, this, this) {
        @Override
        protected void initialize(@NotNull Disposable parent, @NotNull Divider child, @Nullable Project project) {
          init();
        }
      };
    }

    private boolean isInside(Point p) {
      if (myVerticalSplit) {
        if (getHeight() > 0) {
          return p.y >= 0 && p.y < getHeight();
        } else {
          return p.y >= -myDividerZone / 2 && p.y < myDividerZone / 2;
        }
      } else {
        if (getWidth() > 0) {
          return p.x >= 0 && p.x < getWidth();
        } else {
          return p.x >= -myDividerZone / 2 && p.x < myDividerZone / 2;         
        }
      }
    }

    private void init() {
      myGlassPane = IdeGlassPaneUtil.find(this);
      myGlassPane.addMouseMotionPreprocessor(myListener, this);
      myGlassPane.addMousePreprocessor(myListener, this);
    }

    public void dispose() {
    }

    private void setOrientation(boolean isVerticalSplit) {
      removeAll();

      if (!myShowDividerControls) {
        return;
      }

      int xMask = isVerticalSplit ? 1 : 0;
      int yMask = isVerticalSplit ? 0 : 1;

      Icon glueIcon = IconLoader.getIcon(isVerticalSplit ? "/general/splitGlueV.png" : "/general/splitGlueH.png");
      int glueFill = isVerticalSplit ? GridBagConstraints.VERTICAL : GridBagConstraints.HORIZONTAL;
      add(new JLabel(glueIcon),
          new GridBagConstraints(0, 0, 1, 1, 0, 0, isVerticalSplit ? GridBagConstraints.EAST : GridBagConstraints.NORTH, glueFill, new Insets(0, 0, 0, 0), 0, 0));
      JLabel splitDownlabel = new JLabel(IconLoader.getIcon(isVerticalSplit ? "/general/splitDown.png" : "/general/splitRight.png"));
      splitDownlabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      splitDownlabel.setToolTipText(isVerticalSplit ? UIBundle.message("splitter.down.tooltip.text") : UIBundle
        .message("splitter.right.tooltip.text"));
      splitDownlabel.addMouseListener(new MouseAdapter() {
        public void mouseClicked(MouseEvent e) {
          if (myInnerComponent != null) {
            final int income = myVerticalSplit ? myInnerComponent.getHeight() : myInnerComponent.getWidth();
            if (myIsFirst) {
              setFirstSize(myFirstSize + income);
            }
            else {
              setLastSize(myLastSize + income);
            }
          }
        }
      });
      add(splitDownlabel,
          new GridBagConstraints(isVerticalSplit ? 1 : 0,
                                 isVerticalSplit ? 0 : 5,
                                 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
      //
      add(new JLabel(glueIcon),
          new GridBagConstraints(2 * xMask, 2 * yMask, 1, 1, 0, 0, GridBagConstraints.CENTER, glueFill, new Insets(0, 0, 0, 0), 0, 0));
      JLabel splitCenterlabel = new JLabel(IconLoader.getIcon(isVerticalSplit ? "/general/splitCenterV.png" : "/general/splitCenterH.png"));
      splitCenterlabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      splitCenterlabel.setToolTipText(UIBundle.message("splitter.center.tooltip.text"));
      splitCenterlabel.addMouseListener(new MouseAdapter() {
        public void mouseClicked(MouseEvent e) {
          center();
        }
      });
      add(splitCenterlabel,
          new GridBagConstraints(3 * xMask, 3 * yMask, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
      add(new JLabel(glueIcon),
          new GridBagConstraints(4 * xMask, 4 * yMask, 1, 1, 0, 0, GridBagConstraints.CENTER, glueFill, new Insets(0, 0, 0, 0), 0, 0));
      //
      JLabel splitUpLabel = new JLabel(IconLoader.getIcon(isVerticalSplit ? "/general/splitUp.png" : "/general/splitLeft.png"));
      splitUpLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      splitUpLabel.setToolTipText(isVerticalSplit ? UIBundle.message("splitter.up.tooltip.text") : UIBundle
        .message("splitter.left.tooltip.text"));
      splitUpLabel.addMouseListener(new MouseAdapter() {
        public void mouseClicked(MouseEvent e) {
          if (myInnerComponent != null) {
            if (myIsFirst) {
              setFirstSize(getMinSize(myFirstComponent));
            }
            else {
              setLastSize(getMinSize(myLastComponent));
            }
          }
        }
      });
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

    protected void processMouseMotionEvent(MouseEvent e) {
      super.processMouseMotionEvent(e);
      if (MouseEvent.MOUSE_DRAGGED == e.getID() && myWasPressedOnMe) {
        myDragging = true;
        setCursor(
          getOrientation() ? Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR) : Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
        myGlassPane.setCursor(getOrientation() ? Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR) : Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR), myListener);
       
        myPoint = SwingUtilities.convertPoint(this, e.getPoint(), ThreeComponentsSplitter.this);
        float proportion;
        if (getOrientation()) {
          if (getHeight() > 0 || myDividerZone > 0) {
            if (myIsFirst) {
              setFirstSize(Math.max(getMinSize(myFirstComponent), myPoint.y));
            }
            else {
              setLastSize(Math.max(getMinSize(myLastComponent), ThreeComponentsSplitter.this.getHeight() - myPoint.y - getDividerWidth()));
            }
          }
        }
        else {
          if (getWidth() > 0 || myDividerZone > 0) {
            if (myIsFirst) {
              setFirstSize(Math.max(getMinSize(myFirstComponent), myPoint.x));
            }
            else {
              setLastSize(Math.max(getMinSize(myLastComponent), ThreeComponentsSplitter.this.getWidth() - myPoint.x - getDividerWidth()));
            }
          }
        }
        ThreeComponentsSplitter.this.doLayout();
      } else if (MouseEvent.MOUSE_MOVED == e.getID()) {
        if (isInside(e.getPoint())) {
          myGlassPane.setCursor(getOrientation() ? Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR) : Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR), myListener);
          e.consume();
        } else {
          myGlassPane.setCursor(null, myListener);
        }
      }

      if (myWasPressedOnMe) {
        e.consume();
      }
    }

    private int getMinSize(JComponent component) {
      if (isHonorMinimumSize()) {
        if (component != null && myFirstComponent != null && myFirstComponent.isVisible() && myLastComponent != null && myLastComponent.isVisible()) {
          if (getOrientation()) {
            return component.getMinimumSize().height;
          }
          else {
            return component.getMinimumSize().width;
          }
        }
      }
      return 0;
    }

    protected void processMouseEvent(MouseEvent e) {
      super.processMouseEvent(e);
      switch (e.getID()) {
        case MouseEvent.MOUSE_ENTERED:
          {
            setCursor(getOrientation() ? Cursor.getPredefinedCursor(9) : Cursor.getPredefinedCursor(11));
            break;
          }
        case MouseEvent.MOUSE_EXITED:
          {
            if (!myDragging) {
              setCursor(Cursor.getPredefinedCursor(0));
            }
            break;
          }
        case MouseEvent.MOUSE_PRESSED:
          {
            if (isInside(e.getPoint())) {
              myWasPressedOnMe = true;
              setCursor(getOrientation() ? Cursor.getPredefinedCursor(9) : Cursor.getPredefinedCursor(11));
              e.consume();
            } else {
              myWasPressedOnMe = false;
            }
            break;
          }
        case MouseEvent.MOUSE_RELEASED:
          {
            if (myWasPressedOnMe) {
              e.consume();
            }
            myWasPressedOnMe = false;
            myDragging = false;
            myPoint = null;
            break;
          }
        case MouseEvent.MOUSE_CLICKED:
          {
            if (e.getClickCount() == 2) {
              center();
            }
            break;
          }
      }
    }
  }

}
