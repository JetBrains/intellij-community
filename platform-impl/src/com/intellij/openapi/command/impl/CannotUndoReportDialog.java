/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.command.impl;

import com.intellij.CommonBundle;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

/**
 * @author max
 */
public class CannotUndoReportDialog extends DialogWrapper {
  private JList myProblemFilesList;
  private JPanel myPanel;
  private JLabel myProblemMessageLabel;

  public CannotUndoReportDialog(Project project, @Nls String problemText, Collection<DocumentReference> files) {
    super(project, false);

    DefaultListModel model = new DefaultListModel();
    for (DocumentReference file : files) {
      final VirtualFile vFile = file.getFile();
      if (vFile != null) {
        model.add(0, vFile.getPresentableUrl());
      }
      else {
        model.add(0, "<unknown file>");
      }
    }

    myProblemFilesList.setModel(model);
    setTitle(CommonBundle.message("cannot.undo.dialog.title"));

    myProblemMessageLabel.setText(problemText);
    myProblemMessageLabel.setIcon(Messages.getErrorIcon());

    init();
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction()};
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myPanel;
  }
}
