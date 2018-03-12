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
import com.intellij.ide.plugins.PluginNode;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public class CustomizeFeaturedPluginsStepPanel extends AbstractCustomizeWizardStep {
  private static final int COLS = 3;
  private static final ExecutorService ourService = AppExecutorUtil.createBoundedApplicationPoolExecutor(
    "CustomizeFeaturedPluginsStepPanel Pool", 4);

  public final AtomicBoolean myCanceled = new AtomicBoolean(false);
  private final PluginGroups myPluginGroups;
  private final JLabel myInProgressLabel;

  public CustomizeFeaturedPluginsStepPanel(PluginGroups pluginGroups) {
    setLayout(new GridLayout(1, 1));
    add(myInProgressLabel = new JLabel("Loading...", SwingConstants.CENTER));
    myPluginGroups = pluginGroups;
    myPluginGroups.setLoadingCallback(() -> onPluginGroupsLoaded());
  }

  private void onPluginGroupsLoaded() {
    Map<String, IdeaPluginDescriptor> pluginsFromRepository = ContainerUtil.map2Map(myPluginGroups.getPluginsFromRepository(),
                                                                                    descriptor ->
                                                                                      Pair.create(descriptor.getPluginId().getIdString(),
                                                                                                  descriptor));
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
      String description = s.substring(i + 1, j);
      final String pluginId = s.substring(j + 1);
      IdeaPluginDescriptor foundDescriptor = pluginsFromRepository.get(pluginId);
      if (foundDescriptor == null || PluginManagerCore.isBrokenPlugin(foundDescriptor)) {
        continue;
      }
      final IdeaPluginDescriptor descriptor = foundDescriptor;

      List<PluginId> dependentPluginIds;
      if (descriptor instanceof PluginNode) {
        dependentPluginIds = ContainerUtil
          .filter(ContainerUtil.notNullize(((PluginNode)descriptor).getDepends()), id -> !id.getIdString().startsWith("(optional)"));
      }
      else {
        dependentPluginIds = Arrays.asList(descriptor.getDependentPluginIds());
      }
      List<IdeaPluginDescriptor> dependentDescriptors = new ArrayList<>(dependentPluginIds.size());
      boolean failedToFindDependencies = false;
      for (PluginId id : dependentPluginIds) {
        if (PluginManagerCore.isModuleDependency(id) || myPluginGroups.findPlugin(id.getIdString()) != null) {
          continue;
        }
        IdeaPluginDescriptor dependentDescriptor = pluginsFromRepository.get(id.getIdString());
        if (dependentDescriptor == null || PluginManagerCore.isBrokenPlugin(dependentDescriptor)) {
          failedToFindDependencies = true;
          break;
        }
        dependentDescriptors.add(dependentDescriptor);
      }
      if (failedToFindDependencies) {
        continue;
      }

      final boolean isVIM = PluginGroups.IDEA_VIM_PLUGIN_ID.equals(descriptor.getPluginId().getIdString());
      boolean isCloud = "#Cloud".equals(topic);

      if (isCloud) {
        title = descriptor.getName();
        description = StringUtil.defaultIfEmpty(descriptor.getDescription(), "No description available");
        topic = StringUtil.defaultIfEmpty(descriptor.getCategory(), "Unknown");
      }

      JLabel titleLabel = new JLabel("<html><body><h2 style=\"text-align:left;\">" + title + "</h2></body></html>");
      JLabel topicLabel = new JLabel("<html><body><h4 style=\"text-align:left;color:#808080;font-weight:bold;\">" + topic + "</h4></body></html>");

      JLabel descriptionLabel = createHTMLLabel(description);

      StringBuilder dependenciesLabelText = new StringBuilder();
      if (dependentDescriptors.size() > 1) {
        dependenciesLabelText.append("With dependencies: ");
      }
      else if (dependentDescriptors.size() == 1) {
        dependenciesLabelText.append("With dependency: ");
      }
      for (int k = 0; k < dependentDescriptors.size(); k++) {
        IdeaPluginDescriptor dependentDescriptor = dependentDescriptors.get(k);
        if (k > 0) {
          dependenciesLabelText.append(", ");
        }
        dependenciesLabelText.append(dependentDescriptor.getName());
      }
      JLabel dependenciesLabel = createHTMLLabel(dependenciesLabelText.toString());
      if (!SystemInfo.isWindows) UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, dependenciesLabel);

      JLabel warningLabel = null;
      if (isVIM || isCloud) {
        if (isCloud) {
          warningLabel = createHTMLLabel("From your JetBrains account");
          warningLabel.setIcon(AllIcons.General.BalloonInformation);
        }
        else {
          warningLabel = createHTMLLabel("Recommended only if you are<br> familiar with Vim.");
          warningLabel.setIcon(AllIcons.General.BalloonWarning);
        }

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

      final MyIndicator indicator =
        new MyIndicator(wrapperLayout, buttonWrapper, installButton, progressBar, myCanceled, 1 + dependentDescriptors.size());
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
                for (IdeaPluginDescriptor dependentDescriptor : dependentDescriptors) {
                  indicator.nextDownload();
                  downloader = PluginDownloader.createDownloader(dependentDescriptor);
                  downloader.prepareToInstall(indicator);
                  downloader.install();
                }
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
              SwingUtilities.invokeLater(() -> {
                indicator.stop();
                wrapperLayout.show(buttonWrapper, "progress");
                progressBar.setString("Cannot download plugin");
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
      gbc.insets.left = installButton.getInsets().left / 2;
      gbc.insets.right = installButton.getInsets().right / 2;
      gbc.insets.bottom = -5;
      groupPanel.add(titleLabel, gbc);
      gbc.insets.bottom = SMALL_GAP;
      groupPanel.add(topicLabel, gbc);
      groupPanel.add(descriptionLabel, gbc);
      gbc.weighty = 1;
      groupPanel.add(Box.createVerticalGlue(), gbc);
      groupPanel.add(dependenciesLabel, gbc);
      gbc.weighty = 0;
      if (warningLabel != null) {
        Insets insetsBefore = gbc.insets;
        gbc.insets = new Insets(0, -10, SMALL_GAP, -10);
        gbc.insets.left += insetsBefore.left;
        gbc.insets.right += insetsBefore.right;
        JPanel warningPanel = new JPanel(new BorderLayout());
        warningPanel.setBorder(new EmptyBorder(5, 10, 5, 10));
        warningPanel.add(warningLabel);

        groupPanel.add(warningPanel, gbc);
        gbc.insets = insetsBefore;
      }

      gbc.insets.bottom = gbc.insets.left = gbc.insets.right = 0;
      groupPanel.add(buttonWrapper, gbc);
      gridPanel.add(groupPanel);
    }
    while (gridPanel.getComponentCount() < 4) {
      gridPanel.add(Box.createVerticalBox());
    }
    int cursor = 0;
    Component[] components = gridPanel.getComponents();
    int rowCount = components.length / COLS;
    for (Component component : components) {
      ((JComponent)component).setBorder(
        new CompoundBorder(new CustomLineBorder(ColorUtil.withAlpha(JBColor.foreground(), .2), 0, 0,
                                                cursor / 3 < rowCount && (!(component instanceof Box)) ? 1 : 0,
                                                cursor % COLS != COLS - 1 && (!(component instanceof Box)) ? 1 : 0) {
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

  private static class MyIndicator extends AbstractProgressIndicatorExBase {
    private final CardLayout myWrapperLayout;
    private final JPanel myButtonWrapper;
    private final JButton myInstallButton;
    private final JProgressBar myProgressBar;
    private final AtomicBoolean myCanceled;
    private final int myNumberOfDownloads;
    private int myDownload;

    public MyIndicator(CardLayout wrapperLayout,
                       JPanel buttonWrapper,
                       JButton installButton,
                       JProgressBar progressBar,
                       AtomicBoolean canceled,
                       int numberOfDownloads) {
      super(true);
      myWrapperLayout = wrapperLayout;
      myButtonWrapper = buttonWrapper;
      myInstallButton = installButton;
      myProgressBar = progressBar;
      myCanceled = canceled;
      myNumberOfDownloads = numberOfDownloads;
    }

    private void nextDownload() {
      myDownload++;
    }

    @Override
    public void start() {
      myCanceled.set(false);
      super.start();
      SwingUtilities.invokeLater(() -> myWrapperLayout.show(myButtonWrapper, "progress"));
    }

    @Override
    public void processFinish() {
      super.processFinish();
      SwingUtilities.invokeLater(() -> {
        myWrapperLayout.show(myButtonWrapper, "button");
        myInstallButton.setEnabled(false);
        myInstallButton.setText("Installed");
      });
    }

    @Override
    public void setFraction(double fraction) {
      double resultingFraction = (fraction + myDownload) / myNumberOfDownloads;
      super.setFraction(resultingFraction);
      SwingUtilities.invokeLater(() -> {
        int value = (int)(100 * resultingFraction + .5);
        myProgressBar.setValue(value);
        myProgressBar.setString(value + "%");
      });
    }

    @Override
    public void cancel() {
      stop();
      myCanceled.set(true);
      super.cancel();
      SwingUtilities.invokeLater(() -> {
        myWrapperLayout.show(myButtonWrapper, "button");
        myProgressBar.setValue(0);
        myProgressBar.setString("0%");
      });
    }
  }
}
