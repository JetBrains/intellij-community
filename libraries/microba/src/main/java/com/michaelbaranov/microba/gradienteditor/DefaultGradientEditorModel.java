package com.michaelbaranov.microba.gradienteditor;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ListSelectionModel;

import com.michaelbaranov.microba.common.AbstractBoundedTableModelWithSelection;
import com.michaelbaranov.microba.marker.MarkerMutationModel;

/**
 * A basic implementation of {@link AbstractBoundedTableModelWithSelection} and
 * {@link MarkerMutationModel}. Used by default by {@link GradientEditor} as a
 * color model, color selection model and color mutation model.
 * 
 * <p>
 * This implementation is mutable.
 * 
 * @author Michael Baranov
 * 
 */
public class DefaultGradientEditorModel extends
		AbstractBoundedTableModelWithSelection implements MarkerMutationModel {

	public static final int POSITION_COLUMN = 0;

	public static final int COLOR_COLUMN = 1;

	protected List position = new ArrayList(32);

	protected List color = new ArrayList(32);

	public DefaultGradientEditorModel() {
		super();
		setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		position.add(new Integer(0));
		color.add(Color.BLACK);

		position.add(new Integer(255));
		color.add(Color.WHITE);

	}

	public void removeMarkerAtIndex(int index) {
		if (isSelectedIndex(index)) {
			removeSelectionInterval(index, index);
		}
		position.remove(index);
		color.remove(index);
		fireTableRowsDeleted(index, index);

	}

	public int addMarkAtPosition(int pos) {
		position.add(new Integer(pos));
		color.add(new Color((float) Math.random(), (float) Math.random(),
				(float) Math.random()));
		int index = position.size() - 1;
		fireTableRowsInserted(index, index);
		return index;
	}

	public int getLowerBound() {
		return 0;
	}

	public int getUpperBound() {
		return 255;
	}

	public int getRowCount() {
		return position.size();
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
			return position.get(rowIndex);
		case COLOR_COLUMN:
			return color.get(rowIndex);
		}
		return null;
	}

	public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
		switch (columnIndex) {
		case POSITION_COLUMN:

			for (int i = 0; i < position.size(); i++)
				if (rowIndex != i && aValue.equals(position.get(i))) {
					return;
				}

			position.remove(rowIndex);
			position.add(rowIndex, aValue);
			fireTableCellUpdated(rowIndex, columnIndex);
			break;

		case COLOR_COLUMN:
			color.remove(rowIndex);
			color.add(rowIndex, (Color) aValue);
			fireTableCellUpdated(rowIndex, columnIndex);
			break;
		}
	}

	public boolean isCellEditable(int rowIndex, int columnIndex) {
		// protecting first 2 rows (first and last marker) from being moved
		return !(columnIndex == POSITION_COLUMN && rowIndex < 2);
	}
}
