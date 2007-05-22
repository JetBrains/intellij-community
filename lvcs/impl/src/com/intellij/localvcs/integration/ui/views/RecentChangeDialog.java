package com.intellij.localvcs.integration.ui.views;

import com.intellij.CommonBundle;
import com.intellij.localvcs.core.ILocalVcs;
import com.intellij.localvcs.core.revisions.RecentChange;
import com.intellij.localvcs.integration.IdeaGateway;
import com.intellij.localvcs.integration.ui.models.DirectoryHistoryDialogModel;
import com.intellij.localvcs.integration.ui.models.RecentChangeDialogModel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class RecentChangeDialog extends DirectoryHistoryDialog {
  private RecentChange myChange;

  public RecentChangeDialog(IdeaGateway gw, RecentChange c) {
    super(gw);
    myChange = c;
    init(null);
  }

  @Override
  protected DirectoryHistoryDialogModel createModelFor(VirtualFile f, ILocalVcs vcs) {
    return new RecentChangeDialogModel(vcs, myGateway, myChange);
  }

  @Override
  protected JComponent createCenterPanel() {
    return createDiffPanel();
  }

  @Override
  protected boolean shouldRootBeVisible() {
    return false;
  }

  @Override
  protected Action[] createActions() {
    Action revert = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        revert();
      }
    };
    Action cancelAction = getCancelAction();

    UIUtil.setActionNameAndMnemonic("Revert", revert);
    UIUtil.setActionNameAndMnemonic(CommonBundle.getCloseButtonText(), cancelAction);

    return new Action[]{revert, cancelAction};
  }
}
