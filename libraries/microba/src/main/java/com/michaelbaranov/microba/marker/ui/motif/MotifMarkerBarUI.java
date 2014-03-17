package com.michaelbaranov.microba.marker.ui.motif;

import java.awt.Graphics;
import java.awt.Rectangle;

import javax.swing.JComponent;
import javax.swing.plaf.ComponentUI;

import com.michaelbaranov.microba.marker.ui.basic.BasicMarkerBarUI;

public class MotifMarkerBarUI extends BasicMarkerBarUI {

	public static ComponentUI createUI(JComponent c) {
		return new MotifMarkerBarUI();
	}

	protected void drawFocusRect(Graphics g, Rectangle viewRect) {
		// focus painting is handled by the border
	}

}
