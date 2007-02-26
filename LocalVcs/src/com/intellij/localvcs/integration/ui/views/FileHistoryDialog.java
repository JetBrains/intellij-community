package com.intellij.localvcs.integration.ui.views;

import com.intellij.localvcs.ILocalVcs;
import com.intellij.localvcs.integration.IdeaGateway;
import com.intellij.localvcs.integration.ui.models.FileHistoryDialogModel;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.DiffPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;

public class FileHistoryDialog extends HistoryDialog<FileHistoryDialogModel> {
  private DiffPanel myDiffPanel;

  public FileHistoryDialog(VirtualFile f, Project p) {
    super(f, p);
  }

  @Override
  protected void dispose() {
    myDiffPanel.dispose();
    super.dispose();
  }

  @Override
  protected FileHistoryDialogModel createModelFor(VirtualFile f, ILocalVcs vcs) {
    return new FileHistoryDialogModel(f, vcs, new IdeaGateway());
  }

  @Override
  protected JComponent createDiffPanel() {
    myDiffPanel = DiffManager.getInstance().createDiffPanel(getWindow(), myProject);
    updateDiffs();
    return myDiffPanel.getComponent();
  }

  @Override
  protected JComponent createLabelsTable() {
    JScrollPane t = (JScrollPane)super.createLabelsTable();
    addPopupMenuToComponent((JComponent)t.getViewport().getView(), createActionGroup());
    return t;
  }

  private ActionGroup createActionGroup() {
    // todo make it right
    DefaultActionGroup result = new DefaultActionGroup();
    result.add(new AnAction("revert") {
      public void actionPerformed(AnActionEvent e) {
        myModel.revert();
      }
    });
    return result;
  }

  @Override
  protected void updateDiffs() {
    myDiffPanel.setDiffRequest(createDifference(myModel.getDifferenceModel()));
  }
}
