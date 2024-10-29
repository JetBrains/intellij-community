// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ui;

import com.intellij.find.FindManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class ReplacePromptDialog extends DialogWrapper {
  private final boolean myIsMultiple;
  private final @Nullable FindManager.MalformedReplacementStringException myException;

  public ReplacePromptDialog(boolean isMultipleFiles, @NlsContexts.DialogTitle String title, Project project) {
    this(isMultipleFiles, title, project, null);
  }

  public ReplacePromptDialog(boolean isMultipleFiles, @NlsContexts.DialogTitle String title, Project project, @Nullable FindManager.MalformedReplacementStringException exception) {
    super(project, true);
    myIsMultiple = isMultipleFiles;
    myException = exception;
    setTitle(title);
    init();
  }

  @Override
  protected Action @NotNull [] createActions(){
    DoAction replaceAction = new DoAction(UIBundle.message("replace.prompt.replace.button"), FindManager.PromptResult.OK);
    replaceAction.putValue(DEFAULT_ACTION,Boolean.TRUE);
    if (myException == null) {
      if (myIsMultiple){
        setCancelButtonText(UIBundle.message("replace.prompt.review.action"));
        return new Action[]{
          replaceAction,
          createSkipAction(),
          new DoAction(UIBundle.message("replace.prompt.all.in.this.file.button"), FindManager.PromptResult.ALL_IN_THIS_FILE),
          new DoAction(UIBundle.message("replace.prompt.skip.all.in.file.button"), FindManager.PromptResult.SKIP_ALL_IN_THIS_FILE),
          new DoAction(UIBundle.message("replace.prompt.all.files.action"), FindManager.PromptResult.ALL_FILES),
          getCancelAction()
        };
      }
      else {
        return new Action[]{
          replaceAction,
          createSkipAction(),
          new DoAction(UIBundle.message("replace.prompt.all.button"), FindManager.PromptResult.ALL),
          getCancelAction()
        };
      }
    } else {
      return new Action[] {
        createSkipAction(),
        getCancelAction()
      };
    }
  }

  private DoAction createSkipAction() {
    return new DoAction(UIBundle.message("replace.prompt.skip.button"), FindManager.PromptResult.SKIP);
  }

  @Override
  public JComponent createNorthPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
    Icon icon = Messages.getQuestionIcon();
    JLabel iconLabel = new JLabel(icon);
    panel.add(iconLabel, BorderLayout.WEST);
    JLabel label = new JLabel(getMessage());
    label.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 10));
    label.setForeground(JBColor.foreground());
    panel.add(label, BorderLayout.CENTER);
    return panel;
  }

  protected @NlsContexts.Label String getMessage() {
    return myException == null ? UIBundle.message("replace.prompt.replace.occurrence.label") : myException.getMessage();
  }

  @Override
  public JComponent createCenterPanel() {
    return null;
  }

  @Override
  protected String getDimensionServiceKey() {
    return "ReplaceDuplicatesPrompt";
  }

  private final class DoAction extends AbstractAction {
    private final int myExitCode;

    DoAction(@NlsActions.ActionText String name, int exitCode) {
      putValue(Action.NAME, name);
      myExitCode = exitCode;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      close(myExitCode);
    }
  }
}

