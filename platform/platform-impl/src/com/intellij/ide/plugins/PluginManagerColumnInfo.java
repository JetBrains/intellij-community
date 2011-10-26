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
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
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
    else
    // For COLUMN_STATUS - set of icons show the actual state of installed plugins.
    {
      return "";
    }
  }

  protected boolean isSortByName() {
    return COLUMN_NAME == columnIdx;
  }
  
  protected boolean isSortByDownloads() {
    return columnIdx == COLUMN_DOWNLOADS;
  }

  protected boolean isSortByDate() {
    return columnIdx == COLUMN_DATE;
  }

  protected boolean isSortByStatus() {
    return true;
  }

  protected boolean isSortByRepository() {
    return true;
  }

  public static boolean isDownloaded(@NotNull PluginNode node) {
    if (node.getStatus() == PluginNode.STATUS_DOWNLOADED) return true;
    final PluginId pluginId = node.getPluginId();
    if (PluginManager.isPluginInstalled(pluginId)) {
      return false;
    }
    return PluginManagerUISettings.getInstance().myInstalledPlugins.contains(pluginId.getIdString());
  }

  public Comparator<IdeaPluginDescriptor> getComparator() {
    if (isSortByName()) {
      return new Comparator<IdeaPluginDescriptor>() {
        public int compare(IdeaPluginDescriptor o1, IdeaPluginDescriptor o2) {
          return StringUtil.compare(o1.getName(), o2.getName(), true);
        }
      };
    }
    if (isSortByDownloads()) {
      return new Comparator<IdeaPluginDescriptor>() {
        public int compare(IdeaPluginDescriptor o1, IdeaPluginDescriptor o2) {
          String count1 = o1.getDownloads();
          String count2 = o2.getDownloads();
          if (count1 != null && count2 != null) {
            return -Long.valueOf(count1).compareTo(Long.valueOf(count2));
          }
          else if (count1 != null) {
            return -1;
          }
          else {
            return 1;
          }
        }
      };
    }
    if (isSortByDate()) {
      return new Comparator<IdeaPluginDescriptor>() {
        public int compare(IdeaPluginDescriptor o1, IdeaPluginDescriptor o2) {
          long date1 = (o1 instanceof PluginNode) ? ((PluginNode)o1).getDate() : ((IdeaPluginDescriptorImpl)o1).getDate();
          long date2 = (o2 instanceof PluginNode) ? ((PluginNode)o2).getDate() : ((IdeaPluginDescriptorImpl)o2).getDate();
          if (date1 < date2) {
            return 1;
          }
          else if (date1 > date2) return -1;
          return 0;
        }
      };
    }
    if (isSortByStatus()) {
      return new Comparator<IdeaPluginDescriptor>() {
        public int compare(IdeaPluginDescriptor o1, IdeaPluginDescriptor o2) {
          if (o1 instanceof PluginNode && o2 instanceof PluginNode) {
            final int status1 = ((PluginNode)o1).getStatus();
            final int status2 = ((PluginNode)o2).getStatus();
            if (isDownloaded((PluginNode)o1)){
              if (!isDownloaded((PluginNode)o2)) return -1;
              return StringUtil.compare(o1.getName(), o2.getName(), true);
            }
            if (isDownloaded((PluginNode)o2)) return 1;

            if (status1 == PluginNode.STATUS_DELETED) {
              if (status2 != PluginNode.STATUS_DELETED) return -1;
              return StringUtil.compare(o1.getName(), o2.getName(), true);
            }
            if (status2 == PluginNode.STATUS_DELETED) return 1;

            if (status1 == PluginNode.STATUS_INSTALLED) {
              if (status2 !=PluginNode.STATUS_INSTALLED) return -1;
              final boolean hasNewerVersion1 = InstalledPluginsTableModel.hasNewerVersion(o1.getPluginId());
              final boolean hasNewerVersion2 = InstalledPluginsTableModel.hasNewerVersion(o2.getPluginId());
              if (hasNewerVersion1 != hasNewerVersion2) {
                if (hasNewerVersion1) return -1;
                return 1;
              }
              return StringUtil.compare(o1.getName(), o2.getName(), true);
            }
            if (status2 == PluginNode.STATUS_INSTALLED) {
              return 1;
            }
          }
          return StringUtil.compare(o1.getName(), o2.getName(), true);
        }
      };
    }
    if (isSortByRepository()) {
      return new Comparator<IdeaPluginDescriptor>() {
        @Override
        public int compare(IdeaPluginDescriptor o1, IdeaPluginDescriptor o2) {
          if (o1 instanceof PluginNode && o2 instanceof PluginNode) {
            final String repositoryName1 = ((PluginNode)o1).getRepositoryName();
            final String repositoryName2 = ((PluginNode)o2).getRepositoryName();
            if (!Comparing.strEqual(repositoryName1, repositoryName2)) {
              return StringUtil.compare(repositoryName1, repositoryName2, true);
            }
          }
          return StringUtil.compare(o1.getName(), o2.getName(), true);
        }
      };
    }
    return new Comparator<IdeaPluginDescriptor>() {
      public int compare(IdeaPluginDescriptor o, IdeaPluginDescriptor o1) {
        return 0;
      }
    };
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

  public Class getColumnClass() {
    return String.class;
  }

  protected static Font getNameFont() {
    Font f = new JLabel().getFont();
    return f.deriveFont(Math.min(14.f, f.getSize() * 1.1f));
  }
}
