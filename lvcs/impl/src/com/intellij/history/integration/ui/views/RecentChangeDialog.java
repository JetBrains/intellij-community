package com.intellij.history.integration.ui.views;

import com.intellij.CommonBundle;
import com.intellij.history.core.ILocalVcs;
import com.intellij.history.core.revisions.RecentChange;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.LocalHistoryBundle;
import com.intellij.history.integration.ui.models.DirectoryHistoryDialogModel;
import com.intellij.history.integration.ui.models.RecentChangeDialogModel;
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

    UIUtil.setActionNameAndMnemonic(LocalHistoryBundle.message("action.revert"), revert);
    UIUtil.setActionNameAndMnemonic(CommonBundle.getCloseButtonText(), cancel);

    return new Action[]{revert, cancel, help};
  }

  @Override
  protected String getHelpId() {
    return "reference.dialogs.recentChanges";
  }
}
