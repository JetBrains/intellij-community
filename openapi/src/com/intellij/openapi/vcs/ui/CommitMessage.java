/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.ui;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.vcs.VcsBundle;

import javax.swing.*;
import java.awt.*;

public class CommitMessage extends JPanel{

  private final JTextArea myCommentArea = new JTextArea();

  public CommitMessage() {
    super(new BorderLayout());
    final JScrollPane scrollPane = new JScrollPane(myCommentArea);
    scrollPane.setPreferredSize(myCommentArea.getPreferredSize());
    add(scrollPane, BorderLayout.CENTER);
    add(new JLabel(VcsBundle.message("label.commit.comment")), BorderLayout.NORTH);


    final ActionManager actionManager = ActionManager.getInstance();
    final ActionGroup messageActionGroup = (ActionGroup)actionManager.getAction("Vcs.MessageActionGroup");
    if (messageActionGroup != null) {
      ActionToolbar toolbar = actionManager.createButtonToolbar(ActionPlaces.UNKNOWN, messageActionGroup);
      add(toolbar.getComponent(), BorderLayout.SOUTH);
    }
  }

  public JComponent getTextField() {
    return myCommentArea;
  }

  public void setText(final String initialMessage) {
    myCommentArea.setText(initialMessage);
  }

  public String getComment() {
    return myCommentArea.getText().trim();
  }

  public void init() {
    myCommentArea.setRows(3);
    myCommentArea.setWrapStyleWord(true);
    myCommentArea.setLineWrap(true);
    myCommentArea.setSelectionStart(0);
    myCommentArea.setSelectionEnd(myCommentArea.getText().length());

  }

  public void requestFocusInMessage() {
    myCommentArea.requestFocus();
  }
}
