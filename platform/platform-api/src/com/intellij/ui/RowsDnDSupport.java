/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui;

import com.intellij.ide.dnd.*;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.util.Function;
import com.intellij.util.ui.EditableModel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static com.intellij.ui.RowsDnDSupport.RefinedDropSupport.Position.*;

/**
 * @author Konstantin Bulenkov
 */
public class RowsDnDSupport {
  private RowsDnDSupport() {
  }

  public static void install(@NotNull final JTable table, @NotNull final EditableModel model) {
    table.setDragEnabled(true);
    installImpl(table, model);
  }

  public static void install(@NotNull final JList list, @NotNull final EditableModel model) {
    list.setDragEnabled(true);
    installImpl(list, model);
  }

  public static void install(@NotNull final JTree tree, @NotNull final EditableModel model) {
    tree.setDragEnabled(true);
    installImpl(tree, model);
  }

  private static void installImpl(@NotNull final JComponent component, @NotNull final EditableModel model) {
    component.setTransferHandler(new TransferHandler(null));
    DnDSupport.createBuilder(component)
      .setBeanProvider(new Function<DnDActionInfo, DnDDragStartBean>() {
        @Override
        public DnDDragStartBean fun(DnDActionInfo info) {
          final Point p = info.getPoint();
          return new DnDDragStartBean(new RowDragInfo(component, Integer.valueOf(getRow(component, p))));
        }
      })
      .setTargetChecker(new DnDTargetChecker() {
        @Override
        public boolean update(DnDEvent event) {
          final Object o = event.getAttachedObject();
          if (! (o instanceof RowDragInfo) || ((RowDragInfo)o).component != component) {
            event.setDropPossible(false, "");
            return true;
          }
          event.setDropPossible(true);
          int oldIndex = ((RowDragInfo)o).row;
          int newIndex = getRow(component, event.getPoint());

          if (newIndex == -1) {
            event.setDropPossible(false, "");
            return true;
          }

          Rectangle cellBounds = getCellBounds(component, newIndex);
          if (model instanceof RefinedDropSupport) {
            RefinedDropSupport.Position position = ((RefinedDropSupport)model).isDropInto(component, oldIndex, newIndex)
                                                   ? INTO
                                                   : (event.getPoint().y < cellBounds.y + cellBounds.height / 2)
                                                     ? ABOVE
                                                     : BELOW;
            boolean canDrop = ((RefinedDropSupport)model).canDrop(oldIndex, newIndex, position);
            event.setDropPossible(canDrop);
            if (canDrop && oldIndex != newIndex) {
              if (position == BELOW) {
                cellBounds.y += cellBounds.height - 2;
              }
              RelativeRectangle rectangle = new RelativeRectangle(component, cellBounds);
              switch (position) {
                case INTO:
                  event.setHighlighting(rectangle, DnDEvent.DropTargetHighlightingType.RECTANGLE);
                  break;
                case ABOVE:
                case BELOW:
                  rectangle.getDimension().height = 2;
                  event.setHighlighting(rectangle, DnDEvent.DropTargetHighlightingType.FILLED_RECTANGLE);
                  break;
              }
              return true;
            }
            else {
              event.hideHighlighter();
              return true;
            }
          }
          else {

            if (oldIndex == newIndex) {  // Drag&Drop always starts with new==old and we shouldn't display 'rejecting' cursor in this case
              return true;
            }

            boolean canExchange = model.canExchangeRows(oldIndex, newIndex);
            if (canExchange) {
              if (oldIndex < newIndex) {
                cellBounds.y += cellBounds.height - 2;
              }
              RelativeRectangle rectangle = new RelativeRectangle(component, cellBounds);
              rectangle.getDimension().height = 2;
              event.setDropPossible(true);
              event.setHighlighting(rectangle, DnDEvent.DropTargetHighlightingType.FILLED_RECTANGLE);
            }
            else {
              event.setDropPossible(false);
            }
            return true;
          }
        }
      })
      .setDropHandler(new DnDDropHandler() {
        @Override
        public void drop(DnDEvent event) {
          final Object o = event.getAttachedObject();
          final Point p = event.getPoint();
          if (o instanceof RowDragInfo && ((RowDragInfo)o).component == component) {
            int oldIndex = ((RowDragInfo)o).row;
            if (oldIndex == -1) return;
            int newIndex = getRow(component, p);
            if (newIndex == -1) {
              newIndex = getRowCount(component) - 1;
            }

            if (oldIndex != newIndex) {
              if (model instanceof RefinedDropSupport) {
                Rectangle cellBounds = getCellBounds(component, newIndex);
                RefinedDropSupport.Position position = ((RefinedDropSupport)model).isDropInto(component, oldIndex, newIndex)
                                                       ? INTO
                                                       : (event.getPoint().y < cellBounds.y + cellBounds.height / 2)
                                                         ? ABOVE
                                                         : BELOW;
                if (((RefinedDropSupport)model).canDrop(oldIndex, newIndex, position)) {
                  ((RefinedDropSupport)model).drop(oldIndex, newIndex, position);
                }
              }
              else {
                if (model.canExchangeRows(oldIndex, newIndex)) {
                  model.exchangeRows(oldIndex, newIndex);
                  setSelectedRow(component, newIndex);
                }
              }
            }
          }
          event.hideHighlighter();
        }
      })
      .install();
  }

  private static int getRow(JComponent component, Point point) {
    if (component instanceof JTable) {
      return ((JTable)component).rowAtPoint(point);
    }
    else if (component instanceof JList) {
      return ((JList)component).locationToIndex(point);
    }
    else if (component instanceof JTree) {
      return ((JTree)component).getClosestRowForLocation(point.x, point.y);
    }
    else {
      throw new IllegalArgumentException("Unsupported component: " + component);
    }
  }

  private static int getRowCount(JComponent component) {
    if (component instanceof JTable) {
      return ((JTable)component).getRowCount();
    }
    else if (component instanceof JList) {
      return ((JList)component).getModel().getSize();
    }
    else if (component instanceof JTree) {
      return ((JTree)component).getRowCount();
    }
    else {
      throw new IllegalArgumentException("Unsupported component: " + component);
    }
  }

  private static Rectangle getCellBounds(JComponent component, int row) {
    if (component instanceof JTable) {
      Rectangle rectangle = ((JTable)component).getCellRect(row, 0, true);
      rectangle.width = component.getWidth();
      return rectangle;
    }
    else if (component instanceof JList) {
      return ((JList)component).getCellBounds(row, row);
    }
    else if (component instanceof JTree) {
      return ((JTree)component).getRowBounds(row);
    }
    else {
      throw new IllegalArgumentException("Unsupported component: " + component);
    }
  }

  private static void setSelectedRow(JComponent component, int row) {
    if (component instanceof JTable) {
      ((JTable)component).getSelectionModel().setSelectionInterval(row, row);
    }
    else if (component instanceof JList) {
      ((JList)component).setSelectedIndex(row);
    }
    else if (component instanceof JTree) {
      ((JTree)component).setSelectionRow(row);
    }
    else {
      throw new IllegalArgumentException("Unsupported component: " + component);
    }
  }

  private static class RowDragInfo {
    public final JComponent component;
    public final int row;

    RowDragInfo(JComponent component, int row) {
      this.component = component;
      this.row = row;
    }
  }

  public interface RefinedDropSupport {
    enum Position {ABOVE, INTO, BELOW}

    boolean isDropInto(JComponent component, int oldIndex, int newIndex);
    //oldIndex may be equal to newIndex
    boolean canDrop(int oldIndex, int newIndex, @NotNull Position position);

    //This method is also responsible for selection changing
    void drop(int oldIndex, int newIndex, @NotNull Position position);
  }
}
