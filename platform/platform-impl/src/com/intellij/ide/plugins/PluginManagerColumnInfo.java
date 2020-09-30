// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.LightColors;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.Comparator;

/**
 * @author stathik
 */
public class PluginManagerColumnInfo extends ColumnInfo<IdeaPluginDescriptor, String> {
  public static final int COLUMN_NAME = 0;
  public static final int COLUMN_DOWNLOADS = 1;
  public static final int COLUMN_RATE = 2;
  public static final int COLUMN_DATE = 3;
  public static final int COLUMN_CATEGORY = 4;

  public static final String[] COLUMNS = {
    IdeBundle.message("column.plugins.name"),
    IdeBundle.message("column.plugins.downloads"),
    IdeBundle.message("column.plugins.rate"),
    IdeBundle.message("column.plugins.date"),
    IdeBundle.message("column.plugins.category")
  };

  private static final InstalledPluginsState ourState = InstalledPluginsState.getInstance();

  private final int columnIdx;

  public PluginManagerColumnInfo(int columnIdx) {
    super(COLUMNS[columnIdx]);
    this.columnIdx = columnIdx;
  }

  @Override
  public String valueOf(IdeaPluginDescriptor base) {
    if (columnIdx == COLUMN_NAME) {
      return base.getName();
    }
    else if (columnIdx == COLUMN_DOWNLOADS) {
      //  Base class IdeaPluginDescriptor does not declare this field.
      return base instanceof PluginNode ? ((PluginNode)base).getDownloads() : null;
    }
    if (columnIdx == COLUMN_DATE) {
      //  Base class IdeaPluginDescriptor does not declare this field.
      long date = (base instanceof PluginNode) ? ((PluginNode)base).getDate() : 0;
      if (date != 0) {
        return DateFormatUtil.formatDate(date);
      }
      else {
        return IdeBundle.message("plugin.info.not.available");
      }
    }
    else if (columnIdx == COLUMN_CATEGORY) {
      return base.getCategory();
    }
    else if (columnIdx == COLUMN_RATE) {
      return ((PluginNode)base).getRating();
    }
    else {
      // For COLUMN_STATUS - set of icons show the actual state of installed plugins.
      return "";
    }
  }

  @Override
  public Comparator<IdeaPluginDescriptor> getComparator() {
    return getColumnComparator();
  }

  protected Comparator<IdeaPluginDescriptor> getColumnComparator() {
    return (o1, o2) -> {
      return StringUtil.compare(o1.getName(), o2.getName(), true);
    };
  }

  public static String getFormattedSize(String size) {
    if (size.equals("-1")) {
      return IdeBundle.message("plugin.info.unknown");
    }
    return StringUtil.formatFileSize(Long.parseLong(size));
  }

  @Override
  public Class<?> getColumnClass() {
    return columnIdx == COLUMN_DOWNLOADS ? Integer.class : String.class;
  }

  @Override
  public TableCellRenderer getRenderer(IdeaPluginDescriptor o) {
    return columnIdx == COLUMN_RATE ? new PluginRateTableCellRenderer() : new PluginTableCellRenderer((PluginNode)o);
  }

  private static class PluginRateTableCellRenderer extends DefaultTableCellRenderer {
    private final RatesPanel myPanel = new RatesPanel();

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      Component orig = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      myPanel.setBackground(orig.getBackground());
      if (value != null) {
        myPanel.setRate((String)value);
      }
      return myPanel;
    }
  }

  private static final class PluginTableCellRenderer extends DefaultTableCellRenderer {
    private final JLabel myLabel = new JLabel();
    private final PluginNode myPluginNode;

    private PluginTableCellRenderer(PluginNode pluginNode) {
      myLabel.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
      myLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 2));
      myPluginNode = pluginNode;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      Component orig = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      Color bg = orig.getBackground();
      Color grayedFg = isSelected ? orig.getForeground() : JBColor.GRAY;
      myLabel.setForeground(grayedFg);
      myLabel.setBackground(bg);
      myLabel.setOpaque(true);

      if (column == COLUMN_DATE) {
        long date = myPluginNode.getDate();
        myLabel.setText(date != 0 && date != Long.MAX_VALUE ? DateFormatUtil.formatDate(date) : IdeBundle.message("label.category.n.a"));
        myLabel.setHorizontalAlignment(SwingConstants.RIGHT);
      }
      else if (column == COLUMN_DOWNLOADS) {
        String downloads = myPluginNode.getDownloads();
        myLabel.setText(!StringUtil.isEmpty(downloads) ? downloads : IdeBundle.message("label.category.n.a"));
        myLabel.setHorizontalAlignment(SwingConstants.RIGHT);
      }
      else if (column == COLUMN_CATEGORY) {
        String category = myPluginNode.getCategory();
        if (StringUtil.isEmpty(category)) {
          category = myPluginNode.getRepositoryName();
        }
        myLabel.setText(!StringUtil.isEmpty(category) ? category : IdeBundle.message("label.category.n.a"));
      }
      if (myPluginNode.getStatus() == PluginNode.Status.INSTALLED) {
        PluginId pluginId = myPluginNode.getPluginId();
        final boolean hasNewerVersion = ourState.hasNewerVersion(pluginId);
        if (hasNewerVersion) {
          if (!isSelected) myLabel.setBackground(LightColors.BLUE);
        }
      }
      return myLabel;
    }
  }
}