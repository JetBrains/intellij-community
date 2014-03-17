package com.michaelbaranov.microba.marker;

import java.util.ArrayList;
import java.util.List;

import javax.swing.ListSelectionModel;

import com.michaelbaranov.microba.common.AbstractBoundedTableModelWithSelection;

/**
 * A basic implementation of <code>BoundedRangeModel</code>,
 * <code>ListSelectionModel</code> and <code>MutationModel</code> all in
 * one. Used by default by <code>JMarkerBar</code> as data model, selection
 * model and mutation model.
 * <p>
 * 
 * 
 * @author Michael Baranov
 * @see com.michaelbaranov.microba.marker.MarkerBar
 */
public class DefaultMarkerModel extends AbstractBoundedTableModelWithSelection
		implements MarkerMutationModel {

	private List data = new ArrayList(32);

	private int lowerBound = 0;

	private int upperBound = 100;

	/**
	 * Constructs a <code>DefaultMarkerModel</code>.
	 * 
	 */
	public DefaultMarkerModel() {
		super();
		setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
	}

	/**
	 * Returns the count of markers. Actually returns the cound of rows in the
	 * <code>TableModel</code>.
	 * 
	 * @return current marker count.
	 */
	public int getRowCount() {
		return data.size();
	}

	/**
	 * Returns the numbar of columns in the <code>TableModel</code>.This
	 * implementation always returns 1.
	 * 
	 * @return always 1.
	 */
	public int getColumnCount() {
		return 1;
	}

	/**
	 * Returns the position of a marker, specified by index.
	 * 
	 * @param rowIndex
	 *            marker index.
	 * @param columnIndex
	 *            should be 0.
	 * 
	 * @return an Integer position of the marker.
	 * @see #setValueAt(Object, int, int)
	 */
	public Object getValueAt(int rowIndex, int columnIndex) {
		checkIndexes(rowIndex, columnIndex);
		return data.get(rowIndex);
	}

	/**
	 * Assigns a new position to a marker, specified by index (moves the
	 * marker).
	 * <p>
	 * This implementation does not check if the specified marker position lies
	 * beyond current upper and lower bounds.
	 * 
	 * @param aValue
	 *            an Integer new position.
	 * @param rowIndex
	 *            marker index.
	 * @param columnIndex
	 *            should be 0.
	 * @see #getValueAt(int, int)
	 */
	public void setValueAt(Object aValue, int rowIndex, int columnIndex) {

		checkIndexes(rowIndex, columnIndex);

		Integer newValue = (Integer) aValue;
		Integer oldValue = (Integer) getValueAt(rowIndex, columnIndex);

		if (!newValue.equals(oldValue)) {
			data.remove(rowIndex);
			data.add(rowIndex, newValue);
			fireTableCellUpdated(rowIndex, columnIndex);
		}
	}

	/**
	 * This implementation returns <code>Integer.class</code> for
	 * columnIndex=0.
	 * 
	 * @param columnIndex
	 *            should be 0.
	 * 
	 * @return column data class.
	 */
	public Class getColumnClass(int columnIndex) {
		return Integer.class;
	}

	/**
	 * This implementation returns <code>"Position"</code> for columnIndex=0.
	 * 
	 * @param columnIndex
	 *            should be 0.
	 * @return column name.
	 */
	public String getColumnName(int columnIndex) {
		return "Position";
	}

	/**
	 * This implementation always returns <code>true</code>, indicating that
	 * all markers are movable. Override to change behaviour.
	 * 
	 * @return always <code>true</code>.
	 */
	public boolean isCellEditable(int rowIndex, int columnIndex) {
		return true;
	}

	/**
	 * Removes a marker, specified by index. Actually removes a row from the
	 * <code>TableModel</code>.
	 * 
	 * @param index
	 *            marker index.
	 * @see #addMarkAtPosition(int)
	 */
	public void removeMarkerAtIndex(int index) {
		if (isSelectedIndex(index)) {
			removeSelectionInterval(index, index);
		}
		data.remove(index);
		fireTableRowsDeleted(index, index);
	}

	/**
	 * Adds a marker with specified position. Actually appends a row to
	 * <code>TableModel</code> with specified position value. Returns the
	 * index of the added mark.
	 * <p>
	 * This implementation does not check if the specified marker position lies
	 * beyond current upper and lower bounds.
	 * 
	 * @return index of added mark.
	 * @see #removeMarkerAtIndex(int)
	 */
	public int addMarkAtPosition(int position) {
		data.add(new Integer(position));
		int row = data.size() - 1;
		fireTableRowsInserted(row, row);

		setSelectionInterval(row, row);
		return row;
	}

	/**
	 * @inheritDoc
	 * @see #setLowerBound(int)
	 */
	public int getLowerBound() {
		return lowerBound;
	}

	/**
	 * @inheritDoc
	 * @see #setUpperBound(int)
	 */
	public int getUpperBound() {
		return upperBound;
	}

	/**
	 * Sets current lower bound.
	 * 
	 * @param lowerBound
	 *            new lower bound value.
	 * @see #setLowerBound(int)
	 */
	public void setLowerBound(int lowerBound) {
		if (lowerBound >= upperBound)
			throw new IllegalArgumentException("lowerBound >= upperBound");

		int old = this.lowerBound;
		this.lowerBound = lowerBound;
		firePropertyChange(PROPERTY_LOWER_BOUND, old, lowerBound);
	}

	/**
	 * Sets current upper bound.
	 * 
	 * @param upperBound
	 *            new upper bound value.
	 * @see #getUpperBound()
	 */
	public void setUpperBound(int upperBound) {
		int old = this.upperBound;
		this.upperBound = upperBound;
		firePropertyChange(PROPERTY_UPPER_BOUND, old, upperBound);
	}

	private void checkIndexes(int rowIndex, int columnIndex) {
		if (columnIndex != 0)
			throw new IndexOutOfBoundsException("columnIndex: " + columnIndex);

		if (rowIndex < 0 || rowIndex >= data.size())
			throw new IndexOutOfBoundsException("rowIndex: " + rowIndex);
	}

}
