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
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.Function;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.text.DecimalFormat;
import java.util.Set;

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
  private JPanel myBottomPanel;
  private JPanel myInfoPanel;
  private final IdeaPluginDescriptor myPluginDescriptor;

  public PluginsTableRenderer(IdeaPluginDescriptor pluginDescriptor, boolean showFullInfo) {
    myPluginDescriptor = pluginDescriptor;
    boolean myShowFullInfo = showFullInfo;

    final Font smallFont;
    if (SystemInfo.isMac) {
      smallFont = UIUtil.getLabelFont(UIUtil.FontSize.MINI);
    } else {
      smallFont = UIUtil.getLabelFont().deriveFont(Math.max(UIUtil.getLabelFont().getSize() - 2, 10f));
    }
    myName.setFont(UIUtil.getLabelFont().deriveFont(UIUtil.getLabelFont().getSize() + 1.0f));
    myStatus.setFont(smallFont);
    myCategory.setFont(smallFont);
    myDownloads.setFont(smallFont);
    myStatus.setText("");
    myCategory.setText("");
    myLastUpdated.setFont(smallFont);
    if (!myShowFullInfo || !(pluginDescriptor instanceof PluginNode)) {
      myPanel.remove(myRightPanel);
    }

    if (!myShowFullInfo) {
      myInfoPanel.remove(myBottomPanel);
    }

    myPanel.setBorder(UIUtil.isRetina() ? new EmptyBorder(4,3,4,3) : new EmptyBorder(2,3,2,3));
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    if (myPluginDescriptor != null) {
      final PluginId pluginId = myPluginDescriptor.getPluginId();
      myName.setText(myPluginDescriptor.getName() + "  ");

      final Color fg = UIUtil.getTableForeground(isSelected);
      final Color bg = UIUtil.getTableBackground(isSelected);
      final Color grayedFg = isSelected ? fg : new JBColor(Gray._130, Gray._120);
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
        myStatus.setIcon(AllIcons.Nodes.PluginJB);
      }
      final String vendor = myPluginDescriptor.getVendor();
      if (vendor != null && vendor.toLowerCase().contains("jetbrains")) {
        myStatus.setIcon(AllIcons.Nodes.PluginJB);
      }

      myPanel.setBackground(bg);
      myLastUpdated.setForeground(grayedFg);
      myLastUpdated.setText("");
      myDownloads.setForeground(grayedFg);
      myDownloads.setText("");

      final PluginNode pluginNode = myPluginDescriptor instanceof PluginNode ? (PluginNode)myPluginDescriptor : null;
      if (pluginNode != null && pluginNode.getRepositoryName() == null) {
        String downloads = pluginNode.getDownloads();
        if (downloads == null) downloads= "";
        if (downloads.length() > 3) {
          downloads = new DecimalFormat("#,###").format(Integer.parseInt(downloads));
        }
        //if (myDownloads.getFont().canDisplay('\u2193')) {
        //  downloads += '\u2193';
        //}
        myDownloads.setText(downloads);

        myRating.setRate(pluginNode.getRating());
        myLastUpdated.setText(DateFormatUtil.formatBetweenDates(pluginNode.getDate(), System.currentTimeMillis()));
      }

      final IdeaPluginDescriptor installed = PluginManager.getPlugin(pluginId);
      if ((pluginNode != null && PluginManagerColumnInfo.isDownloaded(pluginNode)) ||
          (installed != null && InstalledPluginsTableModel.wasUpdated(installed.getPluginId()))) {
        if (!isSelected) myName.setForeground(FileStatus.ADDED.getColor());
        //todo[kb] set proper icon
        //myStatus.setText("[Downloaded]");
        myStatus.setIcon(AllIcons.Nodes.PluginRestart);
        if (installed != null) {
          myPanel.setToolTipText("Plugin was updated to the newest version. Changes will be available after restart");
        } else {
          myPanel.setToolTipText("Plugin will be activated after restart.");
        }
        //myPanel.setToolTipText(IdeBundle.message("plugin.download.status.tooltip"));
        //myStatus.setBorder(BorderFactory.createEmptyBorder(0, LEFT_MARGIN, 0, 0));
      }
      else if (pluginNode != null && pluginNode.getStatus() == PluginNode.STATUS_INSTALLED) {
        final boolean hasNewerVersion = InstalledPluginsTableModel.hasNewerVersion(pluginId);
        if (!isSelected) myName.setForeground(FileStatus.MODIFIED.getColor());
        if (hasNewerVersion) {
          if (!isSelected) {
            myName.setForeground(FileStatus.MODIFIED.getColor());
          }
          myStatus.setIcon(AllIcons.Nodes.Pluginobsolete);
        }
        //todo[kb] set proper icon
        //myStatus.setText("v." + pluginNode.getInstalledVersion() + (hasNewerVersion ? (" -> " + pluginNode.getVersion()) : ""));
      }

      if (InstalledPluginsTableModel.hasNewerVersion(pluginId)) {
        myStatus.setIcon(AllIcons.Nodes.Pluginobsolete);
        if (!isSelected) {
          myName.setForeground(FileStatus.MODIFIED.getColor());
        }
      }
      if (!myPluginDescriptor.isEnabled()) {
        myStatus.setIcon(IconLoader.getDisabledIcon(myStatus.getIcon()));
      }

      if (table.getModel() instanceof InstalledPluginsTableModel) {
        final InstalledPluginsTableModel installedPluginsTableModel = (InstalledPluginsTableModel)table.getModel();
        final Set<PluginId> required = installedPluginsTableModel.getRequiredPlugins(pluginId);
        if (required != null && required.size() > 0) {
          final StringBuilder s = new StringBuilder();
          if (!installedPluginsTableModel.isLoaded(pluginId)) {
            s.append("Plugin was not loaded.\n");
          }

          if (required.contains(PluginId.getId("com.intellij.modules.ultimate"))) {
            s.append("The plugin requires IntelliJ IDEA Ultimate");
          }
          else {
            s.append("Required plugin").append(required.size() == 1 ? " \"" : "s \"");
            s.append(StringUtil.join(required, new Function<PluginId, String>() {
              @Override
              public String fun(final PluginId id) {
                final IdeaPluginDescriptor plugin = PluginManager.getPlugin(id);
                return plugin == null ? id.getIdString() : plugin.getName();
              }
            }, ","));

            s.append(required.size() == 1 ? "\" is not enabled." : "\" are not enabled.");

          }
          myPanel.setToolTipText(s.toString());
        } else if (PluginManagerCore.isIncompatible(myPluginDescriptor)) {
          myPanel.setToolTipText(IdeBundle.message("plugin.manager.incompatible.tooltip.warning", ApplicationNamesInfo.getInstance().getFullProductName()));
        }
      }
    }
    if (!isSelected) {
      if (PluginManagerCore.isIncompatible(myPluginDescriptor)) {
        myName.setForeground(JBColor.RED);
      } else if (myPluginDescriptor != null && table.getModel() instanceof InstalledPluginsTableModel) {
        if (((InstalledPluginsTableModel)table.getModel()).hasProblematicDependencies(myPluginDescriptor.getPluginId())) {
          myName.setForeground(JBColor.RED);
        }
      }
    }

    return myPanel;
  }

  private void createUIComponents() {
    myRating = new RatesPanel();
  }
}
