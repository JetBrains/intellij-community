// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.ui;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.util.CommitCompareInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.WindowWrapper;
import com.intellij.openapi.ui.WindowWrapperBuilder;
import com.intellij.ui.TabbedPaneImpl;
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

    CompareBranchesDiffPanel diffPanel = new CompareBranchesDiffPanel(helper.getProject(), helper.getDvcsCompareSettings(),
                                                    branchName, currentBranchName);
    diffPanel.setCompareInfo(compareInfo);
    myLogPanel = new CompareBranchesLogPanel(helper, branchName, currentBranchName, compareInfo, initialRepo);

    myTabbedPane = new TabbedPaneImpl(SwingConstants.TOP);
    myTabbedPane.addTab("Log", AllIcons.Vcs.Branch, myLogPanel);
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

