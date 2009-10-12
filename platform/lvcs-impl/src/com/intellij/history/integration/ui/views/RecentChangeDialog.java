/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.history.integration.ui.views;

import com.intellij.CommonBundle;
import com.intellij.history.core.LocalVcs;
import com.intellij.history.core.revisions.RecentChange;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.LocalHistoryBundle;
import com.intellij.history.integration.ui.models.DirectoryHistoryDialogModel;
import com.intellij.history.integration.ui.models.RecentChangeDialogModel;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class RecentChangeDialog extends DirectoryHistoryDialog {
  private final RecentChange myChange;

  public RecentChangeDialog(IdeaGateway gw, RecentChange c) {
    super(gw, null, false);
    myChange = c;
    init();
  }

  @Override
  protected DirectoryHistoryDialogModel createModel(LocalVcs vcs) {
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
