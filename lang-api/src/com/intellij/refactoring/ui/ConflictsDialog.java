/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

/**
 * created at Sep 12, 2001
 * @author Jeka
 */
package com.intellij.refactoring.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.refactoring.RefactoringBundle;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Collection;

public class ConflictsDialog extends DialogWrapper{
  private String[] myConflictDescriptions;

  public ConflictsDialog(Project project, Collection<String> conflictDescriptions) {
    this(project, conflictDescriptions.toArray(new String[conflictDescriptions.size()]));
  }
  public ConflictsDialog(Project project, String... conflictDescriptions) {
    super(project, true);
    myConflictDescriptions = conflictDescriptions;
    setTitle(RefactoringBundle.message("problems.detected.title"));
    setOKButtonText(RefactoringBundle.message("continue.button"));
    init();
  }

  protected Action[] createActions(){
    return new Action[]{getOKAction(),new CancelAction()};
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    @NonNls final String contentType = "text/html";
    final JEditorPane messagePane = new JEditorPane(contentType, "");
    messagePane.setEditable(false);
    JScrollPane scrollPane = new JScrollPane(messagePane);
    scrollPane.setPreferredSize(new Dimension(500, 400));
    panel.add(new JLabel(RefactoringBundle.message("the.following.problems.were.found")), BorderLayout.NORTH);
    panel.add(scrollPane, BorderLayout.CENTER);

    @NonNls StringBuffer buf = new StringBuffer();
    for (String description : myConflictDescriptions) {
      buf.append(description);
      buf.append("<br><br>");
    }
    messagePane.setText(buf.toString());
    return panel;
  }

  protected JComponent createSouthPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(super.createSouthPanel(), BorderLayout.CENTER);
    panel.add(new JLabel(RefactoringBundle.message("do.you.wish.to.ignore.them.and.continue")), BorderLayout.WEST);
    return panel;
  }

  private class CancelAction extends AbstractAction {
    public CancelAction() {
      super(RefactoringBundle.message("cancel.button"));
      putValue(DialogWrapper.DEFAULT_ACTION,Boolean.TRUE);
    }

    public void actionPerformed(ActionEvent e) {
      doCancelAction();
    }
  }
}
