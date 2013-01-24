package com.intellij.ui;

import com.intellij.ui.components.JBList;
import com.intellij.util.Function;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicRadioButtonUI;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;

/**
 * @author oleg
 */
public class CheckBoxList<T> extends JBList {
  private static final int DEFAULT_CHECK_BOX_WIDTH = 20;
  private CheckBoxListListener checkBoxListListener;
  private BidirectionalMap<Object, JCheckBox> myItemMap = new BidirectionalMap<Object, JCheckBox>();

  public CheckBoxList(final CheckBoxListListener checkBoxListListener) {
    this(new DefaultListModel(), checkBoxListListener);
  }
  public CheckBoxList(final DefaultListModel dataModel, final CheckBoxListListener checkBoxListListener) {
    this(dataModel);
    setCheckBoxListListener(checkBoxListListener);
  }

  public CheckBoxList() {
    this(new DefaultListModel());
  }

  public CheckBoxList(final DefaultListModel dataModel) {
    super();
    setModel(dataModel);
    setCellRenderer(new CellRenderer());
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    setBorder(BorderFactory.createEtchedBorder());
    addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped(KeyEvent e) {
        if (e.getKeyChar() == ' ') {
          int[] indices = CheckBoxList.this.getSelectedIndices();
          for (int index : indices) {
            if (index >= 0) {
              JCheckBox checkbox = (JCheckBox)getModel().getElementAt(index);
              setSelected(checkbox, index);
            }
          }
        }
      }
    });
    new ClickListener() {
      @Override
      public boolean onClick(MouseEvent e, int clickCount) {
        if (isEnabled()) {
          int index = locationToIndex(e.getPoint());

          if (index != -1) {
            JCheckBox checkbox = (JCheckBox)getModel().getElementAt(index);
            int iconArea;
            try {
              iconArea = checkbox.getMargin().left +
                         ((BasicRadioButtonUI)checkbox.getUI()).getDefaultIcon().getIconWidth() +
                         checkbox.getIconTextGap();
            }
            catch (ClassCastException c) {
              iconArea = DEFAULT_CHECK_BOX_WIDTH;
            }
            if (e.getX() < iconArea) {
              setSelected(checkbox, index);
              return true;
            }
          }
        }
        return false;
      }
    }.installOn(this);
  }

  public void setStringItems(final Map<String, Boolean> items) {
    clear();
    for (Map.Entry<String, Boolean> entry : items.entrySet()) {
      addItem(entry.getKey(), entry.getKey(), entry.getValue());
    }
  }

  public void setItems(final List<T> items, @Nullable Function<T, String> converter) {
    clear();
    for (T item : items) {
      String text = converter != null ? converter.fun(item) : item.toString();
      addItem(item, text, false);
    }
  }

  private void addItem(Object item, String text, boolean selected) {
    JCheckBox checkBox = new JCheckBox(text, selected);
    myItemMap.put(item, checkBox);
    ((DefaultListModel) getModel()).addElement(checkBox);
  }

  public Object getItemAt(int index) {
    JCheckBox checkBox = (JCheckBox)getModel().getElementAt(index);
    List<Object> value = myItemMap.getKeysByValue(checkBox);
    return value == null || value.isEmpty() ? null : value.get(0);
  }

  private void clear() {
    ((DefaultListModel) getModel()).clear();
    myItemMap.clear();
  }

  public boolean isItemSelected(int index) {
    return ((JCheckBox)getModel().getElementAt(index)).isSelected();  
  }

  public boolean isItemSelected(Object item) {
    JCheckBox checkBox = myItemMap.get(item);
    return checkBox != null && checkBox.isSelected();
  }

  public void setItemSelected(Object item, boolean selected) {
    JCheckBox checkBox = myItemMap.get(item);
    if (checkBox != null) {
      checkBox.setSelected(selected);
    }
  }

  private void setSelected(JCheckBox checkbox, int index) {
    boolean value = !checkbox.isSelected();
    checkbox.setSelected(value);
    repaint();

    // fire change notification in case if we've already initialized model
    final ListModel model = getModel();
    if (model instanceof DefaultListModel) {
      ((DefaultListModel)model).setElementAt(getModel().getElementAt(index), index);
    }

    if (checkBoxListListener != null) {
      checkBoxListListener.checkBoxSelectionChanged(index, value);
    }
  }

  public void setCheckBoxListListener(CheckBoxListListener checkBoxListListener) {
    this.checkBoxListListener = checkBoxListListener;
  }

  protected void adjustRendering(final JCheckBox checkBox, final boolean selected, final boolean hasFocus) {
  }

  private class CellRenderer implements ListCellRenderer {
    private final Border mySelectedBorder;
    private final Border myBorder;

    private CellRenderer() {
      mySelectedBorder = UIManager.getBorder("List.focusCellHighlightBorder");
      final Insets borderInsets = mySelectedBorder.getBorderInsets(new JCheckBox());
      myBorder = new EmptyBorder(borderInsets);
    }

    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      JCheckBox checkbox = (JCheckBox)value;
      if (!UIUtil.isUnderNimbusLookAndFeel()) {
        checkbox.setBackground(getBackground(isSelected, checkbox));
        checkbox.setForeground(getForeground(isSelected, checkbox));
      }
      checkbox.setEnabled(isEnabled());
      checkbox.setFont(getFont(checkbox));
      checkbox.setFocusPainted(false);
      checkbox.setBorderPainted(true);
      checkbox.setBorder(isSelected ? mySelectedBorder : myBorder);
      adjustRendering(checkbox, isSelected, cellHasFocus);
      return checkbox;
    }
  }

  protected Font getFont(final JCheckBox checkbox) {
    return getFont();
  }

  protected Color getBackground(final boolean isSelected, final JCheckBox checkbox) {
      return isSelected ? getSelectionBackground() : getBackground();
    }

  protected Color getForeground(final boolean isSelected, final JCheckBox checkbox) {
    return isSelected ? getSelectionForeground() : getForeground();
  }
}
