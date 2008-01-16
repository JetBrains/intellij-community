package com.intellij.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.find.FindManager;
import com.intellij.openapi.ui.Messages;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class ReplacePromptDialog extends DialogWrapper {

  private boolean myIsMultiple;

  public ReplacePromptDialog(boolean isMultipleFiles, String title, Project project) {
    super(project, true);
    myIsMultiple = isMultipleFiles;
    setButtonsAlignment(SwingUtilities.CENTER);
    setTitle(title);
    init();
  }

  protected Action[] createActions(){
    DoAction replaceAction = new DoAction(UIBundle.message("replace.prompt.replace.button"), FindManager.PromptResult.OK);
    replaceAction.putValue(DEFAULT_ACTION,Boolean.TRUE);
    if (myIsMultiple){
      return new Action[]{
        replaceAction,
        new DoAction(UIBundle.message("replace.prompt.skip.button"), FindManager.PromptResult.SKIP),
        new DoAction(UIBundle.message("replace.prompt.all.in.this.file.button"), FindManager.PromptResult.ALL_IN_THIS_FILE),
        new DoAction(UIBundle.message("replace.prompt.all.files.action"), FindManager.PromptResult.ALL_FILES),
        getCancelAction()
      };
    }else{
      return new Action[]{
        replaceAction,
        new DoAction(UIBundle.message("replace.prompt.skip.button"), FindManager.PromptResult.SKIP),
        new DoAction(UIBundle.message("replace.prompt.all.button"), FindManager.PromptResult.ALL),
        getCancelAction()
      };
    }
  }

  public JComponent createNorthPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
    Icon icon = Messages.getQuestionIcon();
    if (icon != null){
      JLabel iconLabel = new JLabel(icon);
      panel.add(iconLabel, BorderLayout.WEST);
    }
    JLabel label = new JLabel(UIBundle.message("replace.propmt.replace.occurrence.label"));
    label.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 10));
    label.setForeground(Color.black);
    panel.add(label, BorderLayout.CENTER);
    return panel;
  }

  public JComponent createCenterPanel() {
    return null;
  }

  private class DoAction extends AbstractAction {
    private int myExitCode;

    public DoAction(String name,int exitCode) {
      putValue(Action.NAME, name);
      myExitCode = exitCode;
    }

    public void actionPerformed(ActionEvent e) {
      close(myExitCode);
    }
  }
}

