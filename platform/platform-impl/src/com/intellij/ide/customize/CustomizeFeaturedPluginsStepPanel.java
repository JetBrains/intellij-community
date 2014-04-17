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
package com.intellij.ide.customize;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginNode;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ConcurrencyUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class CustomizeFeaturedPluginsStepPanel extends AbstractCustomizeWizardStep {
  private static ScheduledExecutorService ourService = new ScheduledThreadPoolExecutor(4, ConcurrencyUtil.newNamedThreadFactory("FeaturedPlugins", true, Thread.NORM_PRIORITY));

  public CustomizeFeaturedPluginsStepPanel() {
    setLayout(new GridLayout(0, 3, GAP, GAP));
    JPanel gridPanel = new JPanel(new GridLayout(0, 3, GAP, GAP));
    JBScrollPane scrollPane =
      new JBScrollPane(gridPanel, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.getVerticalScrollBar().setUnitIncrement(10);

    Map<String, String> config = PluginGroups.getInstance().getFeaturedPlugins();
    for (Map.Entry<String, String> entry : config.entrySet()) {
      JPanel groupPanel = new JPanel(new GridBagLayout());
      GridBagConstraints gbc = new GridBagConstraints();
      gbc.insets = new Insets(0, 0, 10, 0);
      gbc.fill = GridBagConstraints.BOTH;
      gbc.anchor = GridBagConstraints.WEST;
      gbc.gridwidth = GridBagConstraints.REMAINDER;
      gbc.weightx = 1;

      String title = entry.getKey();
      String s = entry.getValue();
      int i = s.indexOf(":");
      String topic = s.substring(0, i);
      int j = s.indexOf(":", i + 1);
      final String description = s.substring(i + 1, j);
      final String pluginId = s.substring(j + 1);
      IdeaPluginDescriptor foundDescriptor = null;
      List<IdeaPluginDescriptor> pluginsFromRepository = PluginGroups.getInstance().getPluginsFromRepository();
      for (IdeaPluginDescriptor descriptor : pluginsFromRepository) {
        if (descriptor.getPluginId().getIdString().equals(pluginId)) {
          foundDescriptor = descriptor;
          break;
        }
      }
      if (foundDescriptor == null) continue;
      final IdeaPluginDescriptor descriptor = foundDescriptor;



      JLabel titleLabel = new JLabel("<html><body><h2 style=\"text-align:left;\">" + title + "</h2></body></html>");
      JLabel topicLabel = new JLabel("<html><body><h4 style=\"text-align:left;\">" + topic + "</h4></body></html>");
      JLabel descriptionLabel = new JLabel("<html><body><i>" + description + "</i></body></html>") {
        @Override
        public Dimension getPreferredSize() {
          Dimension size = super.getPreferredSize();
          size.width = Math.min(size.width, 200);
          return size;
        }
      };
      final CardLayout wrapperLayout = new CardLayout();
      final JPanel buttonWrapper = new JPanel(wrapperLayout);
      final JButton installButton = new JButton("Install", AllIcons.General.DownloadPlugin);
      final JProgressBar progressBar = new JProgressBar(0, 100);
      progressBar.setStringPainted(true);
      buttonWrapper.add(installButton, "button");
      buttonWrapper.add(progressBar, "progress");
      wrapperLayout.show(buttonWrapper, "button");

      final ProgressIndicatorBase indicator = new ProgressIndicatorBase(true) {
        @Override
        public void start() {
          super.start();
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              wrapperLayout.show(buttonWrapper, "progress");
            }
          });
        }

        @Override
        public void setIndeterminate(boolean indeterminate) {
          super.setIndeterminate(indeterminate);
        }

        @Override
        public void processFinish() {
          super.processFinish();
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              progressBar.setString("Installed");
            }
          });
        }

        @Override
        public void setFraction(final double fraction) {
          super.setFraction(fraction);
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              int value = (int)(100 * fraction + .5);
              progressBar.setValue(value);
              progressBar.setString(value + "%");
            }
          });
        }

        @Override
        public void cancel() {
          super.cancel();
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              wrapperLayout.show(buttonWrapper, "button");
            }
          });
        }
      };
      installButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          wrapperLayout.show(buttonWrapper, "progress");
          ourService.schedule(new Runnable() {
            @Override
            public void run() {
              try {
                PluginNode node = new PluginNode(descriptor.getPluginId());
                node.setUrl(descriptor.getUrl());
                PluginDownloader downloader = PluginDownloader.createDownloader(node);
                downloader.prepareToInstall(indicator);
                downloader.install();
                indicator.processFinish();
              }
              catch (Exception ignored) {
                onFail();
              }
            }

            void onFail() {
              //noinspection SSBasedInspection
              SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                  wrapperLayout.show(buttonWrapper, "progress");
                  progressBar.setString("Cannot download plugin");
                }
              });
            }
          }, 0, TimeUnit.SECONDS);
          //PluginGroups.getInstance().setFeaturedPluginEnabled(pluginId, installButton.isSelected());
        }
      });
      groupPanel.add(titleLabel, gbc);
      groupPanel.add(topicLabel, gbc);
      groupPanel.add(descriptionLabel, gbc);
      gbc.weighty = 1;
      groupPanel.add(Box.createVerticalGlue(), gbc);
      gbc.weighty = 0;
      groupPanel.add(buttonWrapper, gbc);
      gbc.weighty = 1;
      groupPanel.add(Box.createVerticalGlue(), gbc);
      groupPanel.setBorder(BorderFactory.createEmptyBorder(GAP, GAP, GAP, GAP));
      gridPanel.add(groupPanel);
    }
    setLayout(new GridLayout(1, 1));
    add(scrollPane);
  }

  @Override
  public String getTitle() {
    return "Featured plugins";
  }

  @Override
  public String getHTMLHeader() {
    return "<html><body><h2>Download featured plugins</h2>" +
           "We have a few plugins in our repository that most users like to download." +
           "Perhaps, you need them too?</body></html>";
  }

  @Override
  public String getHTMLFooter() {
    return "New plugins can also be downloaded in " + CommonBundle.settingsTitle() + " | Plugins";
  }
}
