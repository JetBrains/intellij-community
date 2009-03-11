package com.intellij.notification.impl.ui;

import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.impl.NotificationSettings;
import com.intellij.notification.impl.NotificationsConfiguration;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author spleaner
 */
public class NotificationsConfigurablePanel extends JPanel implements Disposable {
  private NotificationsTable myTable;

  public NotificationsConfigurablePanel() {
    setLayout(new BorderLayout());

    final NotificationSettings[] settings = NotificationsConfiguration.getAllSettings();

    myTable = new NotificationsTable(settings);

    final JScrollPane pane = new JScrollPane(myTable);
    add(pane);

//    myTable.getColumnModel().getColumn(NotificationsTable.ENABLED_COLUMN).setWidth(20);
  }

  public void dispose() {
    myTable = null;
  }

  public boolean isModified() {
    final List<SettingsWrapper> list = myTable.getSettings();
    for (SettingsWrapper settingsWrapper : list) {
      if (settingsWrapper.hasChanged()) {
        return true;
      }
    }

    return false;
  }

  public void apply() {
    final List<SettingsWrapper> list = myTable.getSettings();
    for (SettingsWrapper settingsWrapper : list) {
      settingsWrapper.apply();
    }
  }

  public void reset() {
    final List<SettingsWrapper> list = myTable.getSettings();
    for (SettingsWrapper settingsWrapper : list) {
      settingsWrapper.reset();
    }

    myTable.invalidate();
    myTable.repaint();
  }

  private static class NotificationsTable extends JTable {
    private static final int ENABLED_COLUMN = 0;
    private static final int DISPLAY_TYPE_COLUMN = 2;

    public NotificationsTable(final NotificationSettings[] settings) {
      super(new NotificationsTableModel(settings));

      final List<String> displayTypes = new ArrayList<String>();
      final NotificationDisplayType[] notificationDisplayTypes = NotificationDisplayType.values();
      for (NotificationDisplayType displayType : notificationDisplayTypes) {
        displayTypes.add(StringUtil.capitalize(displayType.toString().toLowerCase()));
      }

      getColumnModel().getColumn(DISPLAY_TYPE_COLUMN).setCellEditor(
          new DefaultCellEditor(new JComboBox(displayTypes.toArray(new String[displayTypes.size()]))));
    }

    public List<SettingsWrapper> getSettings() {
      return ((NotificationsTableModel) getModel()).getSettings();
    }
  }

  private static class SettingsWrapper extends NotificationSettings {
    private NotificationSettings myOriginal;

    private SettingsWrapper(@NotNull final NotificationSettings original) {
      super(original.getComponentName(), original.getDisplayType(), original.isEnabled(), original.isCanDisable());
      myOriginal = original;
    }

    public boolean hasChanged() {
      return isEnabled() != myOriginal.isEnabled() || !getDisplayType().equals(myOriginal.getDisplayType());
    }

    public void apply() {
      if (hasChanged()) {
        myOriginal.setEnabled(isEnabled());
        myOriginal.setDisplayType(getDisplayType());
      }
    }

    public void reset() {
      if (hasChanged()) {
        setEnabled(myOriginal.isEnabled());
        setDisplayType(myOriginal.getDisplayType());
      }
    }
  }

  private static class NotificationsTableModel extends AbstractTableModel {
    private List<SettingsWrapper> mySettings;

    public NotificationsTableModel(final NotificationSettings[] settings) {
      final List<SettingsWrapper> list = new ArrayList<SettingsWrapper>();
      for (NotificationSettings setting : settings) {
        list.add(new SettingsWrapper(setting));
      }


      mySettings = list;
    }

    public List<SettingsWrapper> getSettings() {
      return mySettings;
    }

    @Override
    public void setValueAt(final Object aValue, final int rowIndex, final int columnIndex) {
      final NotificationSettings settings = getSettings(rowIndex);

      switch (columnIndex) {
        case NotificationsTable.ENABLED_COLUMN:
          settings.setEnabled(((Boolean)aValue).booleanValue());
          break;
        case NotificationsTable.DISPLAY_TYPE_COLUMN:
          settings.setDisplayType(NotificationDisplayType.valueOf(((String)aValue).toUpperCase()));
          break;
      }
    }

    public int getRowCount() {
      return mySettings.size();
    }

    public NotificationSettings getSettings(int row) {
      return mySettings.get(row);
    }

    public int getColumnCount() {
      return 3;
    }

    @Override
    public Class<?> getColumnClass(final int columnIndex) {
      if (NotificationsTable.ENABLED_COLUMN == columnIndex) {
        return Boolean.class;
      }

      return String.class;
    }

    @Override
    public String getColumnName(final int column) {
      switch (column) {
        case NotificationsTable.ENABLED_COLUMN:
          return "Enabled";
        case 1:
          return "Component";
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
      final NotificationSettings settings = mySettings.get(rowIndex);

      switch (columnIndex) {
        case NotificationsTable.ENABLED_COLUMN:
          return settings.isEnabled();
        case 1:
          return settings.getComponentName();
        case NotificationsTable.DISPLAY_TYPE_COLUMN:
          return StringUtil.capitalize(settings.getDisplayType().toString().toLowerCase());
      }

      return null;
    }
  }
}
