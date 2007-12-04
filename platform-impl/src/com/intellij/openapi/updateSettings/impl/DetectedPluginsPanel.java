/*
 * User: anna
 * Date: 04-Dec-2007
 */
package com.intellij.openapi.updateSettings.impl;

import com.intellij.ui.OrderPanel;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.ArrayList;

public class DetectedPluginsPanel extends OrderPanel<PluginDownloader> {
  private ArrayList<Listener> myListeners = new ArrayList<Listener>();

  protected DetectedPluginsPanel() {
    super(PluginDownloader.class);
    getEntryTable().setDefaultRenderer(PluginDownloader.class, new DefaultTableCellRenderer(){
      // implements javax.swing.table.TableCellRenderer
      public Component getTableCellRendererComponent(final JTable table, final Object value,
                                                     final boolean isSelected, final boolean hasFocus, final int row, final int column) {
        final Component rendererComponent = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        setText(((PluginDownloader)value).getPluginName());
        return rendererComponent;
      }
    });
    setCheckboxColumnName("");
  }

  public String getCheckboxColumnName() {
    return "";
  }

  public boolean isCheckable(final PluginDownloader downloader) {
    return true;
  }

  public boolean isChecked(final PluginDownloader downloader) {
    return !UpdateChecker.getDisabledToUpdatePlugins().contains(downloader.getPluginId());
  }

  public void setChecked(final PluginDownloader downloader, final boolean checked) {
    if (checked) {
      UpdateChecker.getDisabledToUpdatePlugins().remove(downloader.getPluginId());
    } else {
      UpdateChecker.getDisabledToUpdatePlugins().add(downloader.getPluginId());
    }
    for (Listener listener : myListeners) {
      listener.stateChanged();
    }
  }

  public void addStateListener(Listener l) {
    myListeners.add(l);
  }

  public static interface Listener {
    void stateChanged();
  }

}