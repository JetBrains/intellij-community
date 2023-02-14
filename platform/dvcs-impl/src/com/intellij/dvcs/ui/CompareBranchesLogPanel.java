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
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.vcs.changes.ui.SimpleChangesBrowser;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsFullCommitDetails;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * @author Kirill Likhodedov
 */
class CompareBranchesLogPanel extends JPanel {

  private final CompareBranchesHelper myHelper;
  private final @NlsSafe String myBranchName;
  private final @NlsSafe String myCurrentBranchName;
  private final CommitCompareInfo myCompareInfo;
  private final Repository myInitialRepo;

  private CommitListPanel myHeadToBranchListPanel;
  private CommitListPanel myBranchToHeadListPanel;

  CompareBranchesLogPanel(@NotNull CompareBranchesHelper helper, @NotNull String branchName, @NotNull String currentBranchName,
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

    myHeadToBranchListPanel = new CommitListPanel(
      getHeadToBranchCommits(myInitialRepo),
      DvcsBundle.message("label.branch.fully.merged.to.branch", myBranchName, myCurrentBranchName));
    myBranchToHeadListPanel = new CommitListPanel(
      getBranchToHeadCommits(myInitialRepo),
      DvcsBundle.message("label.branch.fully.merged.to.branch", myCurrentBranchName, myBranchName));

    addSelectionListener(myHeadToBranchListPanel, myBranchToHeadListPanel, changesBrowser);
    addSelectionListener(myBranchToHeadListPanel, myHeadToBranchListPanel, changesBrowser);

    myHeadToBranchListPanel.registerDiffAction(changesBrowser.getDiffAction());
    myBranchToHeadListPanel.registerDiffAction(changesBrowser.getDiffAction());

    JPanel htb = layoutCommitListPanel(true);
    JPanel bth = layoutCommitListPanel(false);

    JPanel listPanel = switch (getInfoType()) {
      case HEAD_TO_BRANCH -> htb;
      case BRANCH_TO_HEAD -> bth;
      case BOTH -> {
        Splitter lists = new Splitter(true, 0.5f);
        lists.setFirstComponent(htb);
        lists.setSecondComponent(bth);
        yield lists;
      }
    };

    Splitter rootPanel = new Splitter(false, 0.7f);
    rootPanel.setSecondComponent(changesBrowser);
    rootPanel.setFirstComponent(listPanel);
    return rootPanel;
  }

  private JComponent createNorthPanel() {
    final ComboBox<Repository> repoSelector = new ComboBox<>(myCompareInfo.getRepositories().toArray(new Repository[0]));
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
    JBLabel label = new JBLabel(DvcsBundle.message("label.repository") + " ");
    label.setLabelFor(repoSelectorPanel);
    label.setDisplayedMnemonic(KeyEvent.VK_R);
    repoSelectorPanel.add(label);
    repoSelectorPanel.add(repoSelector);

    if (myCompareInfo.getRepositories().size() < 2) {
      repoSelectorPanel.setVisible(false);
    }
    return repoSelectorPanel;
  }

  private List<VcsFullCommitDetails> getBranchToHeadCommits(Repository selectedRepo) {
    return myCompareInfo.getBranchToHeadCommits(selectedRepo);
  }

  private List<VcsFullCommitDetails> getHeadToBranchCommits(Repository selectedRepo) {
    return myCompareInfo.getHeadToBranchCommits(selectedRepo);
  }

  private CommitCompareInfo.InfoType getInfoType() {
    return myCompareInfo.getInfoType();
  }

  private static void addSelectionListener(@NotNull CommitListPanel sourcePanel,
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

  @NlsContexts.Label
  private String makeDescription(boolean forward) {
    String firstBranch = forward ? myCurrentBranchName : myBranchName;
    String secondBranch = forward ? myBranchName : myCurrentBranchName;
    return new HtmlBuilder().appendRaw(
      DvcsBundle.message("compare.branches.commits.that.exist.in.branch.but.not.in.branch.vcs.command",
                         HtmlChunk.text(secondBranch).bold().code(),
                         HtmlChunk.text(firstBranch).bold().code(),
                         HtmlChunk.text(myHelper.formatLogCommand(firstBranch, secondBranch)).bold().code()))
      .wrapWithHtmlBody().toString();
  }
}
