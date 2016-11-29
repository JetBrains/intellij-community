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
package com.intellij.dvcs;

import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.RepositoryManager;
import com.intellij.dvcs.ui.DvcsBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Provides a checkbox to amend current commit to the previous commit.
 * Selecting a checkbox loads the previous commit message from the provider, and substitutes current message in the editor,
 * unless it was already modified by user.
 */
public abstract class AmendComponent {

  private static final Logger LOG = Logger.getInstance(AmendComponent.class);

  @NotNull private final RepositoryManager<? extends Repository> myRepoManager;
  @NotNull private final CheckinProjectPanel myCheckinPanel;
  @NotNull private final JCheckBox myAmend;
  @NotNull private final String myPreviousMessage;

  @Nullable private Map<VirtualFile, String> myMessagesForRoots;
  @Nullable private String myAmendedMessage;

  public AmendComponent(@NotNull Project project,
                        @NotNull RepositoryManager<? extends Repository> repoManager,
                        @NotNull CheckinProjectPanel panel) {
    this(project, repoManager, panel, DvcsBundle.message("commit.amend"));
  }

  public AmendComponent(@NotNull Project project,
                        @NotNull RepositoryManager<? extends Repository> repoManager,
                        @NotNull CheckinProjectPanel panel,
                        @NotNull String title) {
    myRepoManager = repoManager;
    myCheckinPanel = panel;
    myAmend = new NonFocusableCheckBox(title);
    myAmend.setMnemonic('m');
    myAmend.setToolTipText(DvcsBundle.message("commit.amend.tooltip"));
    myPreviousMessage = myCheckinPanel.getCommitMessage();

    myAmend.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myAmend.isSelected()) {
          if (myPreviousMessage.equals(myCheckinPanel.getCommitMessage())) { // if user has already typed something, don't revert it
            if (myMessagesForRoots == null) {
              loadMessagesInModalTask(project); // load all commit messages for all repositories
            }
            String message = constructAmendedMessage();
            if (!StringUtil.isEmptyOrSpaces(message)) {
              myAmendedMessage = message;
              substituteCommitMessage(myAmendedMessage);
            }
          }
        }
        else {
          // there was the amended message, but user has changed it => not reverting
          if (myCheckinPanel.getCommitMessage().equals(myAmendedMessage)) {
            myCheckinPanel.setCommitMessage(myPreviousMessage);
          }
        }
      }
    });
  }

  @Nullable
  private String constructAmendedMessage() {
    Set<VirtualFile> selectedRoots = getVcsRoots(getSelectedFilePaths()); // get only selected files
    LinkedHashSet<String> messages = ContainerUtil.newLinkedHashSet();
    if (myMessagesForRoots != null) {
      for (VirtualFile root : selectedRoots) {
        String message = myMessagesForRoots.get(root);
        if (message != null) {
          messages.add(message);
        }
      }
    }
    return DvcsUtil.joinMessagesOrNull(messages);
  }

  public void refresh() {
    myAmend.setSelected(false);
  }

  @NotNull
  public Component getComponent() {
    return myAmend;
  }

  @NotNull
  public JCheckBox getCheckBox() {
    return myAmend;
  }

  private void loadMessagesInModalTask(@NotNull Project project) {
    try {
      myMessagesForRoots = ProgressManager.getInstance().runProcessWithProgressSynchronously(this::getLastCommitMessages,
                                                                                             "Reading Commit Message...", true, project);
    }
    catch (VcsException e) {
      Messages.showErrorDialog(project, "Couldn't load commit message of the commit to amend.\n" + e.getMessage(),
                               "Commit Message not Loaded");
      LOG.info(e);
    }
  }

  private void substituteCommitMessage(@NotNull String newMessage) {
    if (!StringUtil.equalsIgnoreWhitespaces(myPreviousMessage, newMessage)) {
      VcsConfiguration.getInstance(myCheckinPanel.getProject()).saveCommitMessage(myPreviousMessage);
      myCheckinPanel.setCommitMessage(newMessage);
    }
  }

  @Nullable
  private Map<VirtualFile, String> getLastCommitMessages() throws VcsException {
    Map<VirtualFile, String> messagesForRoots = new HashMap<>();
    // load all vcs roots visible in the commit dialog (not only selected ones), to avoid another loading task if selection changes
    for (VirtualFile root : getAffectedRoots()) {
      String message = getLastCommitMessage(root);
      messagesForRoots.put(root, message);
    }
    return messagesForRoots;
  }

  @NotNull
  protected Collection<VirtualFile> getAffectedRoots() {
    return myRepoManager.getRepositories().stream().
      filter(repo -> !repo.isFresh()).
      map(Repository::getRoot).
      filter(root -> myCheckinPanel.getRoots().contains(root)).
      collect(Collectors.toList());
  }

  @NotNull
  private List<FilePath> getSelectedFilePaths() {
    return ContainerUtil.map(myCheckinPanel.getFiles(), VcsUtil::getFilePath);
  }

  @NotNull
  protected abstract Set<VirtualFile> getVcsRoots(@NotNull Collection<FilePath> files);

  @Nullable
  protected abstract String getLastCommitMessage(@NotNull VirtualFile repo) throws VcsException;

  public boolean isAmend() {
    return myAmend.isSelected();
  }
}
