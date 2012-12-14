/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
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

  public AvailablePluginColumnInfo(AvailablePluginsTableModel model) {
    super(PluginManagerColumnInfo.COLUMN_NAME, model);
  }

  @Override
  public TableCellRenderer getRenderer(final IdeaPluginDescriptor pluginDescriptor) {
    return new AvailableTableRenderer(pluginDescriptor);
  }

  private static class AvailableTableRenderer extends DefaultTableCellRenderer {
    private static final int LEFT_MARGIN = new JLabel().getFontMetrics(UIUtil.getLabelFont()).stringWidth("  ");
    private final JLabel myNameLabel = new JLabel();
    private final JLabel myStatusLabel = new JLabel();
    private final JLabel myCategoryLabel = new JLabel();
    private final JPanel myPanel = new JPanel(new GridBagLayout());

    private final IdeaPluginDescriptor myPluginDescriptor;

    public AvailableTableRenderer(IdeaPluginDescriptor pluginDescriptor) {
      myPluginDescriptor = pluginDescriptor;

      myNameLabel.setFont(getNameFont());

      final Font smallFont = UIUtil.getLabelFont(UIUtil.FontSize.SMALL);
      myStatusLabel.setFont(smallFont);
      myCategoryLabel.setFont(smallFont);

      myPanel.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));

      final GridBagConstraints gn = new GridBagConstraints(GridBagConstraints.RELATIVE, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0,0), 0,0);
      myPanel.add(myNameLabel, gn);
      myPanel.add(myStatusLabel, gn);
      gn.weightx = 1;
      gn.fill = GridBagConstraints.HORIZONTAL;
      myPanel.add(Box.createHorizontalBox(), gn);

      gn.fill = GridBagConstraints.NONE;
      gn.weightx = 0;
      myPanel.add(myCategoryLabel, gn);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      Component orig = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      if (myPluginDescriptor != null) {
        final PluginNode pluginNode = (PluginNode)myPluginDescriptor;
        myNameLabel.setText(myPluginDescriptor.getName());

        final Color fg = orig.getForeground();
        final Color bg = orig.getBackground();
        final Color grayedFg = isSelected ? fg : new JBColor(Color.DARK_GRAY, Gray._128);
        myNameLabel.setForeground(fg);
        myStatusLabel.setForeground(grayedFg);
        

        myPanel.setBackground(bg);
        myNameLabel.setBackground(bg);
        myNameLabel.setIcon(AllIcons.Nodes.Plugin);
        String category = myPluginDescriptor.getCategory();
        if (category != null) {
          myCategoryLabel.setText(category);
          myCategoryLabel.setForeground(grayedFg);
        }
        final IdeaPluginDescriptor installed = PluginManager.getPlugin(pluginNode.getPluginId());
        if (isDownloaded(pluginNode) || (installed != null && InstalledPluginsTableModel.wasUpdated(installed.getPluginId()))) {
          if (!isSelected) myNameLabel.setForeground(FileStatus.ADDED.getColor());
          myStatusLabel.setText("[Downloaded]");
          myPanel.setToolTipText(IdeBundle.message("plugin.download.status.tooltip"));
          myStatusLabel.setBorder(BorderFactory.createEmptyBorder(0, LEFT_MARGIN, 0, 0));
        }
        else if (pluginNode.getStatus() == PluginNode.STATUS_INSTALLED) {
          PluginId pluginId = pluginNode.getPluginId();
          final boolean hasNewerVersion = InstalledPluginsTableModel.hasNewerVersion(pluginId);
          if (!isSelected) myNameLabel.setForeground(FileStatus.MODIFIED.getColor());
          if (hasNewerVersion) {
            if (!isSelected){
              myNameLabel.setForeground(JBColor.RED);
            }
            myNameLabel.setIcon(AllIcons.Nodes.Pluginobsolete);
          }
          myStatusLabel.setText("v." + pluginNode.getInstalledVersion() + (hasNewerVersion ? (" -> " + pluginNode.getVersion()) : ""));
          myStatusLabel.setBorder(BorderFactory.createEmptyBorder(0, LEFT_MARGIN, 0, 0));
        }
      }
      return myPanel;
    }
  }

}
