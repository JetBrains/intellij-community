package com.michaelbaranov.microba.gradienteditor;

import com.michaelbaranov.microba.common.AbstractBoundedTableModelWithSelection;
import com.michaelbaranov.microba.marker.MarkerMutationModel;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

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

  protected List<Integer> position = new ArrayList<>(32);

  protected List<Color> color = new ArrayList<>(32);

  public DefaultGradientEditorModel() {
    super();
    setSelectionMode(SINGLE_SELECTION);

    position.add(Integer.valueOf(0));
    color.add(Color.BLACK);

    position.add(Integer.valueOf(255));
    color.add(Color.WHITE);

  }

  @Override
  public void removeMarkerAtIndex(int index) {
    if (isSelectedIndex(index)) {
      removeSelectionInterval(index, index);
    }
    position.remove(index);
    color.remove(index);
    fireTableRowsDeleted(index, index);

  }

  @Override
  public int addMarkAtPosition(int pos) {
    position.add(Integer.valueOf(pos));
    color.add(new Color((float) Math.random(), (float) Math.random(),
        (float) Math.random()));
    int index = position.size() - 1;
    fireTableRowsInserted(index, index);
    return index;
  }

  @Override
  public int getLowerBound() {
    return 0;
  }

  @Override
  public int getUpperBound() {
    return 255;
  }

  @Override
  public int getRowCount() {
    return position.size();
  }

  @Override
  public int getColumnCount() {
    return 2;
  }

  @Override
  public Class getColumnClass(int columnIndex) {
    return switch (columnIndex) {
      case POSITION_COLUMN -> Integer.class;
      case COLOR_COLUMN -> Color.class;
      default -> super.getColumnClass(columnIndex);
    };
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    return switch (columnIndex) {
      case POSITION_COLUMN -> position.get(rowIndex);
      case COLOR_COLUMN -> color.get(rowIndex);
      default -> null;
    };
  }

  @Override
  public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
    switch (columnIndex) {
    case POSITION_COLUMN:

      for (int i = 0; i < position.size(); i++)
        if (rowIndex != i && aValue.equals(position.get(i))) {
          return;
        }

      position.remove(rowIndex);
      position.add(rowIndex, (Integer)aValue);
      fireTableCellUpdated(rowIndex, columnIndex);
      break;

    case COLOR_COLUMN:
      color.remove(rowIndex);
      color.add(rowIndex, (Color)aValue);
      fireTableCellUpdated(rowIndex, columnIndex);
      break;
    }
  }

  @Override
  public boolean isCellEditable(int rowIndex, int columnIndex) {
    // protecting first 2 rows (first and last marker) from being moved
    return !(columnIndex == POSITION_COLUMN && rowIndex < 2);
  }
}
