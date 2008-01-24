/**
 * created at Sep 12, 2001
 * @author Jeka
 */
package com.intellij.refactoring.safeDelete;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.refactoring.RefactoringBundle;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class UnsafeUsagesDialog extends DialogWrapper {
  private JEditorPane myMessagePane;
  private String[] myConflictDescriptions;
  static final int VIEW_USAGES_EXIT_CODE = NEXT_USER_EXIT_CODE;

  public UnsafeUsagesDialog(String[] conflictDescriptions, Project project) {
    super(project, true);
    myConflictDescriptions = conflictDescriptions;
    setTitle(RefactoringBundle.message("usages.detected"));
    setOKButtonText(RefactoringBundle.message("ignore.button"));
    getOKAction().putValue(Action.MNEMONIC_KEY, new Integer('I'));
    init();
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), new ViewUsagesAction(), new CancelAction()};
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    @NonNls final String contentType = "text/html";
    myMessagePane = new JEditorPane(contentType, "");
    myMessagePane.setEditable(false);
    JScrollPane scrollPane = new JScrollPane(myMessagePane);
    scrollPane.setPreferredSize(new Dimension(500, 400));
    panel.add(new JLabel(RefactoringBundle.message("the.following.problems.were.found")), BorderLayout.NORTH);
    panel.add(scrollPane, BorderLayout.CENTER);

    @NonNls StringBuffer buf = new StringBuffer();
    for (String description : myConflictDescriptions) {
      buf.append(description);
      buf.append("<br><br>");
    }
    myMessagePane.setText(buf.toString());
    return panel;
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.refactoring.safeDelete.UnsafeUsagesDialog";
  }

/*
  protected JComponent createSouthPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(super.createSouthPanel(), BorderLayout.CENTER);
//    panel.add(new JLabel("Do you wish to ignore them and continue?"), BorderLayout.WEST);
    return panel;
  }
*/

  private class CancelAction extends AbstractAction {
    public CancelAction() {
      super(RefactoringBundle.message("cancel.button"));
    }

    public void actionPerformed(ActionEvent e) {
      doCancelAction();
    }
  }

  private class ViewUsagesAction extends AbstractAction {
    public ViewUsagesAction() {
      super(RefactoringBundle.message("view.usages"));
      putValue(Action.MNEMONIC_KEY, new Integer('V'));
      putValue(DialogWrapper.DEFAULT_ACTION, Boolean.TRUE);
    }

    public void actionPerformed(ActionEvent e) {
      close(VIEW_USAGES_EXIT_CODE);
    }
  }
}
