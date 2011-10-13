/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.ui.SideBorder;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

/**
 * User: anna
 * Date: 10/13/11
 */
class AvailablePluginColumnInfo extends PluginManagerColumnInfo {
  private String mySortMode;

  public AvailablePluginColumnInfo(String sortMode) {
    super(PluginManagerColumnInfo.COLUMN_NAME);
    mySortMode = sortMode;
  }

  @Override
  public TableCellRenderer getRenderer(final IdeaPluginDescriptor pluginDescriptor) {
    return new AvailableTableRenderer(pluginDescriptor);
  }

  @Override
  protected boolean isSortByName() {
    return mySortMode == PluginTableModel.NAME;
  }

  @Override
  protected boolean isSortByDownloads() {
    return mySortMode == AvailablePluginsTableModel.DOWNLOADS;
  }

  @Override
  protected boolean isSortByDate() {
    return mySortMode == AvailablePluginsTableModel.RELEASE_DATE;
  }

  public void setSortMode(String sortMode) {
    mySortMode = sortMode;
  }

  private static class AvailableTableRenderer extends DefaultTableCellRenderer {
    private static final int LEFT_MARGIN = new JLabel().getFontMetrics(UIUtil.getLabelFont()).stringWidth("  ");
    private final JLabel myNameLabel = new JLabel();
    private final JLabel myCategoryLabel = new JLabel();
    private final JLabel myDateLabel = new JLabel();
    private final JLabel myDownloadsLabel = new JLabel();
    private final JPanel myPanel = new JPanel(new GridBagLayout());

    private final IdeaPluginDescriptor myPluginDescriptor;

    public AvailableTableRenderer(IdeaPluginDescriptor pluginDescriptor) {
      myPluginDescriptor = pluginDescriptor;

      myNameLabel.setFont(getNameFont());
      myCategoryLabel.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
      myDateLabel.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
      myDownloadsLabel.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));

      myPanel.setBorder(new SideBorder(Color.lightGray, SideBorder.BOTTOM, true));

      final GridBagConstraints gc =
        new GridBagConstraints(GridBagConstraints.RELATIVE, 0, 3, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                               new Insets(0, 0, 0, 0), 0, 0);
      myPanel.add(myNameLabel, gc);

      gc.weightx = 1;
      gc.fill = GridBagConstraints.HORIZONTAL;
      myPanel.add(Box.createHorizontalBox(), gc);

      gc.weightx = 0;
      gc.fill = GridBagConstraints.NONE;
      gc.gridwidth = 1;
      gc.gridy = 1;
      myPanel.add(myCategoryLabel, gc);
      gc.insets.left = LEFT_MARGIN;
      myPanel.add(myDateLabel, gc);
      myPanel.add(myDownloadsLabel, gc);

      gc.weightx = 1;
      gc.fill = GridBagConstraints.HORIZONTAL;
      myPanel.add(Box.createHorizontalBox(), gc);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      Component orig = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      if (myPluginDescriptor != null) {
        myNameLabel.setText(myPluginDescriptor.getName());
        myCategoryLabel.setText("Category: " + myPluginDescriptor.getCategory());
        long date = ((PluginNode)myPluginDescriptor).getDate();
        myDateLabel.setText("Updated: " + (date != 0 ? DateFormatUtil.formatDate(date) : "n/a"));
        myDownloadsLabel.setText("Downloads: " + myPluginDescriptor.getDownloads());

        final Color fg = orig.getForeground();
        final Color bg = orig.getBackground();
        final Color grayedFg = isSelected ? fg : Color.GRAY;
        myNameLabel.setForeground(fg);
        myCategoryLabel.setForeground(grayedFg);
        myDateLabel.setForeground(grayedFg);
        myDownloadsLabel.setForeground(grayedFg);

        myPanel.setBackground(bg);
        myNameLabel.setBackground(bg);
        myDateLabel.setBackground(bg);
        myDownloadsLabel.setBackground(bg);

        final PluginNode pluginNode = (PluginNode)myPluginDescriptor;
        if (pluginNode.getStatus() == PluginNode.STATUS_DOWNLOADED) {
          if (!isSelected) myNameLabel.setForeground(FileStatus.COLOR_ADDED);
          myPanel.setToolTipText(IdeBundle.message("plugin.download.status.tooltip"));
        }
        else if (pluginNode.getStatus() == PluginNode.STATUS_INSTALLED) {
          if (!isSelected) myNameLabel.setForeground(FileStatus.COLOR_MODIFIED);
          myPanel.setToolTipText(IdeBundle.message("plugin.is.already.installed.status.tooltip"));
        }
      }
      return myPanel;
    }
  }

  //waiting for rates available in IDEA
  private static class RatesPanel extends JPanel {
    public static int MAX_RATE = 5;
    private static final Icon STAR_ICON = IconLoader.getIcon("/general/toolWindowFavorites.png");

    private JLabel[] myLabels = new JLabel[MAX_RATE];

    private RatesPanel() {
      super(new GridBagLayout());
      GridBagConstraints gc =
        new GridBagConstraints(GridBagConstraints.RELATIVE, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                               new Insets(0, 0, 0, 0), 0, 0);
      for (int i = 0, myLabelsLength = myLabels.length; i < myLabelsLength; i++) {
        myLabels[i] = new JLabel();
        add(myLabels[i], gc);
      }
      gc.weightx = 1;
      gc.fill = GridBagConstraints.HORIZONTAL;
      add(Box.createHorizontalBox(), gc);
      setOpaque(false);
    }

    public void setRate(int rate) {
      for (int i = 0; i < rate; i++) {
        myLabels[i].setIcon(STAR_ICON);
      }

      for (int i = rate; i < MAX_RATE; i++) {
        myLabels[i].setIcon(IconLoader.getDisabledIcon(STAR_ICON));
      }
    }
  }
}
