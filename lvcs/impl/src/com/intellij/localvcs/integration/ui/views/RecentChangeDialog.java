package com.intellij.localvcs.integration.ui.views;

import com.intellij.CommonBundle;
import com.intellij.localvcs.core.ILocalVcs;
import com.intellij.localvcs.core.revisions.RecentChange;
import com.intellij.localvcs.integration.IdeaGateway;
import com.intellij.localvcs.integration.ui.models.DirectoryHistoryDialogModel;
import com.intellij.localvcs.integration.ui.models.RecentChangeDialogModel;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class RecentChangeDialog extends DirectoryHistoryDialog {
  private RecentChange myChange;

  public RecentChangeDialog(IdeaGateway gw, RecentChange c) {
    super(gw, null, false);
    myChange = c;
    init();
  }

  @Override
  protected DirectoryHistoryDialogModel createModel(ILocalVcs vcs) {
    return new RecentChangeDialogModel(myGateway, vcs, myChange);
  }

  @Override
  protected JComponent createCenterPanel() {
    return createDiffPanel();
  }

  @Override
  protected Action[] createActions() {
    Action revert = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        revert();
      }
    };
    Action help = getHelpAction();
    Action cancel = getCancelAction();

    UIUtil.setActionNameAndMnemonic("Revert", revert);
    UIUtil.setActionNameAndMnemonic(CommonBundle.getCloseButtonText(), cancel);

    return new Action[]{revert, cancel, help};
  }

  @Override
  protected boolean showRoot() {
    return false;
  }

  @Override
  protected String getHelpId() {
    return "reference.dialogs.recentChanges";
  }
}
