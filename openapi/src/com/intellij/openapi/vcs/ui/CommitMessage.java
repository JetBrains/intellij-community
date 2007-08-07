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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.ui.SeparatorFactory;
import com.intellij.ui.TextComponentUndoProvider;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class CommitMessage extends JPanel implements Disposable {
  private final JTextArea myCommentArea = new JTextArea();

  public CommitMessage() {
    super(new BorderLayout());
    final JScrollPane scrollPane = new JScrollPane(myCommentArea);
    scrollPane.setPreferredSize(myCommentArea.getPreferredSize());
    add(scrollPane, BorderLayout.CENTER);
    JComponent separator = SeparatorFactory.createSeparator(VcsBundle.message("label.commit.comment"), myCommentArea);
    add(separator, BorderLayout.NORTH);
    final TextComponentUndoProvider textComponentUndoProvider = new TextComponentUndoProvider(myCommentArea);
    Disposer.register(this, textComponentUndoProvider);
  }

  @Nullable
  public static ActionGroup getToolbarActions() {
    return (ActionGroup)ActionManager.getInstance().getAction("Vcs.MessageActionGroup");
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
    myCommentArea.selectAll();
  }

  public void dispose() {
  }
}
