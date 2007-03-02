package com.intellij.localvcs.integration.ui.views;

import com.intellij.localvcs.ILocalVcs;
import com.intellij.localvcs.integration.IdeaGateway;
import com.intellij.localvcs.integration.ui.models.FileHistoryDialogModel;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.DiffPanel;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.awt.*;

public class FileHistoryDialog extends HistoryDialog<FileHistoryDialogModel> {
  private DiffPanel myDiffPanel;

  public FileHistoryDialog(VirtualFile f, IdeaGateway gw) {
    super(f, gw);
  }

  @Override
  public void dispose() {
    myDiffPanel.dispose();
    super.dispose();
  }

  @Override
  protected FileHistoryDialogModel createModelFor(VirtualFile f, ILocalVcs vcs) {
    return new FileHistoryDialogModel(f, vcs, myIdeaGateway);
  }

  @Override
  protected JComponent createDiffPanel() {
    myDiffPanel = DiffManager.getInstance().createDiffPanel(getWindow(), myIdeaGateway.getProject());
    updateDiffs();
    return myDiffPanel.getComponent();
  }

  @Override
  protected JComponent createLabelsTable() {
    ActionManager am = ActionManager.getInstance();
    ActionGroup g = createActionGroup();

    JPanel result = new JPanel(new BorderLayout());
    ActionToolbar tb = am.createActionToolbar(ActionPlaces.UNKNOWN, g, true);
    result.add(tb.getComponent(), BorderLayout.NORTH);

    JScrollPane t = (JScrollPane)super.createLabelsTable();
    addPopupMenuToComponent((JComponent)t.getViewport().getView(), g);
    result.add(t, BorderLayout.CENTER);

    return result;
  }

  private ActionGroup createActionGroup() {
    DefaultActionGroup result = new DefaultActionGroup();
    result.add(new RevertAction());
    return result;
  }

  @Override
  protected void updateDiffs() {
    myDiffPanel.setDiffRequest(createDifference(myModel.getDifferenceModel()));
  }

  private class RevertAction extends AnAction {
    public RevertAction() {
      super("Rollback");
    }

    public void actionPerformed(AnActionEvent e) {
      myModel.revert();
      close(0);
    }

    public void update(AnActionEvent e) {
      Presentation p = e.getPresentation();
      p.setIcon(IconLoader.getIcon("/actions/rollback.png"));
    }
  }
}
