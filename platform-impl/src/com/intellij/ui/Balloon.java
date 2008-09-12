package com.intellij.ui;

import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.impl.content.GraphicsConfig;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.ui.Animator;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.GeneralPath;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

public class Balloon {

  private MyComponent myComp;
  private JLayeredPane myLayeredPane;
  private Position myPosition;
  private Point myTargetPoint;

  private Color myBorderColor;
  private Color myFillColor;

  private final AWTEventListener myActiveityListener = new AWTEventListener() {
    public void eventDispatched(final AWTEvent event) {
      if (event.getID() == MouseEvent.MOUSE_PRESSED || event.getID() == KeyEvent.KEY_PRESSED) {
        hide();
      }
    }
  };
  private final ComponentAdapter myComponentListener = new ComponentAdapter() {
    public void componentResized(final ComponentEvent e) {
      hide();
    }
  };
  private Animator myAnimator;
  private boolean myShowPointer = true;

  public Balloon(JComponent content, Color borderColor, Color fillColor) {
    myComp = new MyComponent(content);
    myBorderColor = borderColor;
    myFillColor = fillColor;
  }

  public void show(RelativePoint target, Position position) {
    if (isVisible()) return;

    assert target.getComponent().isShowing();

    final Window window = SwingUtilities.getWindowAncestor(target.getComponent());

    JRootPane root = null;
    if (window instanceof JFrame) {
      root = ((JFrame)window).getRootPane();
    }
    else if (window instanceof JDialog) {
      root = ((JDialog)window).getRootPane();
    }
    else {
      assert false : window;
    }

    myLayeredPane = root.getLayeredPane();
    myLayeredPane.addComponentListener(myComponentListener);
    myPosition = position;

    myComp.setBorder(myPosition.createBorder(this));

    myTargetPoint = target.getPoint(myLayeredPane);

    myComp.clear();
    myComp.myAlpha = 0f;

    myLayeredPane.add(myComp, JLayeredPane.POPUP_LAYER);

    myPosition.updateLocation(this);


    myAnimator = new Animator("Balloon", 10, 500, false, 0, 1) {
      public void paintNow(final float frame, final float totalFrames, final float cycle) {
        if (!isVisible()) return;
        myComp.setAlpha(frame / totalFrames);
      }

      @Override
      protected void onAnimationMaxCycleReached() throws InterruptedException {
        myComp.clear();
        myComp.repaint();
        myAnimator = null;
        Disposer.dispose(this);
      }
    };
    myAnimator.setTakInitialDelay(false);
    myAnimator.resume();

    myLayeredPane.revalidate();
    myLayeredPane.repaint();

    Toolkit.getDefaultToolkit().addAWTEventListener(myActiveityListener, MouseEvent.MOUSE_EVENT_MASK | KeyEvent.KEY_EVENT_MASK);
  }


  int getArc() {
    return 6;
  }

  int getPointerWidth() {
    return 12;
  }

  int getNormalInset() {
    return 4;
  }

  int getShadowShift() {
    return 10;
  }

  int getPointerLength() {
    return 16;
  }

  public void hide() {
    if (!isVisible()) return;

    Toolkit.getDefaultToolkit().removeAWTEventListener(myActiveityListener);
    myLayeredPane.removeComponentListener(myComponentListener);

    myLayeredPane.remove(myComp);
    myComp.clear();

    myLayeredPane.revalidate();
    myLayeredPane.repaint();

    myLayeredPane = null;

    onHidden();
  }

  protected void onHidden() {

  }

  public boolean isVisible() {
    return myLayeredPane != null;
  }

  public void setShowPointer(final boolean show) {
    myShowPointer = show;
  }

  public abstract static class Position {

    abstract Border createBorder(final Balloon balloon);


    public void updateLocation(final Balloon balloon) {
      final Dimension size = balloon.myComp.getPreferredSize();
      balloon.myComp.setSize(size);
      final Dimension layeredPaneSize = balloon.myLayeredPane.getSize();
      Point location = getLocation(layeredPaneSize, balloon.myTargetPoint, size);
      final Rectangle bounds = new Rectangle(location.x, location.y, size.width, size.height);

      ScreenUtil.moveToFit(bounds, new Rectangle(0, 0, layeredPaneSize.width, layeredPaneSize.height));

      balloon.myComp.setBounds(bounds);
    }

    abstract Point getLocation(final Dimension containerSize, final Point targetPoint, final Dimension balloonSize);

    void paintComponent(Balloon balloon, final JComponent c, final Graphics2D g, Point pointTarget) {
      final GraphicsConfig cfg = new GraphicsConfig(g);
      cfg.setAntialiasing(true);

      final Rectangle bounds = new Rectangle(0, 0, c.getWidth(), c.getHeight());

      Shape shape;
      if (balloon.myShowPointer) {
        shape = getPointingShape(bounds, g, pointTarget, balloon);
      } else {
        convertBoundsToContent(bounds, balloon);
        bounds.width -= 2;
        bounds.height -= 1;
        bounds.x += 1;
        bounds.y += 1;
        shape = new RoundRectangle2D.Double(bounds.x, bounds.y, bounds.width, bounds.height, balloon.getArc(), balloon.getArc());
      }

      g.setColor(balloon.myFillColor);
      g.fill(shape);
      g.setColor(balloon.myBorderColor);
      g.draw(shape);
      cfg.restore();
    }

    protected abstract void convertBoundsToContent(final Rectangle bounds, final Balloon balloon);

    protected abstract Shape getPointingShape(final Rectangle bounds, final Graphics2D g, final Point pointTarget, final Balloon balloon);

  }

  public static final Position BELOW = new Below();
  public static final Position UNDER = new Under();
  public static final Position AT_RIGHT = new AtRight();
  public static final Position AT_LEFT = new AtLeft();


  private static class Below extends Position {
    Border createBorder(final Balloon balloon) {
      return new EmptyBorder(balloon.getPointerLength(), balloon.getNormalInset(), balloon.getNormalInset(), balloon.getNormalInset());
    }

    Point getLocation(final Dimension containerSize, final Point targetPoint, final Dimension balloonSize) {
      final Point center = UIUtil.getCenterPoint(new Rectangle(targetPoint, new Dimension(0, 0)), balloonSize);
      return new Point(center.x, targetPoint.y);
    }

    protected void convertBoundsToContent(final Rectangle bounds, final Balloon balloon) {
      bounds.y += balloon.getPointerLength();
      bounds.height -= balloon.getPointerLength();
    }

    protected Shape getPointingShape(final Rectangle bounds, final Graphics2D g, final Point pointTarget, final Balloon balloon) {
      final Shaper shaper = new Shaper(balloon, bounds, pointTarget, SwingUtilities.TOP);
      shaper.line(balloon.getPointerWidth() / 2, balloon.getPointerLength()).toRightCurve().roundRightDown().toBottomCurve().roundLeftDown()
          .toLeftCurve().roundLeftUp().toTopCurve().roundUpRight()
          .lineTo(pointTarget.x - balloon.getPointerWidth() / 2, shaper.getCurrent().y).lineTo(pointTarget.x, pointTarget.y);
      shaper.close();

      return shaper.getShape();
    }

    protected Shape getShape(final Rectangle bounds, final Graphics2D g, final Point pointTarget, final Balloon balloon) {
      bounds.y += balloon.getPointerLength();
      bounds.height += balloon.getPointerLength();
      return new RoundRectangle2D.Double(bounds.x, bounds.y, bounds.width, bounds.height, balloon.getArc(), balloon.getArc());
    }

  }

  private static class Under extends Position {
    Border createBorder(final Balloon balloon) {
      return new EmptyBorder(balloon.getNormalInset(), balloon.getNormalInset(), balloon.getPointerLength(), balloon.getNormalInset());
    }

    Point getLocation(final Dimension containerSize, final Point targetPoint, final Dimension balloonSize) {
      final Point center = UIUtil.getCenterPoint(new Rectangle(targetPoint, new Dimension(0, 0)), balloonSize);
      return new Point(center.x, targetPoint.y - balloonSize.height);
    }

    protected void convertBoundsToContent(final Rectangle bounds, final Balloon balloon) {
      bounds.height -= balloon.getPointerLength();
    }

    protected Shape getShape(final Rectangle bounds, final Graphics2D g, final Point pointTarget, final Balloon balloon) {
      bounds.y -= balloon.getPointerLength();
      bounds.height -= balloon.getPointerLength();
      return new RoundRectangle2D.Double(bounds.x, bounds.y, bounds.width, bounds.height, balloon.getArc(), balloon.getArc());
    }

    @Override
    protected Shape getPointingShape(final Rectangle bounds, final Graphics2D g, final Point pointTarget, final Balloon balloon) {
      final Shaper shaper = new Shaper(balloon, bounds, pointTarget, SwingUtilities.BOTTOM);
      shaper.line(-balloon.getPointerWidth() / 2, -balloon.getPointerLength() + 1);
      shaper.toLeftCurve().roundLeftUp().toTopCurve().roundUpRight().toRightCurve().roundRightDown().toBottomCurve().line(0, 2)
          .roundLeftDown().lineTo(pointTarget.x + balloon.getPointerWidth() / 2, shaper.getCurrent().y).lineTo(pointTarget.x, pointTarget.y)
          .close();


      return shaper.getShape();
    }
  }

  private static class AtRight extends Position {
    Border createBorder(final Balloon balloon) {
      return new EmptyBorder(balloon.getNormalInset(), balloon.getPointerLength(), balloon.getNormalInset(), balloon.getNormalInset());
    }

    Point getLocation(final Dimension containerSize, final Point targetPoint, final Dimension balloonSize) {
      final Point center = UIUtil.getCenterPoint(new Rectangle(targetPoint, new Dimension(0, 0)), balloonSize);
      return new Point(targetPoint.x, center.y);
    }

    @Override
    protected Shape getPointingShape(final Rectangle bounds, final Graphics2D g, final Point pointTarget, final Balloon balloon) {
      final Shaper shaper = new Shaper(balloon, bounds, pointTarget, SwingUtilities.LEFT);
      shaper.line(balloon.getPointerLength(), -balloon.getPointerWidth() / 2).toTopCurve().roundUpRight().toRightCurve().roundRightDown()
          .toBottomCurve().roundLeftDown().toLeftCurve().roundLeftUp()
          .lineTo(shaper.getCurrent().x, pointTarget.y + balloon.getPointerWidth() / 2).lineTo(pointTarget.x, pointTarget.y).close();

      return shaper.getShape();
    }

    protected void convertBoundsToContent(final Rectangle bounds, final Balloon balloon) {
      bounds.x += balloon.getPointerLength();
      bounds.width -= balloon.getPointerLength();
    }

    protected Shape getShape(final Rectangle bounds, final Graphics2D g, final Point pointTarget, final Balloon balloon) {
      bounds.x += balloon.getPointerLength();
      bounds.width -= balloon.getPointerLength();
      return new RoundRectangle2D.Double(bounds.x, bounds.y, bounds.width, bounds.height, balloon.getArc(), balloon.getArc());
    }
  }

  private static class AtLeft extends Position {
    Border createBorder(final Balloon balloon) {
      return new EmptyBorder(balloon.getNormalInset(), balloon.getNormalInset(), balloon.getNormalInset(), balloon.getPointerLength());
    }

    Point getLocation(final Dimension containerSize, final Point targetPoint, final Dimension balloonSize) {
      final Point center = UIUtil.getCenterPoint(new Rectangle(targetPoint, new Dimension(0, 0)), balloonSize);
      return new Point(targetPoint.x - balloonSize.width, center.y);
    }

    protected void convertBoundsToContent(final Rectangle bounds, final Balloon balloon) {
      bounds.width -= balloon.getPointerLength();
    }

    @Override
    protected Shape getPointingShape(final Rectangle bounds, final Graphics2D g, final Point pointTarget, final Balloon balloon) {
      final Shaper shaper = new Shaper(balloon, bounds, pointTarget, SwingUtilities.RIGHT);
      shaper.line(-balloon.getPointerLength(), balloon.getPointerWidth() / 2);
      shaper.toBottomCurve().roundLeftDown().toLeftCurve().roundLeftUp().toTopCurve().roundUpRight().toRightCurve().roundRightDown()
          .lineTo(shaper.getCurrent().x, pointTarget.y - balloon.getPointerWidth() / 2).lineTo(pointTarget.x, pointTarget.y).close();
      return shaper.getShape();
    }

    protected Shape getShape(final Rectangle bounds, final Graphics2D g, final Point pointTarget, final Balloon balloon) {
      bounds.width -= balloon.getPointerLength();
      return new RoundRectangle2D.Double(bounds.x, bounds.y, bounds.width, bounds.height, balloon.getArc(), balloon.getArc());
    }
  }

  private class MyComponent extends JPanel {

    private BufferedImage myImage;
    private float myAlpha;

    private MyComponent(JComponent content) {
      setOpaque(false);
      setLayout(new BorderLayout());

      final Wrapper wrapper = new Wrapper(content);
      wrapper.setOpaque(false);
      wrapper.setBorder(new EmptyBorder(4, 4, 4, 4));

      add(wrapper, BorderLayout.CENTER);
    }

    public void clear() {
      myImage = null;
      myAlpha = -1;
    }

    @Override
    protected void paintComponent(final Graphics g) {
      super.paintComponent(g);
      final Point pointTarget = SwingUtilities.convertPoint(myLayeredPane, myTargetPoint, this);

      if (myImage == null && myAlpha != -1) {
        myImage = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
        myPosition.paintComponent(Balloon.this, this, (Graphics2D)myImage.getGraphics(), pointTarget);
      }

      if (myImage != null && myAlpha != -1) {
        final Graphics2D g2d = (Graphics2D)g;
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, myAlpha));

        g2d.drawImage(myImage, 0, 0, null);
      } else {
        myPosition.paintComponent(Balloon.this, this, (Graphics2D)g, pointTarget);
      }
    }

    public void setAlpha(float alpha) {
      myAlpha = alpha;
      paintImmediately(0, 0, getWidth(), getHeight());
    }
  }

  private static class Shaper {
    private GeneralPath myPath = new GeneralPath();

    Rectangle myBounds;
    private int myTargetSide;
    private Balloon myBalloon;

    public Shaper(Balloon balloon, Rectangle bounds, Point targetPoint, int targetSide) {
      myBalloon = balloon;
      myBounds = bounds;
      myTargetSide = targetSide;
      start(targetPoint);
    }

    private void start(Point start) {
      myPath.moveTo(start.x, start.y);
    }

    public Shaper roundUpRight() {
      myPath.quadTo(getCurrent().x, getCurrent().y - myBalloon.getArc(), getCurrent().x + myBalloon.getArc(),
                    getCurrent().y - myBalloon.getArc());
      return this;
    }

    public Shaper roundRightDown() {
      myPath.quadTo(getCurrent().x + myBalloon.getArc(), getCurrent().y, getCurrent().x + myBalloon.getArc(),
                    getCurrent().y + myBalloon.getArc());
      return this;
    }

    public Shaper roundLeftUp() {
      myPath.quadTo(getCurrent().x - myBalloon.getArc(), getCurrent().y, getCurrent().x - myBalloon.getArc(),
                    getCurrent().y - myBalloon.getArc());
      return this;
    }

    public Shaper roundLeftDown() {
      myPath.quadTo(getCurrent().x, getCurrent().y + myBalloon.getArc(), getCurrent().x - myBalloon.getArc(),
                    getCurrent().y + myBalloon.getArc());
      return this;
    }

    public Point getCurrent() {
      return new Point((int)myPath.getCurrentPoint().getX(), (int)myPath.getCurrentPoint().getY());
    }

    public Shaper line(final int deltaX, final int deltaY) {
      myPath.lineTo(getCurrent().x + deltaX, getCurrent().y + deltaY);
      return this;
    }

    public Shaper lineTo(final int x, final int y) {
      myPath.lineTo(x, y);
      return this;
    }


    private int getTargetDelta(int effectiveSide) {
      return effectiveSide == myTargetSide ? myBalloon.getPointerLength() : 0;
    }

    public Shaper toRightCurve() {
      myPath.lineTo((int)myBounds.getMaxX() - myBalloon.getArc() - getTargetDelta(SwingUtilities.RIGHT) - 1, getCurrent().y);
      return this;
    }

    public Shaper toBottomCurve() {
      myPath.lineTo(getCurrent().x, (int)myBounds.getMaxY() - myBalloon.getArc() - getTargetDelta(SwingUtilities.BOTTOM) - 1);
      return this;
    }

    public Shaper toLeftCurve() {
      myPath.lineTo((int)myBounds.getX() + myBalloon.getArc() + getTargetDelta(SwingUtilities.LEFT), getCurrent().y);
      return this;
    }

    public Shaper toTopCurve() {
      myPath.lineTo(getCurrent().x, (int)myBounds.getY() + myBalloon.getArc() + getTargetDelta(SwingUtilities.TOP));
      return this;
    }

    public void close() {
      myPath.closePath();
    }

    public Shape getShape() {
      return myPath;
    }
  }

  public static void main(String[] args) {
    final JFrame frame = new JFrame();
    frame.getContentPane().setLayout(new BorderLayout());
    final JPanel content = new JPanel(new BorderLayout());
    frame.getContentPane().add(content, BorderLayout.CENTER);


    content.setBackground(Color.white);

    final JLabel label = new JLabel("<html>Really cool balloon<br>Really fucking big</html>");
    final Balloon balloon = new Balloon(label, Color.black, Color.pink);
    balloon.setShowPointer(false);

    content.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(final MouseEvent e) {
        if (balloon.isVisible()) {
          balloon.hide();
        }
        else {
          if (e.isControlDown()) {
            balloon.show(new RelativePoint(e), Balloon.UNDER);
          }
          else if (e.isAltDown()) {
            balloon.show(new RelativePoint(e), Balloon.BELOW);
          }
          else if (e.isMetaDown()) {
            balloon.show(new RelativePoint(e), Balloon.AT_LEFT);
          }
          else {
            balloon.show(new RelativePoint(e), Balloon.AT_RIGHT);
          }
        }
      }
    });

    frame.setBounds(300, 300, 300, 300);
    frame.show();
  }

}