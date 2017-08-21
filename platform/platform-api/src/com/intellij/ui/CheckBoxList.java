/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.ui.components.JBList;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicRadioButtonUI;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.Map;

/**
 * @author oleg
 */
public class CheckBoxList<T> extends JBList<JCheckBox> {
  private final static int    RESET_ROLLOVER = -1;

  private final CellRenderer myCellRenderer;
  private CheckBoxListListener checkBoxListListener;
  private final BidirectionalMap<T, JCheckBox> myItemMap = new BidirectionalMap<>();
  private int rollOverIndex = RESET_ROLLOVER;

  public CheckBoxList(final CheckBoxListListener checkBoxListListener) {
    this(new DefaultListModel<>(), checkBoxListListener);
  }

  public CheckBoxList(DefaultListModel<JCheckBox> dataModel, CheckBoxListListener checkBoxListListener) {
    this(dataModel);
    setCheckBoxListListener(checkBoxListListener);
  }

  public CheckBoxList() {
    this(new DefaultListModel());
  }

  public CheckBoxList(final DefaultListModel dataModel) {
    super();
    //noinspection unchecked
    setModel(dataModel);
    myCellRenderer = new CellRenderer();
    setCellRenderer(myCellRenderer);
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped(KeyEvent e) {
        SpeedSearchSupply supply = SpeedSearchSupply.getSupply(CheckBoxList.this);
        if (supply != null && supply.isPopupActive()) {
          return;
        }
        if (e.getKeyChar() == ' ') {
          Boolean value = null;
          for (int index : getSelectedIndices()) {
            if (index >= 0) {
              JCheckBox checkbox = getCheckBoxAt(index);
              value = value != null ? value : !checkbox.isSelected();
              setSelected(checkbox, index, value);
            }
          }
        }
      }
    });
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        if (isEnabled()) {
          int index = locationToIndex(e.getPoint());
          if (index != -1) {
            JCheckBox checkBox = getCheckBoxAt(index);
            Rectangle bounds = getCellBounds(index, index);
            if (bounds == null) {
              return false;
            }
            Point p = findPointRelativeToCheckBox(e.getX() - bounds.x, e.getY() - bounds.y, checkBox, index);
            if (p != null) {
              Dimension dim = getCheckBoxDimension(checkBox);
              if (p.x >= 0 && p.x < dim.width && p.y >= 0 && p.y < dim.height) {
                setSelected(checkBox, index, !checkBox.isSelected());
                return true;
              }
            }
          }
        }
        return false;
      }
    }.installOn(this);

    if (UIUtil.isUnderWin10LookAndFeel()) {
      addMouseMotionListener(new MouseMotionAdapter() {
        @Override public void mouseMoved(MouseEvent e) {
          Point point = e.getPoint();
          int index = locationToIndex(point);
          fireRollOverUpdated(index);
        }
      });

      addMouseListener(new MouseAdapter() {
        @Override public void mouseExited(MouseEvent e) {
          fireRollOverUpdated(RESET_ROLLOVER);
        }

        @Override public void mousePressed(MouseEvent e) {
          setPressed(e, true);
        }

        @Override public void mouseReleased(MouseEvent e) {
          setPressed(e, false);
        }

        private void setPressed(MouseEvent e, boolean pressed) {
          Point point = e.getPoint();
          int index = locationToIndex(point);
          if (index >= 0 && index < getModel().getSize()) {
            JCheckBox cb = getModel().getElementAt(index);
            cb.getModel().setPressed(pressed);
            UIUtil.repaintViewport(CheckBoxList.this);
          }
        }
      });
    }
  }

  /**
   * Reset old rollover row and set new rollover row.
   * @param newIndex new rollover row. If newIndex is -1 then reset old rollover row only.
   */
  private void fireRollOverUpdated(int newIndex) {
    if (rollOverIndex >= 0) {
      JCheckBox oldRollover = getModel().getElementAt(rollOverIndex);
      oldRollover.getModel().setRollover(false);
    }

    rollOverIndex = newIndex;

    if (rollOverIndex >= 0) {
      JCheckBox newRollover = getModel().getElementAt(rollOverIndex);
      newRollover.getModel().setRollover(true);
    }
    UIUtil.repaintViewport(this);
  }

  @NotNull
  private static Dimension getCheckBoxDimension(@NotNull JCheckBox checkBox) {
    Icon icon = null;
    BasicRadioButtonUI ui = ObjectUtils.tryCast(checkBox.getUI(), BasicRadioButtonUI.class);
    if (ui != null) {
      icon = ui.getDefaultIcon();
    }
    if (icon == null) {
      // com.intellij.ide.ui.laf.darcula.ui.DarculaCheckBoxUI.getDefaultIcon()
      icon = JBUI.scale(EmptyIcon.create(20));
    }
    Insets margin = checkBox.getMargin();
    return new Dimension(margin.left + icon.getIconWidth(), margin.top + icon.getIconHeight());
  }

  /**
   * Find point relative to the checkbox. Performs lightweight calculations suitable for default rendering.

   * @param x x-coordinate relative to the rendered component
   * @param y y-coordinate relative to the rendered component
   * @param checkBox  JCheckBox instance
   * @param index     The list cell index
   * @return A point relative to the checkbox or null, if it's outside of the checkbox.
   */
  @Nullable
  protected Point findPointRelativeToCheckBox(int x, int y, @NotNull JCheckBox checkBox, int index) {
    int cx = x - myCellRenderer.getBorderInsets().left;
    int cy = y - myCellRenderer.getBorderInsets().top;
    return  cx >= 0 && cy >= 0 ? new Point(cx, cy) : null;
  }

  /**
   * Find point relative to the checkbox. Performs heavy calculations suitable for adjusted rendering
   * where the checkbox location can be arbitrary inside the rendered component.
   *
   * @param x x-coordinate relative to the rendered component
   * @param y y-coordinate relative to the rendered component
   * @param checkBox  JCheckBox instance
   * @param index     The list cell index
   * @return A point relative to the checkbox or null, if it's outside of the checkbox.
   */
  @Nullable
  protected Point findPointRelativeToCheckBoxWithAdjustedRendering(int x, int y, @NotNull JCheckBox checkBox, int index) {
    boolean selected = isSelectedIndex(index);
    boolean hasFocus = hasFocus();
    Component component = myCellRenderer.getListCellRendererComponent(this, checkBox, index, selected, hasFocus);
    Rectangle bounds = getCellBounds(index, index);
    bounds.x = 0;
    bounds.y = 0;
    component.setBounds(bounds);
    if (component instanceof Container) {
      Container c = (Container)component;
      Component found = c.findComponentAt(x, y);
      if (found == checkBox) {
        Point checkBoxLocation = getChildLocationRelativeToAncestor(component, checkBox);
        if (checkBoxLocation != null) {
          return new Point(x - checkBoxLocation.x, y - checkBoxLocation.y);
        }
      }
    }
    return null;
  }

  @Nullable
  private static Point getChildLocationRelativeToAncestor(@NotNull Component ancestor, @NotNull Component child) {
    int dx = 0, dy = 0;
    Component c = child;
    while (c != null && c != ancestor) {
      Point p = c.getLocation();
      dx += p.x;
      dy += p.y;
      c = child.getParent();
    }
    return c == ancestor ? new Point(dx, dy) : null;
  }


  @NotNull
  private JCheckBox getCheckBoxAt(int index) {
    return getModel().getElementAt(index);
  }

  public void setStringItems(final Map<String, Boolean> items) {
    clear();
    for (Map.Entry<String, Boolean> entry : items.entrySet()) {
      //noinspection unchecked
      addItem((T)entry.getKey(), entry.getKey(), entry.getValue());
    }
  }

  public void setItems(final List<T> items, @Nullable Function<T, String> converter) {
    clear();
    for (T item : items) {
      String text = converter != null ? converter.fun(item) : item.toString();
      addItem(item, text, false);
    }
  }

  public void addItem(T item, String text, boolean selected) {
    JCheckBox checkBox = new JCheckBox(text, selected);
    checkBox.setOpaque(true); // to paint selection background
    myItemMap.put(item, checkBox);
    //noinspection unchecked
    ((DefaultListModel)getModel()).addElement(checkBox);
  }

  public void updateItem(@NotNull T oldItem, @NotNull T newItem, @NotNull String newText) {
    JCheckBox checkBox = myItemMap.remove(oldItem);
    myItemMap.put(newItem, checkBox);
    checkBox.setText(newText);
    DefaultListModel<JCheckBox> model = (DefaultListModel<JCheckBox>)getModel();
    int ind = model.indexOf(checkBox);
    if (ind >= 0) {
      model.set(ind, checkBox); // to fire contentsChanged event
    }
  }

  @Nullable
  public T getItemAt(int index) {
    JCheckBox checkBox = getModel().getElementAt(index);
    List<T> value = myItemMap.getKeysByValue(checkBox);
    return value == null || value.isEmpty() ? null : value.get(0);
  }

  public void clear() {
    ((DefaultListModel)getModel()).clear();
    myItemMap.clear();
  }

  public boolean isItemSelected(int index) {
    return getModel().getElementAt(index).isSelected();
  }

  public boolean isItemSelected(T item) {
    JCheckBox checkBox = myItemMap.get(item);
    return checkBox != null && checkBox.isSelected();
  }

  public void setItemSelected(T item, boolean selected) {
    JCheckBox checkBox = myItemMap.get(item);
    if (checkBox != null) {
      checkBox.setSelected(selected);
    }
  }

  private void setSelected(JCheckBox checkbox, int index, boolean value) {
    checkbox.setSelected(value);
    repaint();

    // fire change notification in case if we've already initialized model
    final ListModel model = getModel();
    if (model instanceof DefaultListModel) {
      //noinspection unchecked
      ((DefaultListModel)model).setElementAt(getModel().getElementAt(index), index);
    }

    if (checkBoxListListener != null) {
      checkBoxListListener.checkBoxSelectionChanged(index, value);
    }
  }

  public void setCheckBoxListListener(CheckBoxListListener checkBoxListListener) {
    this.checkBoxListListener = checkBoxListListener;
  }

  protected JComponent adjustRendering(JComponent rootComponent,
                                       final JCheckBox checkBox,
                                       int index,
                                       final boolean selected,
                                       final boolean hasFocus) {
    return rootComponent;
  }

  private class CellRenderer implements ListCellRenderer<JCheckBox> {
    private final Border mySelectedBorder;
    private final Border myBorder;
    private final Insets myBorderInsets;

    private CellRenderer() {
      mySelectedBorder = UIManager.getBorder("List.focusCellHighlightBorder");
      myBorderInsets = mySelectedBorder.getBorderInsets(new JCheckBox());
      myBorder = new EmptyBorder(myBorderInsets);
    }

    @Override
    public Component getListCellRendererComponent(JList list, JCheckBox checkbox, int index, boolean isSelected, boolean cellHasFocus) {
      Color textColor = getForeground(isSelected);
      Color backgroundColor = getBackground(isSelected);
      Font font = getFont();

      boolean shouldAdjustColors = !UIUtil.isUnderNimbusLookAndFeel();

      if (shouldAdjustColors) {
        checkbox.setBackground(backgroundColor);
        checkbox.setForeground(textColor);
      }

      checkbox.setEnabled(isEnabled());
      checkbox.setFont(font);
      checkbox.setFocusPainted(false);
      checkbox.setBorderPainted(false);
      checkbox.setOpaque(true);
      String auxText = getSecondaryText(index);

      JComponent rootComponent;
      if (auxText != null) {
        JPanel panel = new JPanel(new BorderLayout());

        panel.add(checkbox, BorderLayout.LINE_START);

        JLabel infoLabel = new JLabel(auxText, SwingConstants.RIGHT);
        infoLabel.setBorder(new EmptyBorder(0, 0, 0, checkbox.getInsets().left));
        infoLabel.setFont(UIUtil.getFont(UIUtil.FontSize.SMALL, font));
        panel.add(infoLabel, BorderLayout.CENTER);

        if (shouldAdjustColors) {
          panel.setBackground(backgroundColor);
          infoLabel.setForeground(isSelected ? textColor : JBColor.GRAY);
          infoLabel.setBackground(backgroundColor);
        }

        rootComponent = panel;
      }
      else {
        rootComponent = checkbox;
      }

      rootComponent.setBorder(isSelected ? mySelectedBorder : myBorder);

      boolean isRollOver = checkbox.getModel().isRollover();
      rootComponent = adjustRendering(rootComponent, checkbox, index, isSelected, cellHasFocus);
      checkbox.getModel().setRollover(isRollOver);

      return rootComponent;
    }

    @NotNull
    private Insets getBorderInsets() {
      return myBorderInsets;
    }
  }

  @Nullable
  protected String getSecondaryText(int index) {
    return null;
  }

  protected Color getBackground(final boolean isSelected) {
    return isSelected ? getSelectionBackground() : getBackground();
  }

  protected Color getForeground(final boolean isSelected) {
    return isSelected ? getSelectionForeground() : getForeground();
  }
}
