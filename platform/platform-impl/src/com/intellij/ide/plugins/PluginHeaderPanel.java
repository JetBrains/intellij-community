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
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBGradientPaint;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Konstantin Bulenkov
 */
public class PluginHeaderPanel {
  private IdeaPluginDescriptor myPlugin;
  @Nullable
  private final PluginManagerMain myManager;
  private final JTable myPluginTable;
  private JBLabel myCategory;
  private JBLabel myName;
  private JBLabel myDownloads;
  private RatesPanel myRating;
  private JBLabel myUpdated;
  private JButton myInstallButton;
  private JBLabel myVersion;
  private JPanel myRoot;
  private JPanel myButtonPanel;
  private JPanel myDownloadsPanel;
  private JPanel myVersionInfoPanel;

  enum ACTION_ID {UPDATE, INSTALL, UNINSTALL, RESTART}
  private ACTION_ID myActionId = ACTION_ID.INSTALL;

  public PluginHeaderPanel(@Nullable PluginManagerMain manager, JTable pluginTable) {
    myManager = manager;
    myPluginTable = pluginTable;
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
    myButtonPanel.setVisible(true);
    myUpdated.setVisible(true);
    myName.setFont(UIUtil.getLabelFont().deriveFont(4f + UIUtil.getLabelFont().getSize()));

    //data
    myName.setText("<html><body>" + plugin.getName() + "</body></html>");
    myCategory.setText(plugin.getCategory() == null ? "UNKNOWN" : plugin.getCategory().toUpperCase());
    final boolean hasNewerVersion = InstalledPluginsTableModel.hasNewerVersion(plugin.getPluginId());
    if (plugin instanceof PluginNode) {
      final PluginNode node = (PluginNode)plugin;
      myRating.setRate(node.getRating());
      myDownloads.setText(node.getDownloads() + " downloads");
      myVersion.setText(" ver " + node.getVersion());
      myUpdated.setText("Updated " + DateFormatUtil.formatDate(node.getDate()));
      switch (node.getStatus()) {
        case PluginNode.STATUS_INSTALLED:
          myActionId = hasNewerVersion ? ACTION_ID.UPDATE : ACTION_ID.UNINSTALL;
          break;
        case PluginNode.STATUS_DOWNLOADED:
          myActionId = ACTION_ID.RESTART;
          break;
        default:
          myActionId = ACTION_ID.INSTALL;
      }
      if (node.getRepositoryName() != null) {
        myCategory.setVisible(false);
        myDownloadsPanel.setVisible(false);
        myUpdated.setVisible(false);
      }

      final IdeaPluginDescriptor installed = PluginManager.getPlugin(plugin.getPluginId());
       if ((PluginManagerColumnInfo.isDownloaded(node))
         || (installed != null && InstalledPluginsTableModel.wasUpdated(installed.getPluginId()))
         || (installed instanceof IdeaPluginDescriptorImpl && !plugin.isBundled() && ((IdeaPluginDescriptorImpl)installed).isDeleted())) {
         myActionId = ACTION_ID.RESTART;
       }
    } else {
      myActionId = null;
      myVersionInfoPanel.remove(myUpdated);
      myCategory.setVisible(false);
      myDownloadsPanel.setVisible(false);
      final String version = plugin.getVersion();
      myVersion.setText("Version: " + (version == null ? "N/A" : version));
      myUpdated.setVisible(false);
      if (!plugin.isBundled() || hasNewerVersion) {
        if (((IdeaPluginDescriptorImpl)plugin).isDeleted()) {
          myActionId = ACTION_ID.RESTART;
        } else if (hasNewerVersion) {
          myActionId = ACTION_ID.UPDATE;
        } else {
          myActionId = ACTION_ID.UNINSTALL;
        }
      }
      if (myActionId == ACTION_ID.RESTART && myManager != null && !myManager.isRequireShutdown()) {
        myActionId = null;
      }
    }
    if (myManager == null || myActionId == null || (myManager.getInstalled() != myManager.getAvailable() && myActionId == ACTION_ID.UNINSTALL)) {
      myActionId = ACTION_ID.INSTALL;
      myButtonPanel.setVisible(false);
    }
  myRoot.revalidate();
    ((JComponent)myInstallButton.getParent()).revalidate();
    myInstallButton.revalidate();
    ((JComponent)myVersion.getParent()).revalidate();
    myVersion.revalidate();
  }

  private void createUIComponents() {
    myInstallButton = new JButton() {
      {
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      }
      @Override
      public Dimension getPreferredSize() {
        final FontMetrics metrics = getFontMetrics(getFont());
        final int textWidth = metrics.stringWidth(getText());
        final int width = 8 + 16 + 4 + textWidth + 8;
        final int height = 2 + Math.max(16, metrics.getHeight()) + 2;
        return new Dimension(width, height);
      }

      @Override
      public void paint(Graphics g2) {
        final Graphics2D g = (Graphics2D)g2;
        final GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
        final int w = g.getClipBounds().width;
        final int h = g.getClipBounds().height;

        g.setPaint(getBackgroundBorderPaint());
        g.fillRoundRect(0, 0, w, h, 7, 7);

        g.setPaint(getBackgroundPaint());
        g.fillRoundRect(1, 1, w - 2, h - 2, 6, 6);
        g.setColor(getButtonForeground());
        g.drawString(getText(), 8 + 16 + 4, getBaseline(w, h));
        getIcon().paintIcon(this, g, 8, (getHeight() - getIcon().getIconHeight()) / 2);
        config.restore();
      }

      private Color getButtonForeground() {
        switch (myActionId) {
          case UPDATE: return new JBColor(Gray._240, Gray._210);
          case INSTALL: return new JBColor(Gray._240, Gray._210);
          case UNINSTALL: return new JBColor(Gray._0, Gray._140);
          case RESTART:
            break;
        }

        return new JBColor(Gray._80, Gray._60);
      }

      private Paint getBackgroundPaint() {
        switch (myActionId) {
          case UPDATE: return new JBGradientPaint(this,
                                                  new JBColor(new Color(98, 158, 225), new Color(98, 158, 225)),
                                                  new JBColor(new Color(58, 91, 181), new Color(58, 91, 181)));
          case INSTALL: return new JBGradientPaint(this,
                                                   new JBColor(new Color(96, 204, 105), new Color(81, 149, 87)),
                                                   new JBColor(new Color(50, 101, 41), new Color(40, 70, 47)));
          case UNINSTALL: return UIUtil.isUnderDarcula()
                                 ? new JBGradientPaint(this, UIManager.getColor("Button.darcula.color1"), UIManager.getColor("Button.darcula.color2"))
                                 : Gray._240;
          case RESTART:
            break;
        }
        return Gray._238;
      }

      private Paint getBackgroundBorderPaint() {
        switch (myActionId) {
          case UPDATE: return new JBColor(new Color(166, 180, 205), Gray._85);
          case INSTALL: return new JBColor(new Color(201, 223, 201), Gray._70);
          case UNINSTALL: return new JBColor(Gray._220, Gray._100.withAlpha(180));
          case RESTART:
        }
        return Gray._208;
      }


      @Override
      public String getText() {
        switch (myActionId) {
          case UPDATE: return "Update plugin";
          case INSTALL: return  "Install plugin";
          case UNINSTALL: return "Uninstall plugin";
          case RESTART: return "Restart " + ApplicationNamesInfo.getInstance().getFullProductName();
        }
        return super.getText();
      }

      @Override
      public Icon getIcon() {
        switch (myActionId) {
          case UPDATE: return AllIcons.General.DownloadPlugin;
          case INSTALL: return  AllIcons.General.DownloadPlugin;
          case UNINSTALL: return AllIcons.Actions.Delete;
          case RESTART: return AllIcons.Actions.Restart;
        }
        return super.getIcon();

      }
    };
    myInstallButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        switch (myActionId) {
          case UPDATE:
          case INSTALL:
            new ActionInstallPlugin(myManager.getAvailable(), myManager.getInstalled()).install(new Runnable() {
              @Override
              public void run() {
                setPlugin(myPlugin);
              }
            });
            break;
          case UNINSTALL:
            //try {
              UninstallPluginAction.uninstall(myManager.getInstalled(), myPlugin);
            //}
            //catch (IOException e1) {
            //  e1.printStackTrace();
            //}
            break;
          case RESTART:
            if (myManager != null) {
              myManager.apply();
            }
            break;
        }
        setPlugin(myPlugin);
      }
    });
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

  public JButton getInstallButton() {
    return myInstallButton;
  }

  public JPanel getPanel() {
    return myRoot;
  }
}
