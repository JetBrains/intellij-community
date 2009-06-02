package com.intellij.notification.impl.ui;

import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.impl.NotificationSettings;
import com.intellij.notification.impl.NotificationsConfiguration;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.tabs.BetterJTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

/**
 * @author spleaner
 */
public class NotificationsConfigurablePanel extends JPanel implements Disposable {
  private NotificationsTable myTable;
  private static final String REMOVE_KEY = "REMOVE";

  public NotificationsConfigurablePanel() {
    setLayout(new BorderLayout());
    myTable = new NotificationsTable();

    add(BetterJTable.createStripedJScrollPane(myTable), BorderLayout.CENTER);

    myTable.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), REMOVE_KEY);
    myTable.getActionMap().put(REMOVE_KEY, new AbstractAction() {
      public void actionPerformed(final ActionEvent e) {
        removeSelected();
      }
    });
  }

  private void removeSelected() {
    myTable.removeSelected();
  }

  public void dispose() {
    myTable = null;
  }

  public boolean isModified() {
    final List<SettingsWrapper> list = myTable.getAllSettings();
    for (SettingsWrapper settingsWrapper : list) {
      if (settingsWrapper.hasChanged()) {
        return true;
      }
    }

    return false;
  }

  public void apply() {
    final List<SettingsWrapper> list = myTable.getAllSettings();
    for (SettingsWrapper settingsWrapper : list) {
      settingsWrapper.apply();
    }
  }

  public void reset() {
    final List<SettingsWrapper> list = myTable.getAllSettings();
    for (SettingsWrapper settingsWrapper : list) {
      settingsWrapper.reset();
    }

    myTable.invalidate();
    myTable.repaint();
  }

  private static class NotificationsTable extends BetterJTable {
    private static final int ENABLED_COLUMN = 0;
    private static final int ID_COLUMN = 1;
    private static final int DISPLAY_TYPE_COLUMN = 2;

    public NotificationsTable() {
      super(new NotificationsTableModel());

      final List<String> displayTypes = new ArrayList<String>();
      final NotificationDisplayType[] notificationDisplayTypes = NotificationDisplayType.values();
      for (NotificationDisplayType displayType : notificationDisplayTypes) {
        displayTypes.add(StringUtil.capitalize(displayType.toString().toLowerCase()));
      }

      final int minOnWidth = new JCheckBox().getPreferredSize().width;
      getColumnModel().getColumn(ENABLED_COLUMN).setPreferredWidth(minOnWidth);
      getColumnModel().getColumn(ENABLED_COLUMN).setMinWidth(minOnWidth);
      getColumnModel().getColumn(ENABLED_COLUMN).setMaxWidth(minOnWidth);

      getColumnModel().getColumn(ID_COLUMN).setPreferredWidth(200);

      getColumnModel().getColumn(ENABLED_COLUMN).setCellRenderer(new CheckBoxCellRenderer());
      getColumnModel().getColumn(ENABLED_COLUMN).setCellEditor(new CheckBoxCellRenderer());

      getColumnModel().getColumn(DISPLAY_TYPE_COLUMN).setMaxWidth(300);
      getColumnModel().getColumn(DISPLAY_TYPE_COLUMN).setPreferredWidth(150);

      getColumnModel().getColumn(DISPLAY_TYPE_COLUMN).setCellEditor(
          new DefaultCellEditor(new JComboBox(displayTypes.toArray(new String[displayTypes.size()]))));
    }

    @Override
    public Dimension getMinimumSize() {
      return calcSize(super.getMinimumSize());
    }

    @Override
    public Dimension getPreferredSize() {
      return calcSize(super.getPreferredSize());
    }

    private Dimension calcSize(@NotNull final Dimension s) {
      final Container container = getParent();
      if (container != null) {
        final Dimension size = container.getSize();
        return new Dimension(size.width, s.height);
      }

      return s;
    }

    public List<SettingsWrapper> getSettings() {
      return ((NotificationsTableModel) getModel()).getSettings();
    }

    public void removeSelected() {
      final ListSelectionModel selectionModel = getSelectionModel();
      if (!selectionModel.isSelectionEmpty()) {
        final int min = selectionModel.getMinSelectionIndex();
        final int max = selectionModel.getMaxSelectionIndex();

        final List<SettingsWrapper> settings = getSettings();
        final List<SettingsWrapper> toRemove = new ArrayList<SettingsWrapper>();

        for (int i = min; i <= max; i++) {
          toRemove.add(settings.get(i));
        }

        if (toRemove.size() > 0) {
          ((NotificationsTableModel) getModel()).remove(toRemove);

          revalidate();
          repaint();
        }
      }
    }

    public List<SettingsWrapper> getAllSettings() {
      return ((NotificationsTableModel) getModel()).getAllSettings();
    }
  }

  private static class SettingsWrapper extends NotificationSettings {
    private NotificationSettings myOriginal;
    private boolean myRemoved = false;

    private SettingsWrapper(@NotNull final NotificationSettings original) {
      super(original.getComponentName(), original.getDisplayType(), original.isEnabled(), original.isCanDisable());
      myOriginal = original;
    }

    public boolean hasChanged() {
      return isEnabled() != myOriginal.isEnabled() || !getDisplayType().equals(myOriginal.getDisplayType()) || myRemoved;
    }

    public void remove() {
      myRemoved = true;
    }

    public boolean isRemoved() {
      return myRemoved;
    }

    public void apply() {
      if (myRemoved) {
        NotificationsConfiguration.remove(new NotificationSettings[] { myOriginal });
      } else {
        if (hasChanged()) {
          myOriginal.setEnabled(isEnabled());
          myOriginal.setDisplayType(getDisplayType());
        }
      }
    }

    public void reset() {
      if (hasChanged()) {
        setEnabled(myOriginal.isEnabled());
        setDisplayType(myOriginal.getDisplayType());
        myRemoved = false;
      }
    }
  }

  private static class NotificationsTableModel extends AbstractTableModel {
    private List<SettingsWrapper> mySettings;

    public NotificationsTableModel() {
      final NotificationSettings[] settings = NotificationsConfiguration.getAllSettings();
      final List<SettingsWrapper> list = new ArrayList<SettingsWrapper>();
      for (NotificationSettings setting : settings) {
        list.add(new SettingsWrapper(setting));
      }


      mySettings = list;
    }

    public List<SettingsWrapper> getSettings() {
      return getActiveSettings();
    }

    @Override
    public void setValueAt(final Object aValue, final int rowIndex, final int columnIndex) {
      final NotificationSettings settings = getSettings(rowIndex);

      switch (columnIndex) {
        case NotificationsTable.DISPLAY_TYPE_COLUMN:
          settings.setDisplayType(NotificationDisplayType.valueOf(((String)aValue).toUpperCase()));
          break;
      }
    }

    public int getRowCount() {
      return getSettings().size();
    }

    public NotificationSettings getSettings(int row) {
      return getSettings().get(row);
    }

    public int getColumnCount() {
      return 3;
    }

    @Override
    public Class<?> getColumnClass(final int columnIndex) {
      if (NotificationsTable.ENABLED_COLUMN == columnIndex) {
        return NotificationSettings.class;
      }

      return String.class;
    }

    @Override
    public String getColumnName(final int column) {
      switch (column) {
        case NotificationsTable.ENABLED_COLUMN:
          return "On";
        case NotificationsTable.ID_COLUMN:
          return "ID";
        default:
          return "Display type";
      }
    }

    @Override
    public boolean isCellEditable(final int rowIndex, final int columnIndex) {
      if (columnIndex == NotificationsTable.ENABLED_COLUMN) {
        return getSettings(rowIndex).isCanDisable();
      }

      return columnIndex == NotificationsTable.DISPLAY_TYPE_COLUMN;
    }

    public Object getValueAt(final int rowIndex, final int columnIndex) {
      switch (columnIndex) {
        case NotificationsTable.ENABLED_COLUMN:
          return getSettings().get(rowIndex);
        case NotificationsTable.ID_COLUMN:
          return getSettings().get(rowIndex).getComponentName();
      }

      return StringUtil.capitalize(getSettings().get(rowIndex).getDisplayType().toString().toLowerCase());
    }

    public static void remove(List<SettingsWrapper> toRemove) {
      for (SettingsWrapper settingsWrapper : toRemove) {
        settingsWrapper.remove();
      }
    }

    public List<SettingsWrapper> getAllSettings() {
      return mySettings;
    }

    private List<SettingsWrapper> getActiveSettings() {
      final List<SettingsWrapper> result = new ArrayList<SettingsWrapper>(mySettings);
      final Iterator<SettingsWrapper> iterator = result.iterator();
      while (iterator.hasNext()) {
        final SettingsWrapper wrapper = iterator.next();
        if (wrapper.isRemoved()) {
          iterator.remove();
        }
      }

      return result;
    }
  }

  private static class CheckBoxCellRenderer extends AbstractCellEditor implements TableCellRenderer, TableCellEditor, ActionListener {
    private JComponent myComponent;
    private JCheckBox myCheckBox;
    private Object myValue;

    private CheckBoxCellRenderer() {
      myComponent = new JPanel();
      myComponent.setLayout(new BoxLayout(myComponent, BoxLayout.X_AXIS));
      myComponent.setBorder(BorderFactory.createEmptyBorder());
      myCheckBox = new JCheckBox();
      myCheckBox.setBorder(BorderFactory.createEmptyBorder());
      myComponent.add(myCheckBox);

    }

    public Object getCellEditorValue() {
      return myValue;
    }

    public void actionPerformed(ActionEvent e) {
      stopCellEditing();
    }

    @Override
    public boolean stopCellEditing() {
      if (myValue instanceof NotificationSettings) {
        ((NotificationSettings)myValue).setEnabled(myCheckBox.isSelected());
      }

      return super.stopCellEditing();
    }

    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      myCheckBox.removeActionListener(this);
      myCheckBox.addActionListener(this);

      return getTableCellRendererComponent(table, value, isSelected, true, row, column);
    }

    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      myValue = value;

      myCheckBox.setEnabled(true);
      myCheckBox.setSelected(false);

      if (value instanceof NotificationSettings) {
        final NotificationSettings settings = (NotificationSettings)value;

        myCheckBox.setSelected(settings.isEnabled());
        myCheckBox.setEnabled(settings.isCanDisable());
      }

      myComponent.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());

      return myComponent;
    }
  }
}
