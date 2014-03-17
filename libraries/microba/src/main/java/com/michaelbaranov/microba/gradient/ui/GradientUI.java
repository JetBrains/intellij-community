package com.michaelbaranov.microba.gradient.ui;

import java.awt.Color;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Comparator;

import javax.swing.plaf.ComponentUI;

import com.michaelbaranov.microba.common.BoundedTableModel;
import com.michaelbaranov.microba.gradient.GradientBar;

public class GradientUI extends ComponentUI {

	protected static final int PREF_BAR_HEIGHT = 32;

	protected static Rectangle viewRect = new Rectangle();

	private PreparedColorPoint colorPoints[] = new PreparedColorPoint[0];

	private PreparedAlphaPoint alphaPoints[] = new PreparedAlphaPoint[0];

	protected void calculateViewRect(GradientBar gradient) {
		Insets insets = gradient.getInsets();
		viewRect.x = insets.left;
		viewRect.y = insets.top;
		viewRect.width = gradient.getWidth() - (insets.right + viewRect.x);
		viewRect.height = gradient.getHeight() - (insets.bottom + viewRect.y);

	}

	protected PreparedColorPoint[] prepareColorPoints(BoundedTableModel model,
			int positionColumn, int colorColumn) {

		int numPoints = model.getRowCount();

		if (numPoints > colorPoints.length) {
			colorPoints = new PreparedColorPoint[numPoints * 2];
			for (int i = 0; i < colorPoints.length; i++)
				colorPoints[i] = new PreparedColorPoint();
		}

		for (int i = 0; i < numPoints; i++) {
			PreparedColorPoint p = colorPoints[i];
			Color c = (Color) model.getValueAt(i, colorColumn);
			p.position = ((Number) model.getValueAt(i, positionColumn))
					.intValue();
			p.r = c.getRed();
			p.g = c.getGreen();
			p.b = c.getBlue();
		}

		Arrays.sort(colorPoints, 0, numPoints, PreparedColorPoint.comparator);

		return colorPoints;
	}

	protected PreparedAlphaPoint[] prepareAlphaPoints(BoundedTableModel model,
			int positionColumn, int alphaColumn) {

		int numPoints = model.getRowCount();

		if (numPoints > alphaPoints.length) {
			alphaPoints = new PreparedAlphaPoint[numPoints * 2];
			for (int i = 0; i < alphaPoints.length; i++)
				alphaPoints[i] = new PreparedAlphaPoint();
		}

		for (int i = 0; i < numPoints; i++) {
			PreparedAlphaPoint p = alphaPoints[i];
			float f = ((Number) model.getValueAt(i, alphaColumn)).floatValue();
			p.position = ((Number) model.getValueAt(i, positionColumn))
					.intValue();
			p.alpha = f;
		}

		Arrays.sort(alphaPoints, 0, numPoints, PreparedAlphaPoint.comparator);

		return alphaPoints;
	}

	protected static class PreparedColorPoint {
		public static final Comparator comparator = new PtComparator();

		public int r, g, b;

		public int position;

		public PreparedColorPoint() {
		}

		private static class PtComparator implements Comparator {
			public int compare(Object o1, Object o2) {
				PreparedColorPoint cp1 = (PreparedColorPoint) o1;
				PreparedColorPoint cp2 = (PreparedColorPoint) o2;
				if (cp1.position < cp2.position)
					return -1;
				if (cp1.position > cp2.position)
					return 1;
				return 0;
			}
		}
	}

	protected static class PreparedAlphaPoint {
		public static final Comparator comparator = new PtComparator();

		public float alpha;

		public int position;

		public PreparedAlphaPoint() {
		}

		private static class PtComparator implements Comparator {
			public int compare(Object o1, Object o2) {
				PreparedColorPoint cp1 = (PreparedColorPoint) o1;
				PreparedColorPoint cp2 = (PreparedColorPoint) o2;
				if (cp1.position < cp2.position)
					return -1;
				if (cp1.position > cp2.position)
					return 1;
				return 0;
			}
		}
	}

}
