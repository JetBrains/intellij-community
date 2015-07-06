package com.intellij.ui;

import com.intellij.ui.components.JBList;
import com.intellij.util.Function;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
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
  private final BidirectionalMap<T, JCheckBox> myItemMap = new BidirectionalMap<T, JCheckBox>();

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
    //noinspection unchecked
    setModel(dataModel);
    setCellRenderer(new CellRenderer());
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    setBorder(BorderFactory.createEtchedBorder());
    addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped(KeyEvent e) {
        if (e.getKeyChar() == ' ') {
          for (int index : getSelectedIndices()) {
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
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
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
    myItemMap.put(item, checkBox);
    //noinspection unchecked
    ((DefaultListModel) getModel()).addElement(checkBox);
  }

  public void updateItem(@NotNull T oldItem, @NotNull T newItem) {
    JCheckBox checkBox = myItemMap.remove(oldItem);
    myItemMap.put(newItem, checkBox);
  }

  @Nullable
  public T getItemAt(int index) {
    JCheckBox checkBox = (JCheckBox)getModel().getElementAt(index);
    List<T> value = myItemMap.getKeysByValue(checkBox);
    return value == null || value.isEmpty() ? null : value.get(0);
  }

  public void clear() {
    ((DefaultListModel) getModel()).clear();
    myItemMap.clear();
  }

  public boolean isItemSelected(int index) {
    return ((JCheckBox)getModel().getElementAt(index)).isSelected();
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

  private void setSelected(JCheckBox checkbox, int index) {
    boolean value = !checkbox.isSelected();
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

  protected void adjustRendering(final JCheckBox checkBox, int index, final boolean selected, final boolean hasFocus) {
  }

  private class CellRenderer implements ListCellRenderer {
    private final Border mySelectedBorder;
    private final Border myBorder;

    private CellRenderer() {
      mySelectedBorder = UIManager.getBorder("List.focusCellHighlightBorder");
      final Insets borderInsets = mySelectedBorder.getBorderInsets(new JCheckBox());
      myBorder = new EmptyBorder(borderInsets);
    }

    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      JCheckBox checkbox = (JCheckBox)value;

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

      String auxText = getSecondaryText(index);

      JComponent rootComponent;
      if (auxText != null) {
        JPanel panel = new JPanel(new BorderLayout());

        checkbox.setBorderPainted(false);
        panel.add(checkbox, BorderLayout.LINE_START);

        JLabel infoLabel = new JLabel(auxText, SwingConstants.RIGHT);
        infoLabel.setBorder(new EmptyBorder(0, 0, 0, checkbox.getInsets().left));
        infoLabel.setFont(font);
        panel.add(infoLabel, BorderLayout.CENTER);

        if (shouldAdjustColors) {
          panel.setBackground(backgroundColor);
          infoLabel.setForeground(isSelected ? textColor : JBColor.GRAY);
          infoLabel.setBackground(backgroundColor);
        }

        rootComponent = panel;
      }
      else {
        checkbox.setBorderPainted(true);
        rootComponent = checkbox;
      }

      rootComponent.setBorder(isSelected ? mySelectedBorder : myBorder);

      adjustRendering(checkbox, index, isSelected, cellHasFocus);
      return rootComponent;
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
