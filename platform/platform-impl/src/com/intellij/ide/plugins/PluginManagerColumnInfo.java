/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.plugins;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.LightColors;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.Comparator;

/**
 * @author stathik
 * @since Dec 11, 2003
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

  private static final float MB = 1024.0f * 1024.0f;
  private static final float KB = 1024.0f;

  private static final InstalledPluginsState ourState = InstalledPluginsState.getInstance();

  private final int columnIdx;
  private final PluginTableModel myModel;

  public PluginManagerColumnInfo(int columnIdx, PluginTableModel model) {
    super(COLUMNS[columnIdx]);
    this.columnIdx = columnIdx;
    myModel = model;
  }

  public String valueOf(IdeaPluginDescriptor base) {
    if (columnIdx == COLUMN_NAME) {
      return base.getName();
    }
    else if (columnIdx == COLUMN_DOWNLOADS) {
      //  Base class IdeaPluginDescriptor does not declare this field.
      return base.getDownloads();
    }
    if (columnIdx == COLUMN_DATE) {
      //  Base class IdeaPluginDescriptor does not declare this field.
      long date = (base instanceof PluginNode) ? ((PluginNode)base).getDate() : ((IdeaPluginDescriptorImpl)base).getDate();
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

  protected boolean isSortByName() {
    return !isSortByDate() && !isSortByDownloads() && !isSortByStatus();
  }

  protected boolean isSortByDownloads() {
    return myModel.isSortByDownloads();
  }

  protected boolean isSortByDate() {
    return myModel.isSortByUpdated();
  }

  protected boolean isSortByStatus() {
    return myModel.isSortByStatus();
  }

  public static boolean isDownloaded(@NotNull PluginNode node) {
    if (node.getStatus() == PluginNode.STATUS_DOWNLOADED) return true;
    final PluginId pluginId = node.getPluginId();
    if (PluginManager.isPluginInstalled(pluginId)) {
      return false;
    }
    return ourState.wasInstalled(pluginId);
  }

  public Comparator<IdeaPluginDescriptor> getComparator() {
    final Comparator<IdeaPluginDescriptor> comparator = getColumnComparator();
    if (isSortByStatus()) {
      final RowSorter.SortKey defaultSortKey = myModel.getDefaultSortKey();
      final int up = defaultSortKey != null && defaultSortKey.getSortOrder() == SortOrder.ASCENDING ? -1 : 1;
      return (o1, o2) -> {
        if (o1 instanceof PluginNode && o2 instanceof PluginNode) {
          final int status1 = ((PluginNode)o1).getStatus();
          final int status2 = ((PluginNode)o2).getStatus();
          if (isDownloaded((PluginNode)o1)){
            if (!isDownloaded((PluginNode)o2)) return up;
            return comparator.compare(o1, o2);
          }
          if (isDownloaded((PluginNode)o2)) return -up;

          if (status1 == PluginNode.STATUS_DELETED) {
            if (status2 != PluginNode.STATUS_DELETED) return up;
            return comparator.compare(o1, o2);
          }
          if (status2 == PluginNode.STATUS_DELETED) return -up;

          if (status1 == PluginNode.STATUS_INSTALLED) {
            if (status2 !=PluginNode.STATUS_INSTALLED) return up;
            final boolean hasNewerVersion1 = ourState.hasNewerVersion(o1.getPluginId());
            final boolean hasNewerVersion2 = ourState.hasNewerVersion(o2.getPluginId());
            if (hasNewerVersion1 != hasNewerVersion2) {
              if (hasNewerVersion1) return up;
              return -up;
            }
            return comparator.compare(o1, o2);
          }
          if (status2 == PluginNode.STATUS_INSTALLED) {
            return -up;
          }
        }
        return comparator.compare(o1, o2);
      };
    }

    return comparator;
  }

  protected Comparator<IdeaPluginDescriptor> getColumnComparator() {
    return (o1, o2) -> {
      if (myModel.isSortByRating()) {
        final String rating1 = ((PluginNode)o1).getRating();
        final String rating2 = ((PluginNode)o2).getRating();
        final int compare = Comparing.compare(rating2, rating1);
        if (compare != 0) {
          return compare;
        }
      }

      if (isSortByDate()) {
        long date1 = (o1 instanceof PluginNode) ? ((PluginNode)o1).getDate() : ((IdeaPluginDescriptorImpl)o1).getDate();
        long date2 = (o2 instanceof PluginNode) ? ((PluginNode)o2).getDate() : ((IdeaPluginDescriptorImpl)o2).getDate();
        date1 /= 60 * 1000;
        date2 /= 60 * 1000;
        if (date2 != date1) {
          return date2 - date1 > 0L ? 1 : -1;
        }
      }

      if (isSortByDownloads()) {
        String count1 = o1.getDownloads();
        String count2 = o2.getDownloads();
        if (count1 != null && count2 != null) {
          final Long result = Long.valueOf(count2);
          if (result != 0) {
            return result.compareTo(Long.valueOf(count1));
          }
        }
        else {
          return count1 != null ? -1 : 1;
        }
      }

      return StringUtil.compare(o1.getName(), o2.getName(), true);
    };
  }

  public static String getFormattedSize(String size) {
    if (size.equals("-1")) {
      return IdeBundle.message("plugin.info.unknown");
    }
    if (size.length() >= 7) {
      size = String.format("%.1f", (float)Integer.parseInt(size) / MB) + " M";
    }
    else if (size.length() >= 4) {
      size = String.format("%.1f", (float)Integer.parseInt(size) / KB) + " K";
    }
    return size;
  }

  public Class getColumnClass() {
    if (columnIdx == COLUMN_DOWNLOADS) {
      return Integer.class;
    }
    else {
      return String.class;
    }
  }

  public TableCellRenderer getRenderer(IdeaPluginDescriptor o) {
    if (columnIdx == COLUMN_RATE) {
      return new DefaultTableCellRenderer(){
        private RatesPanel myPanel = new RatesPanel();
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
          final Component orig = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
          myPanel.setBackground(orig.getBackground());
          if (value != null) {
            myPanel.setRate((String)value);
          }
          return myPanel;
        }
      };
    }
    return new PluginTableCellRenderer((PluginNode)o);
  }

  private static class PluginTableCellRenderer extends DefaultTableCellRenderer {
    private final JLabel myLabel = new JLabel();
    private final PluginNode myPluginDescriptor;

    private PluginTableCellRenderer(PluginNode pluginDescriptor) {
      myLabel.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
      myLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 2));
      myPluginDescriptor = pluginDescriptor;
    }

    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      Component orig = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      final Color bg = orig.getBackground();
      final Color grayedFg = isSelected ? orig.getForeground() : Color.GRAY;
      myLabel.setForeground(grayedFg);
      myLabel.setBackground(bg);
      myLabel.setOpaque(true);

      if (column == COLUMN_DATE) {
        long date = myPluginDescriptor.getDate();
        myLabel.setText(date != 0 && date != Long.MAX_VALUE ? DateFormatUtil.formatDate(date) : "n/a");
        myLabel.setHorizontalAlignment(SwingConstants.RIGHT);
      } else if (column == COLUMN_DOWNLOADS) {
        String downloads = myPluginDescriptor.getDownloads();
        myLabel.setText(!StringUtil.isEmpty(downloads) ? downloads : "n/a");
        myLabel.setHorizontalAlignment(SwingConstants.RIGHT);
      } else if (column == COLUMN_CATEGORY) {
        String category = myPluginDescriptor.getCategory();
        if (StringUtil.isEmpty(category)) {
          category = myPluginDescriptor.getRepositoryName();
        }
        myLabel.setText(!StringUtil.isEmpty(category) ? category : "n/a");
      }
      if (myPluginDescriptor.getStatus() == PluginNode.STATUS_INSTALLED) {
        PluginId pluginId = myPluginDescriptor.getPluginId();
        final boolean hasNewerVersion = ourState.hasNewerVersion(pluginId);
        if (hasNewerVersion) {
          if (!isSelected) myLabel.setBackground(LightColors.BLUE);
        }
      }
      return myLabel;
    }
  }
}
