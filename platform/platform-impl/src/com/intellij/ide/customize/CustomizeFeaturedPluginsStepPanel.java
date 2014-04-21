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
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.util.ConcurrencyUtil;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class CustomizeFeaturedPluginsStepPanel extends AbstractCustomizeWizardStep {
  private static final int COLS = 3;
  private static ScheduledExecutorService ourService = new ScheduledThreadPoolExecutor(4, ConcurrencyUtil.newNamedThreadFactory(
    "FeaturedPlugins", true, Thread.NORM_PRIORITY));

  public final AtomicBoolean myCanceled = new AtomicBoolean(false);


  public CustomizeFeaturedPluginsStepPanel() throws OfflineException {
    setLayout(new GridLayout(1, 1));
    JPanel gridPanel = new JPanel(new GridLayout(0, 3));
    JBScrollPane scrollPane =
      new JBScrollPane(gridPanel, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.getVerticalScrollBar().setUnitIncrement(10);

    Map<String, String> config = PluginGroups.getInstance().getFeaturedPlugins();
    boolean isEmptyOrOffline = true;
    List<IdeaPluginDescriptor> pluginsFromRepository = PluginGroups.getInstance().getPluginsFromRepository();
    for (Map.Entry<String, String> entry : config.entrySet()) {
      JPanel groupPanel = new JPanel(new GridBagLayout());
      GridBagConstraints gbc = new GridBagConstraints();
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
      for (IdeaPluginDescriptor descriptor : pluginsFromRepository) {
        if (descriptor.getPluginId().getIdString().equals(pluginId)) {
          foundDescriptor = descriptor;
          isEmptyOrOffline = false;
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
      final JButton installButton = new JButton("Install");
      final JProgressBar progressBar = new JProgressBar(0, 100);
      progressBar.setStringPainted(true);
      JPanel progressPanel = new JPanel(new VerticalFlowLayout(true, false));
      progressPanel.add(progressBar);
      final LinkLabel cancelLink = new LinkLabel("Cancel", AllIcons.Actions.Cancel);
      JPanel linkWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER));
      linkWrapper.add(cancelLink);
      progressPanel.add(linkWrapper);

      JPanel buttonPanel = new JPanel(new VerticalFlowLayout());
      buttonPanel.add(installButton);

      buttonWrapper.add(buttonPanel, "button");
      buttonWrapper.add(progressPanel, "progress");

      wrapperLayout.show(buttonWrapper, "button");

      final ProgressIndicatorBase indicator = new ProgressIndicatorBase(true) {

        @Override
        public void start() {
          myCanceled.set(false);
          super.start();
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              wrapperLayout.show(buttonWrapper, "progress");
            }
          });
        }

        @Override
        public void processFinish() {
          super.processFinish();
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              wrapperLayout.show(buttonWrapper, "button");
              installButton.setEnabled(false);
              installButton.setText("Installed");
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
          stop();
          myCanceled.set(true);
          super.cancel();
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              wrapperLayout.show(buttonWrapper, "button");
              progressBar.setValue(0);
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
                indicator.start();
                PluginNode node = new PluginNode(descriptor.getPluginId());
                node.setUrl(descriptor.getUrl());
                PluginDownloader downloader = PluginDownloader.createDownloader(node);
                downloader.prepareToInstall(indicator);
                downloader.install();
                indicator.processFinish();
              }
              catch (Exception ignored) {
                if (!myCanceled.get()) {
                  onFail();
                }
              }
            }

            void onFail() {
              //noinspection SSBasedInspection
              SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                  indicator.stop();
                  wrapperLayout.show(buttonWrapper, "progress");
                  progressBar.setString("Cannot download plugin");
                }
              });
            }
          }, 0, TimeUnit.SECONDS);
        }
      });
      cancelLink.setListener(new LinkListener() {
        @Override
        public void linkSelected(LinkLabel aSource, Object aLinkData) {
          indicator.cancel();
        }
      }, null);
      gbc.insets.bottom = -5;
      groupPanel.add(titleLabel, gbc);
      gbc.insets.bottom = 10;
      groupPanel.add(topicLabel, gbc);
      groupPanel.add(descriptionLabel, gbc);
      gbc.weighty = 1;
      groupPanel.add(Box.createVerticalGlue(), gbc);
      gbc.weighty = 0;
      groupPanel.add(buttonWrapper, gbc);
      gridPanel.add(groupPanel);
    }
    int cursor = 0;
    Component[] components = gridPanel.getComponents();
    int rowCount = components.length / COLS;
    for (Component component : components) {
      ((JComponent)component).setBorder(
        new CompoundBorder(new CustomLineBorder(ColorUtil.withAlpha(JBColor.foreground(), .2), 0, 0, cursor / 3 < rowCount ? 1 : 0,
                                                cursor % COLS != COLS - 1 ? 1 : 0) {
          @Override
          protected Color getColor() {
            return ColorUtil.withAlpha(JBColor.foreground(), .2);
          }
        }, BorderFactory.createEmptyBorder(GAP, GAP, 0, GAP)));
      cursor++;
    }

    if (isEmptyOrOffline) throw new OfflineException();
    add(scrollPane);
  }

  @Override
  public String getTitle() {
    return "Featured plugins";
  }

  @Override
  public String getHTMLHeader() {
    return "<html><body><h2>Download featured plugins</h2>" +
           "We have a few plugins in our repository that most users like to download. " +
           "Perhaps, you need them too?</body></html>";
  }

  @Override
  public String getHTMLFooter() {
    return "New plugins can also be downloaded in " + CommonBundle.settingsTitle() + " | Plugins";
  }

  static class OfflineException extends Exception {};
}
