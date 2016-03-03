/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.util.concurrency.BoundedTaskExecutor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.PooledThreadExecutor;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public class CustomizeFeaturedPluginsStepPanel extends AbstractCustomizeWizardStep {
  private static final int COLS = 3;
  private static final ExecutorService ourService = new BoundedTaskExecutor(PooledThreadExecutor.INSTANCE, 4);

  public final AtomicBoolean myCanceled = new AtomicBoolean(false);
  private final PluginGroups myPluginGroups;
  private final JLabel myInProgressLabel;

  public CustomizeFeaturedPluginsStepPanel(PluginGroups pluginGroups) {
    setLayout(new GridLayout(1, 1));
    add(myInProgressLabel = new JLabel("Loading...", SwingConstants.CENTER));
    myPluginGroups = pluginGroups;
    myPluginGroups.setLoadingCallback(new Runnable() {
      @Override
      public void run() {
        onPluginGroupsLoaded();
      }
    });
  }

  private void onPluginGroupsLoaded() {
    List<IdeaPluginDescriptor> pluginsFromRepository = myPluginGroups.getPluginsFromRepository();
    if (pluginsFromRepository.isEmpty()) {
      myInProgressLabel.setText("Cannot get featured plugins description online.");
      return;
    }
    removeAll();
    JPanel gridPanel = new JPanel(new GridLayout(0, 3));
    JBScrollPane scrollPane = CustomizePluginsStepPanel.createScrollPane(gridPanel);

    Map<String, String> config = myPluginGroups.getFeaturedPlugins();
    for (Map.Entry<String, String> entry : config.entrySet()) {
      JPanel groupPanel = new JPanel(new GridBagLayout());
      GridBagConstraints gbc = new GridBagConstraints();
      gbc.fill = GridBagConstraints.BOTH;
      gbc.anchor = GridBagConstraints.WEST;
      gbc.gridwidth = GridBagConstraints.REMAINDER;
      gbc.weightx = 1;

      String title = entry.getKey();
      String s = entry.getValue();
      int i = s.indexOf(':');
      String topic = s.substring(0, i);
      int j = s.indexOf(':', i + 1);
      final String description = s.substring(i + 1, j);
      final String pluginId = s.substring(j + 1);
      IdeaPluginDescriptor foundDescriptor = null;
      for (IdeaPluginDescriptor descriptor : pluginsFromRepository) {
        if (descriptor.getPluginId().getIdString().equals(pluginId) && !PluginManagerCore.isBrokenPlugin(descriptor)) {
          foundDescriptor = descriptor;
          break;
        }
      }
      if (foundDescriptor == null) continue;
      final IdeaPluginDescriptor descriptor = foundDescriptor;



      final boolean isVIM = PluginGroups.IDEA_VIM_PLUGIN_ID.equals(descriptor.getPluginId().getIdString());

      JLabel titleLabel = new JLabel("<html><body><h2 style=\"text-align:left;\">" + title + "</h2></body></html>");
      JLabel topicLabel = new JLabel("<html><body><h4 style=\"text-align:left;\">" + topic + "</h4></body></html>");

      JLabel descriptionLabel = createHTMLLabel("<i>" + description + "</i>");
      JLabel warningLabel = null;
      if (isVIM) {
        warningLabel = createHTMLLabel("Recommended only if you are<br> familiar with Vim.");
        warningLabel.setIcon(AllIcons.General.BalloonWarning);

        if (!SystemInfo.isWindows) UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, warningLabel);
      }

      final CardLayout wrapperLayout = new CardLayout();
      final JPanel buttonWrapper = new JPanel(wrapperLayout);
      final JButton installButton = new JButton(isVIM ? "Install and Enable" : "Install");

      final JProgressBar progressBar = new JProgressBar(0, 100);
      progressBar.setStringPainted(true);
      JPanel progressPanel = new JPanel(new VerticalFlowLayout(true, false));
      progressPanel.add(progressBar);
      final LinkLabel cancelLink = new LinkLabel("Cancel", AllIcons.Actions.Cancel);
      JPanel linkWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
      linkWrapper.add(cancelLink);
      progressPanel.add(linkWrapper);

      final JPanel buttonPanel = new JPanel(new VerticalFlowLayout(0, 0));
      buttonPanel.add(installButton);

      buttonWrapper.add(buttonPanel, "button");
      buttonWrapper.add(progressPanel, "progress");

      wrapperLayout.show(buttonWrapper, "button");

      final ProgressIndicatorEx indicator = new AbstractProgressIndicatorExBase(true) {
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
              progressBar.setString("0%");
            }
          });
        }
      };
      installButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          wrapperLayout.show(buttonWrapper, "progress");
          ourService.execute(new Runnable() {
            @Override
            public void run() {
              try {
                indicator.start();
                PluginDownloader downloader = PluginDownloader.createDownloader(descriptor);
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
          });
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
      gbc.insets.bottom = SMALL_GAP;
      groupPanel.add(topicLabel, gbc);
      groupPanel.add(descriptionLabel, gbc);
      gbc.weighty = 1;
      groupPanel.add(Box.createVerticalGlue(), gbc);
      gbc.weighty = 0;
      if (warningLabel != null) {
        Insets insetsBefore = gbc.insets;
        gbc.insets = new Insets(0, -10, SMALL_GAP, -10);
        JPanel warningPanel = new JPanel(new BorderLayout());
        warningPanel.setBorder(new EmptyBorder(5, 10, 5, 10));
        warningPanel.add(warningLabel);

        groupPanel.add(warningPanel, gbc);
        gbc.insets = insetsBefore;
      }

      gbc.insets.bottom = 0;
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
        }, BorderFactory.createEmptyBorder(0, SMALL_GAP, 0, SMALL_GAP)));
      cursor++;
    }

    add(scrollPane);
    revalidate();
    repaint();
  }

  @NotNull
  private static JLabel createHTMLLabel(final String text) {
    return new JLabel("<html><body>" + text + "</body></html>") {
      @Override
      public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        size.width = Math.min(size.width, 200);
        return size;
      }
    };
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
    return "New plugins can also be downloaded in "
           + CommonBundle.settingsTitle()
           + " | " + "Plugins";
  }
}
