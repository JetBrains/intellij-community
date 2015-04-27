/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.Function;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.text.Matcher;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;
import java.awt.*;
import java.text.DecimalFormat;
import java.util.Set;

/**
* @author Konstantin Bulenkov
*/
public class PluginsTableRenderer extends DefaultTableCellRenderer {
  private static final InstalledPluginsState ourState = InstalledPluginsState.getInstance();

  private SimpleColoredComponent myName;
  private JLabel myStatus;
  private RatesPanel myRating;
  private JLabel myDownloads;
  private JLabel myLastUpdated;
  private JPanel myPanel;

  private SimpleColoredComponent myCategory;
  private JPanel myRightPanel;
  private JPanel myBottomPanel;
  private JPanel myInfoPanel;

  private final IdeaPluginDescriptor myPluginDescriptor;
  private final boolean myPluginsView;

  // showFullInfo: true for Plugin Repository view, false for Installed Plugins view
  public PluginsTableRenderer(IdeaPluginDescriptor pluginDescriptor, boolean showFullInfo) {
    myPluginDescriptor = pluginDescriptor;
    myPluginsView = !showFullInfo;

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
    myLastUpdated.setFont(smallFont);

    if (myPluginsView || pluginDescriptor.getDownloads() == null || !(pluginDescriptor instanceof PluginNode)) {
      myPanel.remove(myRightPanel);
    }
    if (myPluginsView) {
      myInfoPanel.remove(myBottomPanel);
    }

    myPanel.setBorder(UIUtil.isRetina() ? new EmptyBorder(4, 3, 4, 3) : new EmptyBorder(2, 3, 2, 3));
  }

  private void createUIComponents() {
    myRating = new RatesPanel();
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    if (myPluginDescriptor != null) {
      Color fg = UIUtil.getTableForeground(isSelected);
      Color bg = UIUtil.getTableBackground(isSelected);
      Color grayedFg = isSelected ? fg : new JBColor(Gray._130, Gray._120);

      myPanel.setBackground(bg);

      myName.setForeground(fg);
      myCategory.setForeground(grayedFg);
      myStatus.setForeground(grayedFg);
      myLastUpdated.setForeground(grayedFg);
      myDownloads.setForeground(grayedFg);

      myName.clear();
      myName.setOpaque(false);
      myCategory.clear();
      myCategory.setOpaque(false);
      String pluginName = myPluginDescriptor.getName() + "  ";
      Object query = table.getClientProperty(SpeedSearchSupply.SEARCH_QUERY_KEY);
      SimpleTextAttributes attr = new SimpleTextAttributes(UIUtil.getListBackground(isSelected),
                                                           UIUtil.getListForeground(isSelected),
                                                           JBColor.RED,
                                                           SimpleTextAttributes.STYLE_PLAIN);
      Matcher matcher = NameUtil.buildMatcher("*" + query, NameUtil.MatchingCaseSensitivity.NONE);
      if (query instanceof String) {
        SpeedSearchUtil.appendColoredFragmentForMatcher(pluginName, myName, attr, matcher, UIUtil.getTableBackground(isSelected), true);
      }
      else {
        myName.append(pluginName);
      }

      String category = myPluginDescriptor.getCategory() == null ? null : StringUtil.toUpperCase(myPluginDescriptor.getCategory());
      if (category != null) {
        if (query instanceof String) {
          SpeedSearchUtil.appendColoredFragmentForMatcher(category, myCategory, attr, matcher, UIUtil.getTableBackground(isSelected), true);
        }
        else {
          myCategory.append(category);
        }
      }
      else if (!myPluginsView) {
        myCategory.append(AvailablePluginsManagerMain.N_A);
      }

      myStatus.setIcon(AllIcons.Nodes.Plugin);
      if (myPluginDescriptor.isBundled()) {
        myCategory.append(" [Bundled]");
        myStatus.setIcon(AllIcons.Nodes.PluginJB);
      }
      String vendor = myPluginDescriptor.getVendor();
      if (vendor != null && StringUtil.containsIgnoreCase(vendor, "jetbrains")) {
        myStatus.setIcon(AllIcons.Nodes.PluginJB);
      }

      String downloads = myPluginDescriptor.getDownloads();
      if (downloads != null && myPluginDescriptor instanceof PluginNode) {
        if (downloads.length() > 3) {
          downloads = new DecimalFormat("#,###").format(Integer.parseInt(downloads));
        }
        myDownloads.setText(downloads);

        myRating.setRate(((PluginNode)myPluginDescriptor).getRating());
        myLastUpdated.setText(DateFormatUtil.formatBetweenDates(((PluginNode)myPluginDescriptor).getDate(), System.currentTimeMillis()));
      }

      // plugin state-dependent rendering

      PluginId pluginId = myPluginDescriptor.getPluginId();
      IdeaPluginDescriptor installed = PluginManager.getPlugin(pluginId);

      if (installed != null && ((IdeaPluginDescriptorImpl)installed).isDeleted()) {
        // existing plugin uninstalled (both views)
        myStatus.setIcon(AllIcons.Nodes.PluginRestart);
        if (!isSelected) myName.setForeground(FileStatus.DELETED.getColor());
        myPanel.setToolTipText(IdeBundle.message("plugin.manager.uninstalled.tooltip"));
      }
      else if (ourState.wasInstalled(pluginId)) {
        // new plugin installed (both views)
        myStatus.setIcon(AllIcons.Nodes.PluginRestart);
        if (!isSelected) myName.setForeground(FileStatus.ADDED.getColor());
        myPanel.setToolTipText(IdeBundle.message("plugin.manager.installed.tooltip"));
      }
      else if (ourState.wasUpdated(pluginId)) {
        // existing plugin updated (both views)
        myStatus.setIcon(AllIcons.Nodes.PluginRestart);
        if (!isSelected) myName.setForeground(FileStatus.ADDED.getColor());
        myPanel.setToolTipText(IdeBundle.message("plugin.manager.updated.tooltip"));
      }
      else if (ourState.hasNewerVersion(pluginId)) {
        // existing plugin has a newer version (both views)
        myStatus.setIcon(AllIcons.Nodes.Pluginobsolete);
        if (!isSelected) myName.setForeground(FileStatus.MODIFIED.getColor());
        if (!myPluginsView && installed != null) {
          myPanel.setToolTipText(IdeBundle.message("plugin.manager.new.version.tooltip", installed.getVersion()));
        }
        else {
          myPanel.setToolTipText(IdeBundle.message("plugin.manager.update.available.tooltip"));
        }
      }
      else if (isIncompatible(myPluginDescriptor, table.getModel())) {
        // a plugin is incompatible with current installation (both views)
        if (!isSelected) myName.setForeground(JBColor.RED);
        myPanel.setToolTipText(whyIncompatible(myPluginDescriptor, table.getModel()));
      }
      else if (!myPluginDescriptor.isEnabled() && myPluginsView) {
        // a plugin is disabled (plugins view only)
        myStatus.setIcon(IconLoader.getDisabledIcon(myStatus.getIcon()));
      }
    }

    return myPanel;
  }

  private static boolean isIncompatible(IdeaPluginDescriptor descriptor, TableModel model) {
    return PluginManagerCore.isIncompatible(descriptor) ||
           model instanceof InstalledPluginsTableModel && ((InstalledPluginsTableModel)model).hasProblematicDependencies(descriptor.getPluginId());
  }

  private static String whyIncompatible(IdeaPluginDescriptor descriptor, TableModel model) {
    if (model instanceof InstalledPluginsTableModel) {
      InstalledPluginsTableModel installedModel = (InstalledPluginsTableModel)model;
      Set<PluginId> required = installedModel.getRequiredPlugins(descriptor.getPluginId());

      if (required != null && required.size() > 0) {
        StringBuilder sb = new StringBuilder();

        if (!installedModel.isLoaded(descriptor.getPluginId())) {
          sb.append(IdeBundle.message("plugin.manager.incompatible.not.loaded.tooltip")).append('\n');
        }

        if (required.contains(PluginId.getId("com.intellij.modules.ultimate"))) {
          sb.append(IdeBundle.message("plugin.manager.incompatible.ultimate.tooltip"));
        }
        else {
          String deps = StringUtil.join(required, new Function<PluginId, String>() {
            @Override
            public String fun(PluginId id) {
              IdeaPluginDescriptor plugin = PluginManager.getPlugin(id);
              return plugin != null ? plugin.getName() : id.getIdString();
            }
          }, ", ");
          sb.append(IdeBundle.message("plugin.manager.incompatible.deps.tooltip", required.size(), deps));
        }

        return sb.toString();
      }
    }

    return IdeBundle.message("plugin.manager.incompatible.tooltip", ApplicationNamesInfo.getInstance().getFullProductName());
  }
}
