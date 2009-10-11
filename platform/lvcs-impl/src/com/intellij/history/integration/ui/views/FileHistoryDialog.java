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

import com.intellij.history.core.LocalVcs;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.ui.models.EntireFileHistoryDialogModel;
import com.intellij.history.integration.ui.models.FileHistoryDialogModel;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.DiffPanel;
import com.intellij.openapi.diff.ex.DiffPanelEx;
import com.intellij.openapi.diff.ex.DiffPanelOptions;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.awt.*;

public class FileHistoryDialog extends HistoryDialog<FileHistoryDialogModel> {
  private DiffPanel myDiffPanel;

  public FileHistoryDialog(IdeaGateway gw, VirtualFile f) {
    this(gw, f, true);
  }

  protected FileHistoryDialog(IdeaGateway gw, VirtualFile f, boolean doInit) {
    super(gw, f, doInit);
  }

  @Override
  protected void dispose() {
    myDiffPanel.dispose();
    super.dispose();
  }

  @Override
  protected FileHistoryDialogModel createModel(LocalVcs vcs) {
    return new EntireFileHistoryDialogModel(myGateway, vcs, myFile);
  }

  @Override
  protected Dimension getInitialSize() {
    Dimension ss = Toolkit.getDefaultToolkit().getScreenSize();
    return new Dimension(ss.width - 40, ss.height - 40);
  }

  @Override
  protected JComponent createDiffPanel() {
    myDiffPanel = DiffManager.getInstance().createDiffPanel(getWindow(), getProject());
    DiffPanelOptions o = ((DiffPanelEx)myDiffPanel).getOptions();
    o.setRequestFocusOnNewContent(false);

    updateDiffs();

    return myDiffPanel.getComponent();
  }

  @Override
  protected void updateDiffs() {
    myDiffPanel.setDiffRequest(createDifference(myModel.getDifferenceModel()));
  }

  @Override
  protected String getHelpId() {
    return "reference.dialogs.showhistory";
  }
}
