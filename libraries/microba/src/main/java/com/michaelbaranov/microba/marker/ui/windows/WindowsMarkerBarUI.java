package com.michaelbaranov.microba.marker.ui.windows;

import java.awt.Graphics;
import java.awt.Rectangle;

import javax.swing.JComponent;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicGraphicsUtils;

import com.michaelbaranov.microba.marker.MarkerBar;
import com.michaelbaranov.microba.marker.ui.basic.BasicMarkerBarUI;

public class WindowsMarkerBarUI extends BasicMarkerBarUI {

	private int dashedRectGapX;

	private int dashedRectGapY;

	private int dashedRectGapWidth;

	private int dashedRectGapHeight;

	private boolean defaults_initialized;

	public static ComponentUI createUI(JComponent c) {
		return new WindowsMarkerBarUI();
	}

	protected void drawFocusRect(Graphics g, Rectangle viewRect) {
		g.setColor(getFocusColor());

		g.setColor(getFocusColor());
		// BasicGraphicsUtils.drawDashedRect(g, dashedRectGapX, dashedRectGapY,
		// viewRect.width - dashedRectGapWidth, viewRect.height
		// - dashedRectGapHeight);
		BasicGraphicsUtils.drawDashedRect(g, viewRect.x, viewRect.y, viewRect.width - 1,
				viewRect.height - 1);
	}

	protected void installDefaults(MarkerBar b) {
		super.installDefaults(b);
		// if(!defaults_initialized) {
		dashedRectGapX = UIManager.getInt("Button.dashedRectGapX");
		dashedRectGapY = UIManager.getInt("Button.dashedRectGapY");
		dashedRectGapWidth = UIManager.getInt("Button.dashedRectGapWidth");
		dashedRectGapHeight = UIManager.getInt("Button.dashedRectGapHeight");
		// focusColor = UIManager.getColor(pp + "focus");
		defaults_initialized = true;
	}
}
