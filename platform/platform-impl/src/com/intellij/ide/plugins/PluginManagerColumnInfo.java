/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.ColumnInfo;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.Comparator;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Dec 11, 2003
 * Time: 2:55:50 PM
 * To change this template use Options | File Templates.
 */
class PluginManagerColumnInfo extends ColumnInfo<IdeaPluginDescriptor, String> {
  public static final int COLUMN_NAME = 0;
  public static final int COLUMN_DOWNLOADS = 1;
  public static final int COLUMN_DATE = 2;
  public static final int COLUMN_CATEGORY = 3;
  public static final int COLUMN_INSTALLED_VERSION = 4;
  public static final int COLUMN_SIZE = 5;
  public static final int COLUMN_VERSION = 6;
  public static final int COLUMN_STATE = 7;
  private static final float mgByte = 1024.0f * 1024.0f;
  private static final float kByte = 1024.0f;


  public static final String[] COLUMNS = {
    IdeBundle.message("column.plugins.name"),
    IdeBundle.message("column.plugins.downloads"),
    IdeBundle.message("column.plugins.date"),
    IdeBundle.message("column.plugins.category")
  };

  private final int columnIdx;

  public PluginManagerColumnInfo(int columnIdx) {
    super(COLUMNS[columnIdx]);
    this.columnIdx = columnIdx;
  }

  public String valueOf(IdeaPluginDescriptor base) {
    if (columnIdx == COLUMN_NAME) {
      return base.getName();
    }
    else if (columnIdx == COLUMN_DOWNLOADS) {
      //  Base class IdeaPluginDescriptor does not declare this field.
      return (base instanceof PluginNode) ? ((PluginNode)base).getDownloads() : ((IdeaPluginDescriptorImpl)base).getDownloads();
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
    else
    // For COLUMN_STATUS - set of icons show the actual state of installed plugins.
    {
      return "";
    }
  }

  public Comparator<IdeaPluginDescriptor> getComparator() {
    switch (columnIdx) {
      case COLUMN_NAME:
        return new Comparator<IdeaPluginDescriptor>() {
          public int compare(IdeaPluginDescriptor o1, IdeaPluginDescriptor o2) {
            String name1 = o1.getName();
            String name2 = o2.getName();
            return compareStrings(name1, name2);
          }
        };

      case COLUMN_DOWNLOADS:
        return new Comparator<IdeaPluginDescriptor>() {
          public int compare(IdeaPluginDescriptor o1, IdeaPluginDescriptor o2) {
            String count1 = (o1 instanceof PluginNode) ? ((PluginNode)o1).getDownloads() : ((IdeaPluginDescriptorImpl)o1).getDownloads();
            String count2 = (o2 instanceof PluginNode) ? ((PluginNode)o2).getDownloads() : ((IdeaPluginDescriptorImpl)o2).getDownloads();
            if (count1 != null && count2 != null) {
              return new Long(count1).compareTo(new Long(count2));
            }
            else if (count1 != null) {
              return 1;
            }
            else {
              return -1;
            }
          }
        };

      case COLUMN_CATEGORY:
        return new Comparator<IdeaPluginDescriptor>() {
          public int compare(IdeaPluginDescriptor o1, IdeaPluginDescriptor o2) {
            String cat1 = o1.getCategory();
            String cat2 = o2.getCategory();
            return compareStrings(cat1, cat2);
          }
        };

      case COLUMN_DATE:
        return new Comparator<IdeaPluginDescriptor>() {
          public int compare(IdeaPluginDescriptor o1, IdeaPluginDescriptor o2) {
            long date1 = (o1 instanceof PluginNode) ? ((PluginNode)o1).getDate() : ((IdeaPluginDescriptorImpl)o1).getDate();
            long date2 = (o2 instanceof PluginNode) ? ((PluginNode)o2).getDate() : ((IdeaPluginDescriptorImpl)o2).getDate();
            if (date1 > date2) {
              return 1;
            }
            else if (date1 < date2) return -1;
            return 0;
          }
        };

      default:
        return new Comparator<IdeaPluginDescriptor>() {
          public int compare(IdeaPluginDescriptor o, IdeaPluginDescriptor o1) {
            return 0;
          }
        };
    }
  }

  public static int compareStrings(String str1, String str2) {
    if (str1 == null && str2 == null) {
      return 0;
    }
    else if (str1 == null) {
      return -1;
    }
    else if (str2 == null) {
      return 1;
    }
    else {
      return str1.compareToIgnoreCase(str2);
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static String getFormattedSize(String size) {
    if (size.equals("-1")) {
      return IdeBundle.message("plugin.info.unknown");
    }
    else if (size.length() >= 4) {
      if (size.length() < 7) {
        size = String.format("%.1f", (float)Integer.parseInt(size) / kByte) + " K";
      }
      else {
        size = String.format("%.1f", (float)Integer.parseInt(size) / mgByte) + " M";
      }
    }
    return size;
  }

  public static int getRealNodeState(PluginNode node) {
    if (node.getStatus() == PluginNode.STATUS_DOWNLOADED) return PluginNode.STATUS_DOWNLOADED;
    return PluginNode.STATUS_MISSING;
  }

  public TableCellRenderer getRenderer(IdeaPluginDescriptor o) {
    return new PluginTableCellRenderer(this);
  }

  protected int getHorizontalAlignment() {
    return SwingConstants.LEADING;
  }

  public Class getColumnClass() {
    if (columnIdx == COLUMN_SIZE || columnIdx == COLUMN_DOWNLOADS) {
      return Integer.class;
    }
    else {
      return String.class;
    }
  }

  private static class PluginTableCellRenderer extends DefaultTableCellRenderer {
    private final PluginManagerColumnInfo myColumnInfo;

    private PluginTableCellRenderer(final PluginManagerColumnInfo columnInfo) {
      myColumnInfo = columnInfo;
    }

    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      Object descriptor = ((PluginTable)table).getObjectAt(row);
      if (column == COLUMN_NAME) {
        setIcon(IconLoader.getIcon("/nodes/pluginnotinstalled.png"));
      }

      setHorizontalAlignment(myColumnInfo.getHorizontalAlignment());
      if (descriptor instanceof IdeaPluginDescriptorImpl) {
        final IdeaPluginDescriptorImpl ideaPluginDescriptor = (IdeaPluginDescriptorImpl)descriptor;
        if (ideaPluginDescriptor.isDeleted()) {
          if (!isSelected) setForeground(FileStatus.COLOR_MISSING);
          setToolTipText(IdeBundle.message("plugin.deleted.status.tooltip"));
        } else if (InstalledPluginsTableModel.hasNewerVersion(ideaPluginDescriptor.getPluginId())) {
          if (!isSelected) setForeground(FileStatus.COLOR_MODIFIED);
          setToolTipText(IdeBundle.message("plugin.outdated.version.status.tooltip"));
        } else if (InstalledPluginsTableModel.wasUpdated(ideaPluginDescriptor.getPluginId())) {
          if (!isSelected) setForeground(FileStatus.COLOR_MODIFIED);
          setToolTipText(IdeBundle.message("plugin.updated.status.tooltip"));
        }
      } else if (descriptor instanceof PluginNode) {
        final PluginNode pluginNode = (PluginNode)descriptor;
        if (pluginNode.getStatus() == PluginNode.STATUS_DOWNLOADED){
          if (!isSelected) setForeground(FileStatus.COLOR_ADDED);
          if (column == COLUMN_NAME) setIcon(IconLoader.getIcon("/nodes/plugin.png"));
          setToolTipText(IdeBundle.message("plugin.download.status.tooltip"));
        } else if (pluginNode.getStatus() == PluginNode.STATUS_INSTALLED) {
          if (!isSelected) setForeground(FileStatus.COLOR_MODIFIED);
          setToolTipText(IdeBundle.message("plugin.is.already.installed.status.tooltip"));
        }
      }

      return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
    }
  }


  @Override
  public int getWidth(JTable table) {
    return (columnIdx == 0) ? 35 : -1;
  }
}
