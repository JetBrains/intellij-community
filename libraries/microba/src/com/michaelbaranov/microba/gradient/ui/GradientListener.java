package com.michaelbaranov.microba.gradient.ui;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import com.michaelbaranov.microba.common.BoundedTableModel;
import com.michaelbaranov.microba.gradient.GradientBar;
import com.michaelbaranov.microba.gradient.ui.basic.BasicGradientUI;

public class GradientListener implements TableModelListener,
		PropertyChangeListener {

	private final BasicGradientUI gradientUI;

	private GradientBar gradient;

	public GradientListener(BasicGradientUI gradientUI, GradientBar gradient) {
		this.gradientUI = gradientUI;
		this.gradient = gradient;
	}

	public void propertyChange(PropertyChangeEvent evt) {
		if (GradientBar.PROPERTY_DATA_MODEL.equals(evt.getPropertyName())) {
			BoundedTableModel oldModel = (BoundedTableModel) evt.getOldValue();
			BoundedTableModel newModel = (BoundedTableModel) evt.getNewValue();

			if (oldModel != null)
				oldModel.removeTableModelListener(this);
			if (newModel != null)
				newModel.addTableModelListener(this);

			gradient.revalidate();
		}

		if (GradientBar.PROPERTY_ORIENTATION.equals(evt.getPropertyName())) {
			gradient.revalidate();
		}
		if (GradientBar.PROPERTY_COLOR_POSITION_COLUMN.equals(evt.getPropertyName())) {
			gradient.repaint();
		}
		if (GradientBar.PROPERTY_COLOR_COLUMN.equals(evt.getPropertyName())) {
			gradient.repaint();
		}
		if ("enabled".equals(evt.getPropertyName())) {
			gradient.repaint();
		}
	}

	public void tableChanged(TableModelEvent e) {
		gradient.repaint();

	}
}