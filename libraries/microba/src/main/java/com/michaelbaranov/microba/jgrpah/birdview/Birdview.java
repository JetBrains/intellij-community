package com.michaelbaranov.microba.jgrpah.birdview;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.UIManager;

import org.jgraph.JGraph;
import org.jgraph.event.GraphModelEvent;
import org.jgraph.event.GraphModelListener;
import org.jgraph.graph.GraphLayoutCache;

/**
 * A JFC/Swing component that displays a bird-eyes (thumbnail) view of a
 * {@link JGraph} in combination with {@link JScrollPane}. Also allows to pan
 * view with the mouse.
 * 
 * 
 * @author Michael Baranov
 * 
 */
public class Birdview extends JPanel {

	/**
	 * Color constant
	 */
	public static final Color PAN_RECT_COLOR = Color.black;

	/**
	 * Color constant
	 */
	public static final Color BACKGROUND_COLOR = UIManager.getColor("Panel.background");

	/**
	 * a {@link JGraph} component used as main display
	 */
	private JGraph displayGraph;

	/**
	 * a {@link JScrollPane} to track
	 */
	private JScrollPane peerScroller;

	/**
	 * a {@link JGraph} to tracl
	 */
	private JGraph peerGraph;

	/**
	 * a rect, subrect of the component area, that matches the tracked view
	 */
	private Rectangle2D paintRect;

	/**
	 * a rect, a subrect of the component area, that matches the tracked
	 * viewport
	 */
	private Rectangle2D panRect;

	/**
	 * the scale applied to {@link #displayGraph} to fit into {@link #paintRect}
	 */
	private double scale = 1.0;

	/**
	 * A listener
	 */
	private ScrollerListener scrollerListener = new ScrollerListener();

	/**
	 * A listener
	 */
	private SelfMouseListener selfMouseListener = new SelfMouseListener();

	/**
	 * A listener
	 */
	private GraphPropertyChangeListener graphPropertyChangeListener = new GraphPropertyChangeListener();
	
	/**
	 * Constructor.
	 * 
	 * @param doc
	 *            document to track. May be <code>null</code>
	 */
	public Birdview() {

		displayGraph = new JGraph();
		displayGraph.setEnabled(false);
		displayGraph.setAntiAliased(true);
		displayGraph.addMouseListener(selfMouseListener);
		displayGraph.addMouseMotionListener(selfMouseListener);

		this.setLayout(null);
		this.addComponentListener(new SelfResizeListener());
		this.add(displayGraph);

	}

	/**
	 * Makes this component track the provided graph and scroller. Set
	 * parameters to <code>null</code> to unbind thie component.
	 * 
	 * @param graph
	 *            the graph component. May be <code>null</code>
	 * @param scroller
	 *            the croller, usually the one that holds the graph. May be
	 *            <code>null</code>
	 */
	public void setTrackingFor(JGraph graph, JScrollPane scroller) {
		if (this.peerGraph != null) {
			this.peerGraph.getModel().removeGraphModelListener(scrollerListener);
			this.peerGraph.removePropertyChangeListener(graphPropertyChangeListener);
		}

		this.peerGraph = graph;

		if (this.peerGraph != null) {
			this.peerGraph.getModel().addGraphModelListener(scrollerListener);
			this.peerGraph.addPropertyChangeListener(graphPropertyChangeListener);

			this.displayGraph.setGraphLayoutCache(peerGraph.getGraphLayoutCache());

		} else {
			this.displayGraph.setGraphLayoutCache(new GraphLayoutCache());
		}

		//
		if (this.peerScroller != null) {
			this.peerScroller.getHorizontalScrollBar().removeAdjustmentListener(
					scrollerListener);
			this.peerScroller.getVerticalScrollBar().removeAdjustmentListener(
					scrollerListener);
		}

		this.peerScroller = scroller;

		if (this.peerScroller != null) {
			this.peerScroller.getHorizontalScrollBar().addAdjustmentListener(
					scrollerListener);
			this.peerScroller.getVerticalScrollBar().addAdjustmentListener(
					scrollerListener);
		}

		update();
		repaint();

	}

	/**
	 * Calculates {@link #paintRect}, {@link #panRect}, {@link #scale} based
	 * on observations of peerScroller.
	 * 
	 */
	private void update() {
		if (peerScroller != null) {
			Dimension viewSize = peerScroller.getViewport().getViewSize();
			double viewAspect = viewSize.getHeight() / viewSize.getWidth();

			Dimension panelSize = this.getSize();
			double panelAspect = panelSize.getHeight() / panelSize.getWidth();

			if (panelAspect < viewAspect) {
				// panel is wider, height is primary
				double desiredPanelWidth = panelSize.getHeight() / viewAspect;
				double blankWidth = panelSize.getWidth() - desiredPanelWidth;
				double gap = blankWidth / 2;
				paintRect = new Rectangle2D.Double(gap, 0, desiredPanelWidth,
						panelSize.height);
				scale = panelSize.getHeight() / viewSize.getHeight();
				scale *= peerGraph.getScale();
			} else {
				// panel is heigher, width is primary
				double desiredPanelHeight = panelSize.getWidth() * viewAspect;
				double blankHeight = panelSize.getHeight() - desiredPanelHeight;
				double gap = blankHeight / 2;
				paintRect = new Rectangle2D.Double(0, gap, panelSize.getWidth(),
						desiredPanelHeight);
				scale = panelSize.getWidth() / viewSize.getWidth();
				scale *= peerGraph.getScale();
			}

			Rectangle viewRect = peerScroller.getViewport().getViewRect();
			double shiftX = viewRect.getX() / viewSize.getWidth();
			double shiftY = viewRect.getY() / viewSize.getHeight();

			double sizeX = viewRect.getWidth() / viewSize.getWidth();
			double sizeY = viewRect.getHeight() / viewSize.getHeight();

			panRect = new Rectangle2D.Double(paintRect.getX() + paintRect.getWidth()
					* shiftX, paintRect.getY() + paintRect.getHeight() * shiftY,
					paintRect.getWidth() * sizeX, paintRect.getHeight() * sizeY);

		} else {
			panRect = null;
			paintRect = null;
			scale = 1;
		}
	}

	/**
	 * Addjusts {@link #panRect} to be postitionsed with the centter at a given
	 * point if possible. This methods ensures that {@link #panRect} fits
	 * entirely in {@link #paintRect}.
	 * 
	 * @param point
	 *            the desired panRect center
	 */
	private void panRectTo(Point point) {

		Dimension viewSize = peerScroller.getViewport().getViewSize();
		Rectangle viewRect = peerScroller.getViewport().getViewRect();

		double panHalfWidth = panRect.getWidth() / 2;
		double panHalfHeight = panRect.getHeight() / 2;

		Point2D panOrigin = new Point2D.Double(point.x - panHalfWidth, point.y
				- panHalfHeight);
		double xk = panOrigin.getX() / paintRect.getWidth();
		double yk = panOrigin.getY() / paintRect.getHeight();

		Point viewPos = new Point((int) (viewSize.getWidth() * xk), (int) (viewSize
				.getHeight() * yk));

		// make sure we do not pan past the bounds:
		if (viewPos.x < 0)
			viewPos.x = 0;
		if (viewPos.y < 0)
			viewPos.y = 0;

		int wd = (viewPos.x + viewRect.width) - viewSize.width;
		int hd = (viewPos.y + viewRect.height) - viewSize.height;

		if (wd > 0)
			viewPos.x -= wd;
		if (hd > 0)
			viewPos.y -= hd;

		// pan it
		peerScroller.getViewport().setViewPosition(viewPos);

		update();
		repaint();
	}

	public void paint(Graphics g) {
		Graphics2D g2 = (Graphics2D) g;

		// fill background
		g2.setColor(BACKGROUND_COLOR);
		g2.fillRect(0, 0, this.getWidth(), this.getHeight());

		if (peerGraph != null) {
			// paint the graph display
			displayGraph.setBounds(paintRect.getBounds());
			displayGraph.setScale(scale);
			displayGraph.setBackground(peerGraph.getBackground());
			paintChildren(g);
		}

		if (panRect != null) {
			// draw pan rect
			g2.setColor(PAN_RECT_COLOR);
			g2.draw(panRect.getBounds());
		}
	}

	/**
	 * A listener to watch for scrollbar movement.
	 * 
	 * @author Michael Baranov
	 * 
	 */
	private class ScrollerListener implements AdjustmentListener, GraphModelListener {

		public void adjustmentValueChanged(AdjustmentEvent e) {
			update();
			repaint();
		}

		public void graphChanged(GraphModelEvent e) {
			update();
			repaint();

		}

	}

	/**
	 * A listener to watch for sizing of the component itself.
	 * 
	 * @author Michael Baranov
	 * 
	 */
	private class SelfResizeListener implements ComponentListener {

		public void componentHidden(ComponentEvent e) {
		}

		public void componentMoved(ComponentEvent e) {
		}

		public void componentResized(ComponentEvent e) {
			update();
			repaint();
		}

		public void componentShown(ComponentEvent e) {
			update();
			repaint();
		}

	}

	/**
	 * A listener to watch for mouse for the component itself.
	 * 
	 * @author Michael Baranov
	 * 
	 */
	private class SelfMouseListener implements MouseListener, MouseMotionListener {

		public void mouseClicked(MouseEvent e) {
		}

		public void mouseEntered(MouseEvent e) {
		}

		public void mouseExited(MouseEvent e) {
		}

		public void mousePressed(MouseEvent e) {

		}

		public void mouseReleased(MouseEvent e) {
			if (paintRect != null)
				panRectTo(e.getPoint());

		}

		public void mouseDragged(MouseEvent e) {
			if (paintRect != null)
				panRectTo(e.getPoint());

		}

		public void mouseMoved(MouseEvent e) {
		}

	}

	/**
	 * A listener to watch for graph property change, most important "scale".
	 * 
	 * @author Michael Baranov
	 * 
	 */
	private class GraphPropertyChangeListener implements PropertyChangeListener {

		public void propertyChange(PropertyChangeEvent evt) {
			if ("scale".equals(evt.getPropertyName())) {
				update();
				repaint();
			}

		}

	}

}
