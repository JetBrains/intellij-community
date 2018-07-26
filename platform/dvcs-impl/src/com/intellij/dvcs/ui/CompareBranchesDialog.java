/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.dvcs.ui;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.util.CommitCompareInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.WindowWrapper;
import com.intellij.openapi.ui.WindowWrapperBuilder;
import com.intellij.ui.TabbedPaneImpl;
import icons.VcsLogIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Dialog for comparing two DVCS branches.
 */
public class CompareBranchesDialog {
  @NotNull private final Project myProject;

  @NotNull private final JPanel myLogPanel;
  @NotNull private final TabbedPaneImpl myTabbedPane;
  @NotNull private final String myTitle;

  @NotNull private final WindowWrapper.Mode myMode;

  private WindowWrapper myWrapper;

  public CompareBranchesDialog(@NotNull CompareBranchesHelper helper,
                               @NotNull String branchName,
                               @NotNull String currentBranchName,
                               @NotNull CommitCompareInfo compareInfo,
                               @NotNull Repository initialRepo,
                               boolean dialog) {
    myProject = helper.getProject();

    String rootString;
    if (compareInfo.getRepositories().size() == 1 && helper.getRepositoryManager().moreThanOneRoot()) {
      rootString = " in root " + DvcsUtil.getShortRepositoryName(initialRepo);
    }
    else {
      rootString = "";
    }
    myTitle = String.format("Comparing %s with %s%s", currentBranchName, branchName, rootString);
    myMode = dialog ? WindowWrapper.Mode.MODAL : WindowWrapper.Mode.FRAME;

    JPanel diffPanel = new CompareBranchesDiffPanel(helper, branchName, currentBranchName, compareInfo);
    myLogPanel = new CompareBranchesLogPanel(helper, branchName, currentBranchName, compareInfo, initialRepo);

    myTabbedPane = new TabbedPaneImpl(SwingConstants.TOP);
    myTabbedPane.addTab("Log", VcsLogIcons.Branch, myLogPanel);
    myTabbedPane.addTab("Files", AllIcons.Actions.ListChanges, diffPanel);
    myTabbedPane.setKeyboardNavigation(TabbedPaneImpl.DEFAULT_PREV_NEXT_SHORTCUTS);
  }

  public void show() {
    if (myWrapper == null) {
      myWrapper = new WindowWrapperBuilder(myMode, myTabbedPane)
        .setProject(myProject)
        .setTitle(myTitle)
        .setPreferredFocusedComponent(myLogPanel)
        .setDimensionServiceKey(CompareBranchesDialog.class.getName())
        .build();
    }
    myWrapper.show();
  }
}

