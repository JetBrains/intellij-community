/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.util.CommitCompareInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.vcs.changes.ui.SimpleChangesBrowser;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsFullCommitDetails;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;

/**
 * @author Kirill Likhodedov
 */
class CompareBranchesLogPanel extends JPanel {

  private final CompareBranchesHelper myHelper;
  private final String myBranchName;
  private final String myCurrentBranchName;
  private final CommitCompareInfo myCompareInfo;
  private final Repository myInitialRepo;

  private CommitListPanel<VcsFullCommitDetails> myHeadToBranchListPanel;
  private CommitListPanel<VcsFullCommitDetails> myBranchToHeadListPanel;

  public CompareBranchesLogPanel(@NotNull CompareBranchesHelper helper, @NotNull String branchName, @NotNull String currentBranchName,
                            @NotNull CommitCompareInfo compareInfo, @NotNull Repository initialRepo) {
    super(new BorderLayout(UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP));
    myHelper = helper;
    myBranchName = branchName;
    myCurrentBranchName = currentBranchName;
    myCompareInfo = compareInfo;
    myInitialRepo = initialRepo;

    add(createNorthPanel(), BorderLayout.NORTH);
    add(createCenterPanel());
  }

  private JComponent createCenterPanel() {
    final SimpleChangesBrowser changesBrowser = new SimpleChangesBrowser(myHelper.getProject(), false, true);

    myHeadToBranchListPanel = new CommitListPanel<>(getHeadToBranchCommits(myInitialRepo),
                                                    String.format("Branch %s is fully merged to %s", myBranchName, myCurrentBranchName));
    myBranchToHeadListPanel = new CommitListPanel<>(getBranchToHeadCommits(myInitialRepo),
                                                    String.format("Branch %s is fully merged to %s", myCurrentBranchName, myBranchName));

    addSelectionListener(myHeadToBranchListPanel, myBranchToHeadListPanel, changesBrowser);
    addSelectionListener(myBranchToHeadListPanel, myHeadToBranchListPanel, changesBrowser);

    myHeadToBranchListPanel.registerDiffAction(changesBrowser.getDiffAction());
    myBranchToHeadListPanel.registerDiffAction(changesBrowser.getDiffAction());

    JPanel htb = layoutCommitListPanel(true);
    JPanel bth = layoutCommitListPanel(false);

    JPanel listPanel = null;
    switch (getInfoType()) {
      case HEAD_TO_BRANCH:
        listPanel = htb;
        break;
      case BRANCH_TO_HEAD:
        listPanel = bth;
        break;
      case BOTH:
        Splitter lists = new Splitter(true, 0.5f);
        lists.setFirstComponent(htb);
        lists.setSecondComponent(bth);
        listPanel = lists;
    }

    Splitter rootPanel = new Splitter(false, 0.7f);
    rootPanel.setSecondComponent(changesBrowser);
    rootPanel.setFirstComponent(listPanel);
    return rootPanel;
  }

  private JComponent createNorthPanel() {
    final ComboBox<Repository> repoSelector = new ComboBox<>(ArrayUtil.toObjectArray(myCompareInfo.getRepositories(), Repository.class));
    repoSelector.setRenderer(new RepositoryComboboxListCellRenderer());
    repoSelector.setSelectedItem(myInitialRepo);

    repoSelector.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        Repository selectedRepo = (Repository)repoSelector.getSelectedItem();
        myHeadToBranchListPanel.setCommits(getHeadToBranchCommits(selectedRepo));
        myBranchToHeadListPanel.setCommits(getBranchToHeadCommits(selectedRepo));
      }
    });

    JPanel repoSelectorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    JBLabel label = new JBLabel("Repository: ");
    label.setLabelFor(repoSelectorPanel);
    label.setDisplayedMnemonic(KeyEvent.VK_R);
    repoSelectorPanel.add(label);
    repoSelectorPanel.add(repoSelector);

    if (myCompareInfo.getRepositories().size() < 2) {
      repoSelectorPanel.setVisible(false);
    }
    return repoSelectorPanel;
  }

  private ArrayList<VcsFullCommitDetails> getBranchToHeadCommits(Repository selectedRepo) {
    return new ArrayList<>(myCompareInfo.getBranchToHeadCommits(selectedRepo));
  }

  private ArrayList<VcsFullCommitDetails> getHeadToBranchCommits(Repository selectedRepo) {
    return new ArrayList<>(myCompareInfo.getHeadToBranchCommits(selectedRepo));
  }

  private CommitCompareInfo.InfoType getInfoType() {
    return myCompareInfo.getInfoType();
  }

  private static void addSelectionListener(@NotNull CommitListPanel<VcsFullCommitDetails> sourcePanel,
                                           @NotNull final CommitListPanel otherPanel,
                                           @NotNull final SimpleChangesBrowser changesBrowser) {
    sourcePanel.addListMultipleSelectionListener(changes -> {
      changesBrowser.setChangesToDisplay(changes);
      otherPanel.clearSelection();
    });
  }

  private JPanel layoutCommitListPanel(boolean forward) {
    String desc = makeDescription(forward);

    JPanel bth = new JPanel(new BorderLayout());
    JBLabel descriptionLabel = new JBLabel(desc, UIUtil.ComponentStyle.SMALL);
    descriptionLabel.setBorder(JBUI.Borders.emptyBottom(5));
    bth.add(descriptionLabel, BorderLayout.NORTH);
    bth.add(forward ? myHeadToBranchListPanel : myBranchToHeadListPanel);
    return bth;
  }

  private String makeDescription(boolean forward) {
    String firstBranch = forward ? myCurrentBranchName : myBranchName;
    String secondBranch = forward ? myBranchName : myCurrentBranchName;
    return String.format("<html>Commits that exist in <code><b>%s</b></code> but don't exist in <code><b>%s</b></code> (<code>%s</code>):</html>",
                         secondBranch, firstBranch, myHelper.formatLogCommand(firstBranch, secondBranch));
  }
}
