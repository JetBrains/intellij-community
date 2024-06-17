package com.michaelbaranov.microba.marker.ui;

import java.awt.Insets;
import java.awt.Polygon;
import java.awt.Rectangle;

import javax.swing.SwingConstants;
import javax.swing.plaf.ComponentUI;

import com.michaelbaranov.microba.common.BoundedTableModel;
import com.michaelbaranov.microba.marker.MarkerBar;

public class MarkerBarUI extends ComponentUI {

	protected int MARKER_BODY_WIDTH = 12;

	protected int MARKER_BODY_HEIGHT = 12;

	protected int MARKER_BICK_HEIGHT = 6;

	protected int baselineLeft;

	protected int baselineTop;

	protected int baselineLength;

	protected Rectangle viewRect = new Rectangle();

	protected Polygon[] polys = new Polygon[0];

	protected Polygon[] calculateMarkerAreas(MarkerBar bar) {

		BoundedTableModel model = bar.getDataModel();

		int count = model.getRowCount();

		if (polys.length < count) {
			// grow shared polygon array
			polys = new Polygon[count * 2];
			for (int i = 0; i < polys.length; i++) {
				int xx[] = new int[5];
				int yy[] = new int[5];
				Polygon p = new Polygon(xx, yy, 5);
				polys[i] = p;
			}
		}

		for (int i = 0; i < count; i++) {
			int intValueAt = ((Integer) model.getValueAt(i, bar
					.getPositionColumn())).intValue();

			int logicalOffset = intValueAt - model.getLowerBound();

			int baselineOffset = logicalOffsetToBaselineOffset(logicalOffset,
					model);
			int xx[] = polys[i].xpoints;
			int yy[] = polys[i].ypoints;

			if (bar.getOrientation() == SwingConstants.HORIZONTAL) {

				int x = baselineLeft + baselineOffset;
				if (bar.isFliped()) {
					xx[0] = x;
					yy[0] = baselineTop;

					xx[1] = x + MARKER_BODY_WIDTH / 2;
					yy[1] = baselineTop + MARKER_BICK_HEIGHT;

					xx[2] = x + MARKER_BODY_WIDTH / 2;
					yy[2] = baselineTop + MARKER_BICK_HEIGHT
							+ MARKER_BODY_HEIGHT;

					xx[3] = x - MARKER_BODY_WIDTH / 2;
					yy[3] = baselineTop + MARKER_BICK_HEIGHT
							+ MARKER_BODY_HEIGHT;

					xx[4] = x - MARKER_BODY_WIDTH / 2;
					yy[4] = baselineTop + MARKER_BICK_HEIGHT;

				} else {

					xx[0] = x;
					yy[0] = baselineTop;

					xx[1] = x + MARKER_BODY_WIDTH / 2;
					yy[1] = baselineTop - MARKER_BICK_HEIGHT;

					xx[2] = x + MARKER_BODY_WIDTH / 2;
					yy[2] = baselineTop - MARKER_BICK_HEIGHT
							- MARKER_BODY_HEIGHT;

					xx[3] = x - MARKER_BODY_WIDTH / 2;
					yy[3] = baselineTop - MARKER_BICK_HEIGHT
							- MARKER_BODY_HEIGHT;

					xx[4] = x - MARKER_BODY_WIDTH / 2;
					yy[4] = baselineTop - MARKER_BICK_HEIGHT;
				}

			} else {
				int y = baselineLeft + baselineOffset;

				if (bar.isFliped()) {
					xx[0] = baselineTop;
					yy[0] = y;

					yy[1] = y + MARKER_BODY_WIDTH / 2;
					xx[1] = baselineTop - MARKER_BICK_HEIGHT;

					yy[2] = y + MARKER_BODY_WIDTH / 2;
					xx[2] = baselineTop - MARKER_BICK_HEIGHT
							- MARKER_BODY_HEIGHT;

					yy[3] = y - MARKER_BODY_WIDTH / 2;
					xx[3] = baselineTop - MARKER_BICK_HEIGHT
							- MARKER_BODY_HEIGHT;

					yy[4] = y - MARKER_BODY_WIDTH / 2;
					xx[4] = baselineTop - MARKER_BICK_HEIGHT;
				} else {

					xx[0] = baselineTop;
					yy[0] = y;

					yy[1] = y + MARKER_BODY_WIDTH / 2;
					xx[1] = baselineTop + MARKER_BICK_HEIGHT;

					yy[2] = y + MARKER_BODY_WIDTH / 2;
					xx[2] = baselineTop + MARKER_BICK_HEIGHT
							+ MARKER_BODY_HEIGHT;

					yy[3] = y - MARKER_BODY_WIDTH / 2;
					xx[3] = baselineTop + MARKER_BICK_HEIGHT
							+ MARKER_BODY_HEIGHT;

					yy[4] = y - MARKER_BODY_WIDTH / 2;
					xx[4] = baselineTop + MARKER_BICK_HEIGHT;
				}

			}
			polys[i].invalidate();

		}
		return polys;
	}

	protected int logicalOffsetToBaselineOffset(int logicalOffset,
			BoundedTableModel model) {
		int positionRange = model.getUpperBound() - model.getLowerBound();
		return logicalOffset * baselineLength / positionRange;
	}

	protected int baselineOffsetToLogicalOffset(int baselineOffset,
			BoundedTableModel model) {
		int positionRange = model.getUpperBound() - model.getLowerBound();
		return baselineOffset * positionRange / baselineLength;
	}

	protected int componentOffsetToLogicalOffset(int componentOffsetLeft,
			BoundedTableModel dataModel) {
		return baselineOffsetToLogicalOffset(
				componentOffsetLeft - baselineLeft, dataModel);
	}

	protected void calculateViewRectAndBaseline(MarkerBar bar) {
		Insets insets = bar.getInsets();
		viewRect.x = insets.left;
		viewRect.y = insets.top;
		viewRect.width = bar.getWidth() - (insets.right + viewRect.x);
		viewRect.height = bar.getHeight() - (insets.bottom + viewRect.y);

		if (bar.getOrientation() == SwingConstants.HORIZONTAL) {
			baselineLeft = viewRect.x + MARKER_BODY_WIDTH / 2;
			baselineLength = viewRect.width - MARKER_BODY_WIDTH - 1;
			if (bar.isFliped()) {
				baselineTop = viewRect.y;
			} else {
				baselineTop = viewRect.y + viewRect.height - 1;
			}
		} else {
			baselineLeft = viewRect.y + MARKER_BODY_WIDTH / 2;
			baselineLength = viewRect.height - MARKER_BODY_WIDTH - 1;
			if (bar.isFliped()) {
				baselineTop = viewRect.x + viewRect.width - 1;
			} else {
				baselineTop = viewRect.x;
			}
		}
	}

	public int getMarkerSideGap() {
		return MARKER_BODY_WIDTH / 2;
	}

}
