// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class PluginHeaderPanel {
  private static final InstalledPluginsState ourState = InstalledPluginsState.getInstance();

  private IdeaPluginDescriptor myPlugin;
  private JBLabel myCategory;
  private JBLabel myName;
  private JBLabel myDownloads;
  private RatesPanel myRating;
  private JBLabel myUpdated;
  private JBLabel myVersion;
  private JPanel myRoot;
  private JPanel myDownloadsPanel;
  private JPanel myVersionInfoPanel;

  public PluginHeaderPanel() {
    final Font font = myName.getFont();
    myName.setFont(new Font(font.getFontName(), font.getStyle(), font.getSize() + 2));
    final JBColor greyed = new JBColor(Gray._130, Gray._200);
    myCategory.setForeground(greyed);
    myDownloads.setForeground(greyed);
    myUpdated.setForeground(greyed);
    myVersion.setForeground(greyed);
    final Font smallFont = new Font(font.getFontName(), font.getStyle(), font.getSize() - 1);
    myCategory.setFont(smallFont);
    myVersion.setFont(smallFont);
    myVersion.setCopyable(true);
    myDownloads.setFont(smallFont);
    myUpdated.setFont(smallFont);
    myRoot.setVisible(false);
  }

  public void setPlugin(IdeaPluginDescriptor plugin) {
    myPlugin = plugin;
    myRoot.setVisible(true);
    myRoot.setBackground(UIUtil.getTextFieldBackground());
    myCategory.setVisible(true);
    myDownloadsPanel.setVisible(true);
    myUpdated.setVisible(true);
    myName.setFont(StartupUiUtil.getLabelFont().deriveFont(4f + StartupUiUtil.getLabelFont().getSize()));

    //data
    //noinspection HardCodedStringLiteral
    myName.setText("<html><body>" + plugin.getName() + "</body></html>");
    myCategory.setText(plugin.getCategory() == null ? IdeBundle.message("label.category.unknown") : StringUtil.toUpperCase(plugin.getCategory())); //NON-NLS
    String versionText;
    boolean showVersion = !plugin.isBundled() || plugin.allowBundledUpdate();
    if (plugin instanceof PluginNode) {
      final PluginNode node = (PluginNode)plugin;
      myRating.setRate(node.getRating());
      myDownloads.setText(IdeBundle.message("label.plugin.0.downloads", node.getDownloads()));
      versionText = showVersion ? "v" + node.getVersion() : null; //NON-NLS
      myUpdated.setText(IdeBundle.message("label.plugin.updated.0", DateFormatUtil.formatDate(node.getDate())));
      if (node.getRepositoryName() != null) {
        myCategory.setVisible(false);
        myDownloadsPanel.setVisible(false);
        myUpdated.setVisible(false);
      }
    }
    else {
      myVersionInfoPanel.remove(myUpdated);
      myCategory.setVisible(false);
      myDownloadsPanel.setVisible(false);
      final String version = plugin.getVersion();
      if (ourState.wasUpdated(plugin.getPluginId())) {
        versionText = IdeBundle.message("label.new.version.will.be.available.after.restart");
      }
      else if (version != null && showVersion) {
        versionText = IdeBundle.message("label.version", version);
      }
      else {
        versionText = null;
      }
      myUpdated.setVisible(false);
    }
    myVersion.setVisible(versionText != null);
    myVersion.setText(StringUtil.notNullize(versionText));
    myRoot.revalidate();
    myVersion.getParent().revalidate();
    myVersion.revalidate();
  }

  public JBLabel getCategory() {
    return myCategory;
  }

  public JBLabel getName() {
    return myName;
  }

  public JBLabel getDownloads() {
    return myDownloads;
  }

  public RatesPanel getRating() {
    return myRating;
  }

  public JBLabel getUpdated() {
    return myUpdated;
  }

  public JPanel getPanel() {
    return myRoot;
  }
}