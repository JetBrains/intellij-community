/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.dvcs.ui.DvcsBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * @author Nadya Zabrodina
 */
public abstract class DvcsCommitAdditionalComponent implements RefreshableOnComponent {

  private static final Logger log = Logger.getInstance(DvcsCommitAdditionalComponent.class);

  protected final JPanel myPanel;
  protected final JCheckBox myAmend;
  @Nullable private String myPreviousMessage;
  @Nullable private String myAmendedMessage;
  @NotNull protected final CheckinProjectPanel myCheckinPanel;

  public DvcsCommitAdditionalComponent(@NotNull final Project project, @NotNull CheckinProjectPanel panel) {
    myCheckinPanel = panel;
    myPanel = new JPanel(new GridBagLayout());
    final Insets insets = new Insets(2, 2, 2, 2);
    // add amend checkbox
    GridBagConstraints c = new GridBagConstraints();
    //todo change to MigLayout
    c.gridx = 0;
    c.gridy = 1;
    c.gridwidth = 2;
    c.anchor = GridBagConstraints.CENTER;
    c.insets = insets;
    c.weightx = 1;
    c.fill = GridBagConstraints.HORIZONTAL;

    myAmend = new NonFocusableCheckBox(DvcsBundle.message("commit.amend"));
    myAmend.setMnemonic('m');
    myAmend.setToolTipText(DvcsBundle.message("commit.amend.tooltip"));
    myPreviousMessage = myCheckinPanel.getCommitMessage();

    myAmend.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myAmend.isSelected()) {
          if (myPreviousMessage.equals(myCheckinPanel.getCommitMessage())) { // if user has already typed something, don't revert it
            if (myAmendedMessage == null) {
              loadMessageInModalTask(project);
            }
            else { // checkbox is selected not the first time
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
    myPanel.add(myAmend, c);
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public void refresh() {
    myAmend.setSelected(false);
  }

  private void loadMessageInModalTask(@NotNull Project project) {
    try {
      String messageFromVcs =
        ProgressManager.getInstance().runProcessWithProgressSynchronously(new ThrowableComputable<String, VcsException>() {
          @Override
          public String compute() throws VcsException {
            return getLastCommitMessage();
          }
        }, "Reading commit message...", false, project);
      if (!StringUtil.isEmptyOrSpaces(messageFromVcs)) {
        substituteCommitMessage(messageFromVcs);
        myAmendedMessage = messageFromVcs;
      }
    }
    catch (VcsException e) {
      Messages.showErrorDialog(getComponent(), "Couldn't load commit message of the commit to amend.\n" + e.getMessage(),
                               "Commit Message not Loaded");
      log.info(e);
    }
  }

  private void substituteCommitMessage(@NotNull String newMessage) {
    myPreviousMessage = myCheckinPanel.getCommitMessage();
    if (!myPreviousMessage.trim().equals(newMessage.trim())) {
      myCheckinPanel.setCommitMessage(newMessage);
    }
  }

  @Nullable
  private String getLastCommitMessage() throws VcsException {
    Collection<VirtualFile> roots = getRoots();
    final Ref<VcsException> exception = Ref.create();
    LinkedHashSet<String> messages = ContainerUtil.newLinkedHashSet();
    for (VirtualFile root : roots) {
      String message = getLastCommitMessage(root);
      if (message != null) {
        messages.add(message);
      }
    }
    if (!exception.isNull()) {
      throw exception.get();
    }
    return DvcsUtil.joinMessagesOrNull(messages);
  }

  @NotNull
  protected abstract Collection<VirtualFile> getRoots();

  @Nullable
  protected abstract String getLastCommitMessage(@NotNull VirtualFile repo) throws VcsException;
}
