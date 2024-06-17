package com.michaelbaranov.microba.marker.ui.metal;

import java.awt.Graphics;
import java.awt.Rectangle;

import javax.swing.JComponent;
import javax.swing.plaf.ComponentUI;

import com.michaelbaranov.microba.marker.ui.basic.BasicMarkerBarUI;

public class MetalMarkerBarUI extends BasicMarkerBarUI {

	public static ComponentUI createUI(JComponent c) {
		return new MetalMarkerBarUI();
	}

	protected void drawFocusRect(Graphics g, Rectangle viewRect) {
		g.setColor(getFocusColor());

		g.drawRect(viewRect.x, viewRect.y, viewRect.width - 1, viewRect.height - 1);
	}

}
