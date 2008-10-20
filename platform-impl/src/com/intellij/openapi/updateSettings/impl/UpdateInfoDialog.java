package com.intellij.openapi.updateSettings.impl;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: pti
 * Date: Jun 24, 2005
 * Time: 10:26:21 PM
 * To change this template use File | Settings | File Templates.
 */

class UpdateInfoDialog extends AbstractUpdateDialog {
  private UpdateInfoPanel myUpdateInfoPanel;
  private UpdateChecker.NewVersion myNewVersion;

  protected UpdateInfoDialog(final boolean canBeParent,
                             UpdateChecker.NewVersion newVersion,
                             final List<PluginDownloader> uploadedPlugins,
                             final boolean enableLink) {
    super(canBeParent, enableLink, uploadedPlugins);
    myNewVersion = newVersion;
    setTitle(IdeBundle.message("updates.info.dialog.title"));
    init();
  }

  @Override
  protected Action[] createActions() {
    AbstractAction moreInfo = new AbstractAction(IdeBundle.message("updates.more.info.button")) {
      public void actionPerformed(ActionEvent e) {
        BrowserUtil.launchBrowser(ApplicationInfoEx.getInstanceEx().getUpdateUrls().getDownloadUrl());
        UpdateInfoDialog.super.doCancelAction();
      }
    };
    return new Action[] {getOKAction(), moreInfo, getCancelAction()};
  }

  protected String getOkButtonText() {
    return IdeBundle.message("updates.download.and.install.patch.button");
  }

  protected JComponent createCenterPanel() {
    myUpdateInfoPanel = new UpdateInfoPanel();
    return myUpdateInfoPanel.myPanel;
  }

  @Override
  protected boolean doDownloadAndPrepare() {
    return UpdateChecker.downloadAndInstallPatch(myNewVersion) || super.doDownloadAndPrepare();
  }

  private class UpdateInfoPanel {
    private JPanel myPanel;
    private JLabel myBuildNumber;
    private JLabel myVersionNumber;
    private JLabel myNewVersionNumber;
    private JLabel myNewBuildNumber;
    private JPanel myUpdatedPluginsPanel;
    private JEditorPane myEditorPane;
    private JPanel myWholePluginsPanel;
    private JLabel myPatchAvailableLabel;
    private JLabel myPatchSizeLabel;

    public UpdateInfoPanel() {

      final String build = ApplicationInfo.getInstance().getBuildNumber().trim();
      myBuildNumber.setText(build + ")");
      final String majorVersion = ApplicationInfo.getInstance().getMajorVersion();
      final String version;
      if (majorVersion != null && majorVersion.trim().length() > 0) {
        final String minorVersion = ApplicationInfo.getInstance().getMinorVersion();
        if (minorVersion != null && minorVersion.trim().length() > 0) {
          version = majorVersion + "." + minorVersion;
        }
        else {
          version = majorVersion + ".0";
        }
      }
      else {
        version = ApplicationInfo.getInstance().getVersionName();
      }

      myVersionNumber.setText(version);
      myNewBuildNumber.setText(Integer.toString(myNewVersion.getLatestBuild()) + ")");
      myNewVersionNumber.setText(myNewVersion.getLatestVersion());

      UpdateChecker.PatchInfo patch = myNewVersion.findPatchFor(build);
      if (patch == null) {
        myPatchAvailableLabel.setVisible(false);
        myPatchSizeLabel.setVisible(false);
      } else {
        myPatchSizeLabel.setText(patch.getSize() + "MB");
      }

      initPluginsPanel(myPanel, myUpdatedPluginsPanel, myWholePluginsPanel, myEditorPane);
    }
  }

}
