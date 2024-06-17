package com.michaelbaranov.microba.gradient;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import com.michaelbaranov.microba.common.AbstractBoundedTableModel;

/**
 * A very basic implementation of {@link AbstractBoundedTableModel} used by
 * default by {@link GradientBar}. This implementation has bounds 0 - 100 and
 * is mutable.
 * 
 * @author Michael Baranov
 * 
 */
public class DefaultGradientModel extends AbstractBoundedTableModel {

	protected static final int POSITION_COLUMN = 0;

	protected static final int COLOR_COLUMN = 1;

	protected List positionList = new ArrayList(32);

	protected List colorList = new ArrayList(32);

	/**
	 * Constructor.
	 */
	public DefaultGradientModel() {
		super();
		positionList.add(new Integer(0));
		colorList.add(Color.YELLOW);

		positionList.add(new Integer(50));
		colorList.add(Color.RED);

		positionList.add(new Integer(100));
		colorList.add(Color.GREEN);
	}

	public int getLowerBound() {
		return 0;
	}

	public int getUpperBound() {
		return 100;
	}

	public int getRowCount() {
		return positionList.size();
	}

	public int getColumnCount() {
		return 2;
	}

	public Class getColumnClass(int columnIndex) {
		switch (columnIndex) {
		case POSITION_COLUMN:
			return Integer.class;
		case COLOR_COLUMN:
			return Color.class;
		}
		return super.getColumnClass(columnIndex);
	}

	public Object getValueAt(int rowIndex, int columnIndex) {
		switch (columnIndex) {
		case POSITION_COLUMN:
			return positionList.get(rowIndex);
		case COLOR_COLUMN:
			return colorList.get(rowIndex);
		}
		return null;
	}

	/**
	 * Adds a color point.
	 * 
	 * @param color
	 * @param position
	 */
	public void add(Color color, int position) {
		colorList.add(color);
		positionList.add(new Integer(position));
		fireTableDataChanged();
	}

	/**
	 * Removes a color point at specified index.
	 * 
	 * @param index
	 */
	public void remove(int index) {
		colorList.remove(index);
		positionList.remove(index);
		fireTableDataChanged();
	}

	/**
	 * Removes all color points.
	 */
	public void clear() {
		colorList.clear();
		positionList.clear();
		fireTableDataChanged();
	}

}
