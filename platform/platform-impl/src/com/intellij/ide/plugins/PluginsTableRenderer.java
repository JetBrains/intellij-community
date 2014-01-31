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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.text.DecimalFormat;

/**
* @author Konstantin Bulenkov
*/
public class PluginsTableRenderer extends DefaultTableCellRenderer {
  private JLabel myName;
  private JLabel myStatus;
  private RatesPanel myRating;
  private JLabel myDownloads;
  private JLabel myLastUpdated;
  private JPanel myPanel;

  private JLabel myCategory;
  private JPanel myRightPanel;
  private final IdeaPluginDescriptor myPluginDescriptor;

  public PluginsTableRenderer(IdeaPluginDescriptor pluginDescriptor) {
    myPluginDescriptor = pluginDescriptor;

    final Font smallFont = UIUtil.getLabelFont(UIUtil.FontSize.MINI);
    final Font miniFont = UIUtil.getLabelFont(UIUtil.FontSize.MINI);
    myName.setFont(UIUtil.getLabelFont().deriveFont(UIUtil.getLabelFont().getSize() + 1.0f));
    myStatus.setFont(smallFont);
    myCategory.setFont(smallFont);
    myDownloads.setFont(miniFont);
    myStatus.setText("");
    myCategory.setText("");
    myLastUpdated.setFont(miniFont);
    if (! (pluginDescriptor instanceof PluginNode)) {
      myPanel.remove(myRightPanel);
    }
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    if (myPluginDescriptor != null) {
      myName.setText(myPluginDescriptor.getName() + "  ");

      final Color fg = UIUtil.getTableForeground(isSelected);
      final Color bg = UIUtil.getTableBackground(isSelected);
      final Color grayedFg = isSelected ? fg : new JBColor(Gray._130, Gray._200);
      myName.setForeground(fg);
      myStatus.setForeground(grayedFg);
      myStatus.setIcon(AllIcons.Nodes.Plugin);
      String category = myPluginDescriptor.getCategory();
      myCategory.setForeground(grayedFg);
      if (category != null) {
        myCategory.setText(category.toUpperCase() + " ");
      }
      if (myPluginDescriptor.isBundled()) {
        myCategory.setText(myCategory.getText() + "[Bundled]");
      }

      myPanel.setBackground(bg);
      myLastUpdated.setForeground(grayedFg);
      myLastUpdated.setText("");
      myDownloads.setForeground(grayedFg);
      myDownloads.setText("");

      final PluginNode pluginNode = myPluginDescriptor instanceof PluginNode ? (PluginNode)myPluginDescriptor : null;
      if (pluginNode != null) {
        String downloads = pluginNode.getDownloads();
        if (downloads.length() > 3) {
          downloads = new DecimalFormat("#,###").format(Integer.parseInt(downloads));
        }
        myDownloads.setText(downloads);

        myRating.setRate(pluginNode.getRating());
        myLastUpdated.setText(DateFormatUtil.formatBetweenDates(pluginNode.getDate(), System.currentTimeMillis()));
      }

      final IdeaPluginDescriptor installed = PluginManager.getPlugin(myPluginDescriptor.getPluginId());
      if ((pluginNode != null && PluginManagerColumnInfo.isDownloaded(pluginNode)) ||
          (installed != null && InstalledPluginsTableModel.wasUpdated(installed.getPluginId()))) {
        if (!isSelected) myName.setForeground(FileStatus.ADDED.getColor());
        //todo[kb] set proper icon
        //myStatus.setText("[Downloaded]");
        //myPanel.setToolTipText(IdeBundle.message("plugin.download.status.tooltip"));
        //myStatus.setBorder(BorderFactory.createEmptyBorder(0, LEFT_MARGIN, 0, 0));
      }
      else if (pluginNode != null && pluginNode.getStatus() == PluginNode.STATUS_INSTALLED) {
        PluginId pluginId = pluginNode.getPluginId();
        final boolean hasNewerVersion = InstalledPluginsTableModel.hasNewerVersion(pluginId);
        if (!isSelected) myName.setForeground(FileStatus.MODIFIED.getColor());
        if (hasNewerVersion) {
          if (!isSelected) {
            myName.setForeground(JBColor.RED);
          }
          myName.setIcon(AllIcons.Nodes.Pluginobsolete);
        }
        //todo[kb] set proper icon
        //myStatus.setText("v." + pluginNode.getInstalledVersion() + (hasNewerVersion ? (" -> " + pluginNode.getVersion()) : ""));
      }
    }

    return myPanel;
  }

  private void createUIComponents() {
    myRating = new RatesPanel();
  }
}
