package com.michaelbaranov.microba.gradient;

import com.michaelbaranov.microba.common.AbstractBoundedTableModel;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

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

  protected List<Integer> positionList = new ArrayList<>(32);

  protected List<Color> colorList = new ArrayList<>(32);

  /**
   * Constructor.
   */
  public DefaultGradientModel() {
    super();
    positionList.add(Integer.valueOf(0));
    colorList.add(Color.YELLOW);

    positionList.add(Integer.valueOf(50));
    colorList.add(Color.RED);

    positionList.add(Integer.valueOf(100));
    colorList.add(Color.GREEN);
  }

  @Override
  public int getLowerBound() {
    return 0;
  }

  @Override
  public int getUpperBound() {
    return 100;
  }

  @Override
  public int getRowCount() {
    return positionList.size();
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
      case POSITION_COLUMN -> positionList.get(rowIndex);
      case COLOR_COLUMN -> colorList.get(rowIndex);
      default -> null;
    };
  }

  /**
   * Adds a color point.
   */
  public void add(Color color, int position) {
    colorList.add(color);
    positionList.add(Integer.valueOf(position));
    fireTableDataChanged();
  }

  /**
   * Removes a color point at specified index.
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
