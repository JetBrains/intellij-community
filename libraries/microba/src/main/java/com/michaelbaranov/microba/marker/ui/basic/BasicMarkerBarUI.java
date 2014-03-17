package com.michaelbaranov.microba.marker.ui.basic;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.event.MouseMotionListener;

import javax.swing.JComponent;
import javax.swing.ListSelectionModel;
import javax.swing.LookAndFeel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicGraphicsUtils;

import com.michaelbaranov.microba.common.BoundedTableModel;
import com.michaelbaranov.microba.marker.MarkerBar;
import com.michaelbaranov.microba.marker.ui.MarkerBarListener;
import com.michaelbaranov.microba.marker.ui.MarkerBarUI;

public class BasicMarkerBarUI extends MarkerBarUI {

	private Color selectionColor;

	private Color shadowColor;

	private Color focusColor;

	private Color disabledColor;

	private Color normalColor;

	private Color textColor;

	public static ComponentUI createUI(JComponent c) {
		return new BasicMarkerBarUI();
	}

	public void installUI(JComponent component) {
		installListeners((MarkerBar) component);
		installKeyboardActions((MarkerBar) component);
		installDefaults((MarkerBar) component);
	}

	public void uninstallUI(JComponent component) {
		uninstallListeners((MarkerBar) component);
		uninstallKeyboardActions((MarkerBar) component);
	}

	protected void installDefaults(MarkerBar bar) {
		LookAndFeel.installBorder(bar, "Slider.border");
		LookAndFeel.installColors(bar, "Slider.background", "Slider.foreground");

		// selectedmark
		selectionColor = UIManager.getColor("ComboBox.selectionBackground");
		// fixed mark
		disabledColor = UIManager.getColor("ComboBox.disabledBackground");
		normalColor = UIManager.getColor("ComboBox.background");
		// focus rect
		focusColor = UIManager.getColor("Slider.focus");
		// text & outline color
		textColor = UIManager.getColor("textText");

	}

	protected void installKeyboardActions(MarkerBar bar) {
		MarkerBarListener listener = lookupListsner(bar);
		if (listener != null)
			listener.installKeyboardActions(bar);

	}

	protected void uninstallKeyboardActions(MarkerBar bar) {
		MarkerBarListener listener = lookupListsner(bar);
		if (listener != null)
			listener.uninstallKeyboardActions(bar);

	}

	protected void installListeners(MarkerBar markerBar) {
		MarkerBarListener listener = createListener(markerBar);

		if (markerBar.getDataModel() != null)
			markerBar.getDataModel().addTableModelListener(listener);
		if (markerBar.getSelectionModel() != null)
			markerBar.getSelectionModel().addListSelectionListener(listener);
		markerBar.addMouseListener(listener);
		markerBar.addMouseMotionListener(listener);
		markerBar.addFocusListener(listener);
		markerBar.addPropertyChangeListener(listener);
		markerBar.addComponentListener(listener);
	}

	protected void uninstallListeners(MarkerBar markerBar) {
		MarkerBarListener listener = lookupListsner(markerBar);
		if (listener != null) {
			if (markerBar.getDataModel() != null)
				markerBar.getDataModel().removeTableModelListener(listener);
			if (markerBar.getSelectionModel() != null)
				markerBar.getSelectionModel().removeListSelectionListener(listener);
			markerBar.removeMouseListener(listener);
			markerBar.removeMouseMotionListener(listener);
			markerBar.removeFocusListener(listener);
			markerBar.removePropertyChangeListener(listener);
			markerBar.addComponentListener(listener);
		}

	}

	protected MarkerBarListener lookupListsner(MarkerBar markerBar) {
		MouseMotionListener[] listeners = markerBar.getMouseMotionListeners();

		if (listeners != null) {
			for (int counter = 0; counter < listeners.length; counter++) {
				if (listeners[counter] instanceof MarkerBarListener) {
					return (MarkerBarListener) listeners[counter];
				}
			}
		}
		return null;
	}

	protected MarkerBarListener createListener(MarkerBar markerBar) {
		return new MarkerBarListener(this, markerBar);
	}

	public void paint(Graphics g, JComponent c) {

		MarkerBar bar = (MarkerBar) c;

		calculateViewRectAndBaseline(bar);

		if (bar.isFocusOwner())
			drawFocusRect(g, viewRect);

		BoundedTableModel dataModel = bar.getDataModel();
		if (dataModel == null)
			return;

		int numAreas = dataModel.getRowCount();
		Polygon areas[] = calculateMarkerAreas(bar);

		ListSelectionModel selectionModel = bar.getSelectionModel();

		for (int i = 0; i < numAreas; i++) {
			boolean isMovable = dataModel.isCellEditable(i, bar.getPositionColumn());
			boolean isLeadSelect = (selectionModel == null || selectionModel
					.isSelectionEmpty()) ? false
					: selectionModel.getLeadSelectionIndex() == i;
			if (!isLeadSelect)
				drawMarker(g, areas[i], false, (Color) dataModel.getValueAt(i, bar
						.getColorColumn()));
		}

		if (selectionModel != null && !selectionModel.isSelectionEmpty()) {
			int selectedIndex = selectionModel.getLeadSelectionIndex();
			boolean isMovable = dataModel.isCellEditable(selectedIndex, bar
					.getPositionColumn());

			if (selectedIndex < numAreas) {
				drawMarker(g, areas[selectedIndex], /* bar.isFocusOwner() */
				true, (Color) dataModel.getValueAt(selectedIndex, bar.getColorColumn()));
			} else {
				throw new IllegalStateException(
						"Selection model inconsistent with data model: " + "element at "
								+ selectedIndex + " selected, but does not exist.");
			}
		}

	}

	protected void drawFocusRect(Graphics g, Rectangle viewRect) {
		g.setColor(getFocusColor());

		BasicGraphicsUtils.drawDashedRect(g, viewRect.x, viewRect.y, viewRect.width,
				viewRect.height);
	}

	protected void drawMarker(Graphics g, Polygon p, boolean isSelected, Color color) {

		Color innerColor = color != null ? color : getNormalColor();
		// Color selColor = getSelectionColor();
		Color selColor = isSelected ? getTextColor() : innerColor;

		// body
		g.setColor(innerColor);
		g.fillPolygon(p);

		// bick

		if (isSelected) {
			int xx[] = new int[3];
			int yy[] = new int[3];

			xx[0] = p.xpoints[0];
			xx[1] = p.xpoints[1];
			xx[2] = p.xpoints[p.npoints - 1];
			yy[0] = p.ypoints[0];
			yy[1] = p.ypoints[1];
			yy[2] = p.ypoints[p.npoints - 1];

			Polygon bickP = new Polygon(xx, yy, 3);
			g.setColor(selColor);
			g.fillPolygon(bickP);
			g.drawPolygon(bickP);
		}

		// outline
		g.setColor(getTextColor());
		g.drawPolygon(p);

	}

	public Dimension getMinimumSize(JComponent c) {
		MarkerBar bar = (MarkerBar) c;
		Insets ins = bar.getInsets();

		if (bar.getOrientation() == SwingConstants.HORIZONTAL)
			return new Dimension(MARKER_BODY_WIDTH + ins.left + ins.right + 1,
					MARKER_BODY_HEIGHT + ins.top + ins.bottom + 1);
		else
			return new Dimension(MARKER_BODY_HEIGHT + ins.top + ins.bottom + 1,
					MARKER_BODY_WIDTH + ins.left + ins.right + 1);
	}

	public Dimension getPreferredSize(JComponent c) {

		MarkerBar bar = (MarkerBar) c;
		BoundedTableModel dataModel = bar.getDataModel();
		Insets ins = bar.getInsets();

		int r;
		if (dataModel == null)
			r = 1;
		else
			r = dataModel.getUpperBound() - dataModel.getLowerBound();

		if (bar.getOrientation() == SwingConstants.HORIZONTAL)
			return new Dimension((r) * 2 + MARKER_BODY_WIDTH + ins.left + ins.right + 1,
					MARKER_BODY_HEIGHT + MARKER_BICK_HEIGHT + ins.top + ins.bottom + 1);
		else
			return new Dimension(MARKER_BODY_HEIGHT + MARKER_BICK_HEIGHT + ins.top
					+ ins.bottom + 1, (r) * 2 + MARKER_BODY_WIDTH + ins.left + ins.right
					+ 1);

	}

	public Color getFocusColor() {
		return focusColor;
	}

	public Color getShadowColor() {
		return shadowColor;
	}

	public Color getNormalColor() {
		return normalColor;
	}

	public Color getSelectionColor() {
		return selectionColor;
	}

	public Color getDisabledColor() {
		return disabledColor;
	}

	public Color getTextColor() {
		return textColor;
	}

}
