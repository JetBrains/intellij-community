/*
 * User: anna
 * Date: 04-Dec-2007
 */
package com.intellij.openapi.updateSettings.impl;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.util.List;

public abstract class AbstractUpdateDialog extends DialogWrapper {
  private final boolean myEnableLink;
  protected final List<PluginDownloader> myUploadedPlugins;

  protected AbstractUpdateDialog(boolean canBeParent, boolean enableLink, final List<PluginDownloader> updatePlugins) {
    super(canBeParent);
    myEnableLink = enableLink;
    myUploadedPlugins = updatePlugins;
    setButtonsText();
  }

  protected void initPluginsPanel(final JPanel panel, JPanel pluginsPanel, JEditorPane updateLinkPane) {
    pluginsPanel.setVisible(myUploadedPlugins != null);
    if (myUploadedPlugins != null) {
      final DetectedPluginsPanel foundPluginsPanel = new DetectedPluginsPanel();

      foundPluginsPanel.addStateListener(new DetectedPluginsPanel.Listener() {
        public void stateChanged() {
          setButtonsText();
        }
      });
      for (PluginDownloader uploadedPlugin : myUploadedPlugins) {
        foundPluginsPanel.add(uploadedPlugin);
      }
      pluginsPanel.add(foundPluginsPanel, BorderLayout.NORTH);
    }
    updateLinkPane.setBackground(UIUtil.getPanelBackground());
    updateLinkPane.setText(IdeBundle.message("updates.configure.label", UIUtil.getCssFontDeclaration(UIUtil.getLabelFont())));
    updateLinkPane.setEditable(false);
    LabelTextReplacingUtil.replaceText(panel);

    if (myEnableLink) {
      updateLinkPane.addHyperlinkListener(new HyperlinkListener() {
        public void hyperlinkUpdate(final HyperlinkEvent e) {
          if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            UpdateSettingsConfigurable updatesSettings = UpdateSettingsConfigurable.getInstance();
            updatesSettings.setCheckNowEnabled(false);
            ShowSettingsUtil.getInstance().editConfigurable(panel, updatesSettings);
            updatesSettings.setCheckNowEnabled(true);
          }
        }
      });
    }
  }

  private void setButtonsText() {
    boolean found = false;
    if (myUploadedPlugins != null) {
      for (PluginDownloader uploadedPlugin : myUploadedPlugins) {
        if (!UpdateChecker.getDisabledToUpdatePlugins().contains(uploadedPlugin.getPluginId())) found = true;
      }
    }
    setOKButtonText(found ? IdeBundle.message("update.plugins.shutdown.action") : getOkButtonText());
    setCancelButtonText(found ? IdeBundle.message("update.plugins.update.later.action") : CommonBundle.getCloseButtonText());
  }

  protected String getOkButtonText() {
    return CommonBundle.getOkButtonText();
  }

  protected void doOKAction() {
    if (myUploadedPlugins != null) {
      UpdateChecker.saveDisabledToUpdatePlugins();
      if (UpdateChecker.install(myUploadedPlugins)) {
        final ApplicationEx app = ApplicationManagerEx.getApplicationEx();
        app.saveAll();
        app.exit(true);
      }
    }
    super.doOKAction();
  }

  public void doCancelAction() {
    UpdateChecker.saveDisabledToUpdatePlugins();
    if (myUploadedPlugins != null) UpdateChecker.install(myUploadedPlugins); //update on restart
    super.doCancelAction();
  }
}