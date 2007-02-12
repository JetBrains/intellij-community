package com.intellij.localvcs.integration.ui;

import com.intellij.localvcs.Content;
import com.intellij.localvcs.ILocalVcs;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.DiffPanel;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.PopupHandler;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

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
    FileDocumentManager dm = FileDocumentManager.getInstance();
    return new FileHistoryDialogModel(f, vcs, dm);
  }

  @Override
  protected JComponent createDiffPanel() {
    myDiffPanel = DiffManager.getInstance().createDiffPanel(getWindow(), myProject);
    addPopupMenuToDiffPanel();
    updateDiffs();
    return myDiffPanel.getComponent();
  }

  private void addPopupMenuToDiffPanel() {
    myDiffPanel.getComponent().addMouseListener(new PopupHandler() {
      public void invokePopup(Component c, int x, int y) {
        ActionPopupMenu m = createPopupMenu();
        m.getComponent().show(c, x, y);
      }
    });
  }

  private ActionPopupMenu createPopupMenu() {
    ActionGroup g = createActionGroup();
    ActionManager m = ActionManager.getInstance();
    return m.createActionPopupMenu(ActionPlaces.UNKNOWN, g);
  }

  private ActionGroup createActionGroup() {
    // todo make it right
    DefaultActionGroup result = new DefaultActionGroup();
    result.add(new AnAction("revert") {
      public void actionPerformed(AnActionEvent e) {
        try {
          myModel.revert();
        }
        catch (IOException ex) {
          throw new RuntimeException(ex);
        }
      }
    });
    return result;
  }

  @Override
  protected void updateDiffs() {
    Content left = myModel.getLeftContent();
    Content right = myModel.getRightContent();

    myDiffPanel.setDiffRequest(createDiffRequest(left, right));
  }
}
