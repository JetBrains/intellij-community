// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.RoundedActionButton;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.options.newEditor.SettingsDialog;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Locale;

/**
 * @author Konstantin Bulenkov
 */
public class PluginHeaderPanel {
  private static final InstalledPluginsState ourState = InstalledPluginsState.getInstance();

  private final PluginManagerMain myManager;

  private IdeaPluginDescriptor myPlugin;
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

  public PluginHeaderPanel(@Nullable PluginManagerMain manager) {
    myManager = manager;
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
    myButtonPanel.setVisible(true);
    myUpdated.setVisible(true);
    myName.setFont(UIUtil.getLabelFont().deriveFont(4f + UIUtil.getLabelFont().getSize()));

    //data
    myName.setText("<html><body>" + plugin.getName() + "</body></html>");
    myCategory.setText(plugin.getCategory() == null ? "UNKNOWN" : plugin.getCategory().toUpperCase(Locale.US));
    final boolean hasNewerVersion = ourState.hasNewerVersion(plugin.getPluginId());
    String versionText;
    boolean showVersion = !plugin.isBundled() || plugin.allowBundledUpdate();
    if (plugin instanceof PluginNode) {
      final PluginNode node = (PluginNode)plugin;
      myRating.setRate(node.getRating());
      myDownloads.setText(node.getDownloads() + " downloads");
      versionText = showVersion ? "v" + node.getVersion() : null;
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
      if ((PluginManagerColumnInfo.isDownloaded(node)) ||
          (installed != null && ourState.wasUpdated(installed.getPluginId())) ||
          (installed instanceof IdeaPluginDescriptorImpl && !plugin.isBundled() && ((IdeaPluginDescriptorImpl)installed).isDeleted())) {
        myActionId = ACTION_ID.RESTART;
      }
    }
    else {
      myActionId = null;
      myVersionInfoPanel.remove(myUpdated);
      myCategory.setVisible(false);
      myDownloadsPanel.setVisible(false);
      final String version = plugin.getVersion();
      if (ourState.wasUpdated(plugin.getPluginId())) {
        versionText = "New version will be available after restart";
      }
      else if (version != null && showVersion) {
        versionText = "Version: " + version;
      }
      else {
        versionText = null;
      }
      myUpdated.setVisible(false);
      if (ourState.wasUpdated(plugin.getPluginId()) || ourState.wasInstalled(plugin.getPluginId())) {
        myActionId = ACTION_ID.RESTART;
      }
      else if (!plugin.isBundled() || hasNewerVersion) {
        if (((IdeaPluginDescriptorImpl)plugin).isDeleted()) {
          myActionId = ACTION_ID.RESTART;
        }
        else if (hasNewerVersion) {
          myActionId = ACTION_ID.UPDATE;
        }
        else {
          myActionId = ACTION_ID.UNINSTALL;
        }
      }
      if (myActionId == ACTION_ID.RESTART && myManager != null && !myManager.isRequireShutdown()) {
        myActionId = null;
      }
    }
    myVersion.setVisible(versionText != null);
    myVersion.setText(StringUtil.notNullize(versionText));
    UIUtil.setEnabled(myButtonPanel, true, true);
    if (myManager == null || myActionId == null || (myManager.getInstalled() != myManager.getAvailable() && myActionId == ACTION_ID.UNINSTALL)) {
      myActionId = ACTION_ID.INSTALL;
      myButtonPanel.setVisible(false);
    }
    else if (InstallPluginAction.isInstalling(plugin)) {
      UIUtil.setEnabled(myButtonPanel, false, true);
    }
    myRoot.revalidate();
    myInstallButton.getParent().revalidate();
    myInstallButton.revalidate();
    myVersion.getParent().revalidate();
    myVersion.revalidate();
  }

  private void createUIComponents() {
    myInstallButton = new RoundedActionButton(2, 8) {

      @NotNull
      protected Color getButtonForeground() {
        switch (myActionId) {
          case UPDATE: return new JBColor(Gray._240, Gray._210);
          case INSTALL: return new JBColor(Gray._240, Gray._210);
          case RESTART:
          case UNINSTALL: return new JBColor(Gray._0, Gray._210);
        }
        return new JBColor(Gray._80, Gray._60);
      }

      @NotNull
      protected Paint getBackgroundPaint() {
        switch (myActionId) {
          case UPDATE: return ColorUtil.mix(new JBColor(0x629ee1, 0x629ee1), new JBColor(0x3a5bb5, 0x3a5bb5), 0.5);
          case INSTALL: return ColorUtil.mix(new JBColor(0x60cc69, 0x519557), new JBColor(0x326529, 0x28462f), 0.5);
          case RESTART:
          case UNINSTALL:
            return UIUtil.isUnderDarcula()
                   ? ColorUtil.mix(UIManager.getColor("Button.darcula.startColor"), UIManager.getColor("Button.darcula.endColor"), 0.5)
                   : Gray._240;
        }
        return Gray._238;
      }

      @NotNull
      protected Paint getBackgroundBorderPaint() {
        switch (myActionId) {
          case UPDATE: return new JBColor(new Color(0xa6b4cd), Gray._85);
          case INSTALL: return new JBColor(new Color(201, 223, 201), Gray._70);
          case RESTART:
          case UNINSTALL: return new JBColor(Gray._220, Gray._100.withAlpha(180));
        }
        return Gray._208;
      }


      @Override
      public String getText() {
        switch (myActionId) {
          case UPDATE: return "Update";
          case INSTALL: return "Install";
          case UNINSTALL: return "Uninstall";
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

    myInstallButton.addActionListener(e -> {
      switch (myActionId) {
        case UPDATE:
        case INSTALL:
          Runnable setPlugin = () -> setPlugin(myPlugin);
          new InstallPluginAction(myManager.getAvailable(), myManager.getInstalled()).install(setPlugin, setPlugin, true);
          break;
        case UNINSTALL:
          UninstallPluginAction.uninstall(myManager.getInstalled(), true, myPlugin);
          break;
        case RESTART:
          if (myManager != null) {
            myManager.apply();
          }
          final DialogWrapper dialog =
            DialogWrapper.findInstance(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
          if (dialog != null && dialog.isModal()) {
            dialog.close(DialogWrapper.OK_EXIT_CODE);
          }
          IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
            DialogWrapper settings = DialogWrapper.findInstance(IdeFocusManager.findInstance().getFocusOwner());
            if (settings instanceof SettingsDialog) {
              ((SettingsDialog)settings).doOKAction();
            }
            ApplicationManager.getApplication().restart();
          }, ModalityState.current());
          break;
      }
      setPlugin(myPlugin);
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