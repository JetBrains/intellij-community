package com.michaelbaranov.microba.common;

import javax.swing.table.TableModel;
import java.beans.PropertyChangeListener;

/**
 * An extended <code>TableModel</code>.
 * <p>
 * The upper and lower bound values are introduced to further describe table
 * data. For example, <code>BoundedTableModel</code> is used as a data model
 * for <code>{@link com.michaelbaranov.microba.marker.MarkerBar}</code>
 * 
 * @version 0.1 (rev. 13 Aug 2005)
 * @author Michael Baranov <a
 *         href=http://www.michaelbaranov.com>www.michaelbaranov.com</a>
 * 
 */
public interface BoundedTableModel extends TableModel {

  /**
   * The name of the bound property, that holds lower bound value.
   */
  String PROPERTY_LOWER_BOUND = "lowerBound";

  /**
   * The name of the bound property, that holds upper bound value.
   */
  String PROPERTY_UPPER_BOUND = "upperBound";

  /**
   * Returns some lower bound, further describing the data.
   * 
   * @return lower bound.
   */
  int getLowerBound();

  /**
   * Returns some upper bound, further describing the data.
   * 
   * @return upper bound.
   */
  int getUpperBound();

  void addPropertyChangeListener(PropertyChangeListener listener);

  void addPropertyChangeListener(String propertyName,
                                 PropertyChangeListener listener);

  void removePropertyChangeListener(PropertyChangeListener listener);

  void removePropertyChangeListener(String propertyName,
                                    PropertyChangeListener listener);

}
