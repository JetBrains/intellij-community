package com.intellij.openapi.updateSettings.impl;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.List;

/**
 * @author pti
 */
class UpdateInfoDialog extends AbstractUpdateDialog {
  private final UpdateChecker.NewVersion myNewVersion;

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
    if (hasPatch()) {
      AbstractAction moreInfo = new AbstractAction(IdeBundle.message("updates.more.info.button")) {
        public void actionPerformed(ActionEvent e) {
          openDownloadPage();
        }
      };
      return new Action[]{getOKAction(), moreInfo, getCancelAction()};
    }

    return super.createActions();
  }

  private static void openDownloadPage() {
    BrowserUtil.launchBrowser(ApplicationInfoEx.getInstanceEx().getUpdateUrls().getDownloadUrl());
  }

  protected String getOkButtonText() {
    if (hasPatch()) {
      return ApplicationManager.getApplication().isRestartCapable()
             ? IdeBundle.message("updates.download.and.install.patch.button.restart")
             : IdeBundle.message("updates.download.and.install.patch.button");
    }
    else {
      return IdeBundle.message("updates.more.info.button");
    }
  }

  protected JComponent createCenterPanel() {
    UpdateInfoPanel updateInfoPanel = new UpdateInfoPanel();
    return updateInfoPanel.myPanel;
  }

  @Override
  protected void doOKAction() {
    if (hasPatch()) {
      super.doOKAction();
      return;
    }

    openDownloadPage();
    super.doOKAction();
  }

  @Override
  protected boolean doDownloadAndPrepare() {
    if (hasPatch()) {
      switch (UpdateChecker.downloadAndInstallPatch(myNewVersion)) {
        case CANCELED:
          return false;
        case FAILED:
          openDownloadPage();
          return false;
        case SUCCESS:
          super.doDownloadAndPrepare();
          return true;
      }
    }
    return super.doDownloadAndPrepare();
  }

  private boolean hasPatch() {
    return myNewVersion.findPatchForCurrentBuild() != null;
  }

  private class UpdateInfoPanel {
    private JPanel myPanel;
    private JLabel myBuildNumber;
    private JLabel myVersionNumber;
    private JLabel myNewVersionNumber;
    private JLabel myNewBuildNumber;
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

      UpdateChecker.PatchInfo patch = myNewVersion.findPatchForCurrentBuild();
      if (patch == null) {
        myPatchAvailableLabel.setVisible(false);
        myPatchSizeLabel.setVisible(false);
      }
      else {
        myPatchSizeLabel.setText(patch.getSize() + "MB");
      }
      LabelTextReplacingUtil.replaceText(myPanel);
    }
  }
}
