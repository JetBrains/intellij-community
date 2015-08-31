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

import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestPanel;
import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.ui.models.EntireFileHistoryDialogModel;
import com.intellij.history.integration.ui.models.FileDifferenceModel;
import com.intellij.history.integration.ui.models.FileHistoryDialogModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ExcludingTraversalPolicy;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

public class FileHistoryDialog extends HistoryDialog<FileHistoryDialogModel> {
  private DiffRequestPanel myDiffPanel;

  public FileHistoryDialog(@NotNull Project p, IdeaGateway gw, VirtualFile f) {
    this(p, gw, f, true);
  }

  protected FileHistoryDialog(@NotNull Project p, IdeaGateway gw, VirtualFile f, boolean doInit) {
    super(p, gw, f, doInit);
  }

  @Override
  protected FileHistoryDialogModel createModel(LocalHistoryFacade vcs) {
    return new EntireFileHistoryDialogModel(myProject, myGateway, vcs, myFile);
  }

  @Override
  protected Pair<JComponent, Dimension> createDiffPanel(JPanel root, ExcludingTraversalPolicy traversalPolicy) {
    myDiffPanel = DiffManager.getInstance().createRequestPanel(myProject, this, getFrame());
    return Pair.create((JComponent)myDiffPanel.getComponent(), null);
  }

  @Override
  protected void setDiffBorder(Border border) {
  }

  @Override
  public void dispose() {
    super.dispose();
    if (myDiffPanel != null) {
      Disposer.dispose(myDiffPanel);
    }
  }

  @Override
  protected Runnable doUpdateDiffs(final FileHistoryDialogModel model) {
    final FileDifferenceModel diffModel = model.getDifferenceModel();
    return new Runnable() {
      public void run() {
        myDiffPanel.setRequest(createDifference(diffModel));
      }
    };
  }

  @Override
  protected String getHelpId() {
    return "reference.dialogs.showhistory";
  }
}
