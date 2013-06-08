package org.hanuna.gitalk.ui.frame;

import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.RefreshAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.ui.components.JBLoadingPanel;
import org.hanuna.gitalk.ui.GitLogIcons;
import org.hanuna.gitalk.ui.VcsLogController;

import javax.swing.*;
import java.awt.*;

/**
 * @author erokhins
 */
public class MainFrame {

  private final VcsLogController myVcsLog_controller;
  private final JPanel mainPanel = new JPanel();
  private final ActiveSurface myActiveSurface;
  private final JBLoadingPanel myLoadingPanel;


  public MainFrame(final VcsLogController vcsLog_controller) {
    this.myVcsLog_controller = vcsLog_controller;
    myActiveSurface = new ActiveSurface(vcsLog_controller);

    mainPanel.setLayout(new BorderLayout());
    mainPanel.add(createToolbar(), BorderLayout.NORTH);
    mainPanel.add(myActiveSurface, BorderLayout.CENTER);

    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), vcsLog_controller.getProject());
    myLoadingPanel.startLoading();
  }

  public VcsLogGraphTable getGraphTable() {
    return myActiveSurface.getGraphTable();
  }

  private JComponent createToolbar() {
    AnAction hideBranchesAction = new DumbAwareAction("Collapse linear branches", "Collapse linear branches", GitLogIcons.SPIDER) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myVcsLog_controller.hideAll();
      }
    };

    AnAction showBranchesAction = new DumbAwareAction("Expand all branches", "Expand all branches", GitLogIcons.WEB) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myVcsLog_controller.showAll();
      }
    };

    RefreshAction refreshAction = new RefreshAction("Refresh", "Refresh", AllIcons.Actions.Refresh) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myVcsLog_controller.refresh(false);
      }

      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(true);
      }
    };

    AnAction showFullPatchAction = new ToggleAction("Show full patch", "Expand all branches even if they occupy a lot of space",
                                                    AllIcons.Actions.Expandall) {
      @Override
      public boolean isSelected(AnActionEvent e) {
        return !myVcsLog_controller.areLongEdgesHidden();
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        myVcsLog_controller.setLongEdgeVisibility(state);
      }
    };

    refreshAction.registerShortcutOn(mainPanel);

    DefaultActionGroup toolbarGroup = new DefaultActionGroup(hideBranchesAction, showBranchesAction, showFullPatchAction, refreshAction);
    return ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarGroup, true).getComponent();
  }

  public JPanel getMainComponent() {
    return myLoadingPanel;
  }

  public void refresh() {
    myActiveSurface.getBranchesPanel().rebuild();
  }

  public void initialLoadingCompleted() {
    myLoadingPanel.add(mainPanel);
    myLoadingPanel.stopLoading();
  }
}
