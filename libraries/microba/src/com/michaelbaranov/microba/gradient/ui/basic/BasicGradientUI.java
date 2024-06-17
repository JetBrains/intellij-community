package com.michaelbaranov.microba.gradient.ui.basic;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.beans.PropertyChangeListener;

import javax.swing.JComponent;
import javax.swing.SwingConstants;
import javax.swing.plaf.ComponentUI;

import com.michaelbaranov.microba.common.BoundedTableModel;
import com.michaelbaranov.microba.gradient.GradientBar;
import com.michaelbaranov.microba.gradient.ui.GradientListener;
import com.michaelbaranov.microba.gradient.ui.GradientUI;

public class BasicGradientUI extends GradientUI {

	public static ComponentUI createUI(JComponent c) {
		return new BasicGradientUI();
	}

	public void installUI(JComponent component) {
		installListeners((GradientBar) component);
	}

	public void uninstallUI(JComponent component) {
		uninstallListeners((GradientBar) component);
	}

	protected void installListeners(GradientBar gradient) {
		GradientListener listener = createListener(gradient);

		if (gradient.getDataModel() != null)
			gradient.getDataModel().addTableModelListener(listener);
		gradient.addPropertyChangeListener(listener);
	}

	protected void uninstallListeners(GradientBar gradient) {
		GradientListener listener = lookupListsner(gradient);
		if (listener != null) {
			if (gradient.getDataModel() != null)
				gradient.getDataModel().removeTableModelListener(listener);
			gradient.removePropertyChangeListener(listener);
		}

	}

	protected GradientListener lookupListsner(GradientBar gradient) {
		PropertyChangeListener[] listeners = gradient
				.getPropertyChangeListeners();

		if (listeners != null) {
			for (int counter = 0; counter < listeners.length; counter++) {
				if (listeners[counter] instanceof GradientListener) {
					return (GradientListener) listeners[counter];
				}
			}
		}
		return null;
	}

	protected GradientListener createListener(GradientBar gradient) {
		return new GradientListener(this, gradient);
	}

	public void paint(Graphics g, JComponent c) {
		Graphics2D g2 = (Graphics2D) g;

		GradientBar gradient = (GradientBar) c;

		calculateViewRect(gradient);

		BoundedTableModel colorModel = gradient.getDataModel();
		BoundedTableModel alphaModel = gradient.getAlphaModel();

		if (colorModel == null)
			return;

		// TODO: alpha support

		int numColorPoints = colorModel.getRowCount();
		int numAlphaPoints = alphaModel == null ? 0 : alphaModel.getRowCount();

		int colorPositionColumn = gradient.getColorPositionColumn();
		int colorColumn = gradient.getColorColumn();

		int alphaPositionColumn = gradient.getAlphaPositionColumn();
		int alphaColumn = gradient.getAlphaColumn();

		switch (numColorPoints) {
		case 0:
			// draw checkers?
			// g.drawString("no data", viewRect.x, viewRect.y +
			// viewRect.height);
			break;
		case 1:
			g.setColor((Color) colorModel.getValueAt(0, colorColumn));
			g.fillRect(viewRect.x, viewRect.y, viewRect.width, viewRect.height);
			break;
		default:
			PreparedColorPoint pts[] = prepareColorPoints(colorModel,
					colorPositionColumn, colorColumn);
			// PreparedAlphaPoint apts[] = prepareAlphaPoints(alphaModel,
			// colorPositionColumn, colorColumn);

			int range = colorModel.getUpperBound() - colorModel.getLowerBound();
			boolean isHorizontal = gradient.getOrientation() == SwingConstants.HORIZONTAL;

			for (int i = 0; i < numColorPoints - 1; i++) {
				PreparedColorPoint p0 = pts[i];
				PreparedColorPoint p1 = pts[i + 1];

				int pos0 = p0.position - colorModel.getLowerBound();
				int pos1 = p1.position - colorModel.getLowerBound();

				int pixPos0;
				int pixPos1;
				if (isHorizontal) {
					pixPos0 = viewRect.x + viewRect.width * pos0 / range;
					pixPos1 = viewRect.x + viewRect.width * pos1 / range;
				} else {
					pixPos0 = viewRect.y + viewRect.height * pos0 / range;
					pixPos1 = viewRect.y + viewRect.height * pos1 / range;
				}

				int pixDist = pixPos1 - pixPos0;

				for (int t = pixPos0; t < pixPos1; t++) {
					Color cc = interpolate(p0, p1, pixDist, t - pixPos0);
					g2.setColor(cc);
					if (isHorizontal)
						g2.drawLine(t, viewRect.y, t, viewRect.y
								+ viewRect.height);
					else
						g2.drawLine(viewRect.x, t, viewRect.x + viewRect.width,
								t);

				}

			}
			break;
		}

	}

	private Color interpolate(PreparedColorPoint p0, PreparedColorPoint p1,
			int pixDist, int t) {
		int r = p0.r + (p1.r - p0.r) * t / pixDist;
		int g = p0.g + (p1.g - p0.g) * t / pixDist;
		int b = p0.b + (p1.b - p0.b) * t / pixDist;
		return new Color(r, g, b);
	}

	public Dimension getMinimumSize(JComponent c) {
		return new Dimension(1, 1);
	}

	public Dimension getPreferredSize(JComponent c) {

		GradientBar gradient = (GradientBar) c;
		BoundedTableModel dataModel = gradient.getDataModel();
		Insets ins = gradient.getInsets();

		int r;
		if (dataModel == null)
			r = 1;
		else
			r = dataModel.getUpperBound() - dataModel.getLowerBound();

		if (gradient.getOrientation() == SwingConstants.HORIZONTAL)
			return new Dimension((r) * 2 + ins.left + ins.right + 1,
					PREF_BAR_HEIGHT + ins.top + ins.bottom + 1);
		else
			return new Dimension(PREF_BAR_HEIGHT + ins.top + ins.bottom + 1,
					(r) * 2 + ins.left + ins.right + 1);

	}

}
